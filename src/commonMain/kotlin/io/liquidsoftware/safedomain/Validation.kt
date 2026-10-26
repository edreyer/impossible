package io.liquidsoftware.safedomain

import arrow.core.NonEmptyList
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.Raise
import arrow.core.raise.RaiseAccumulate
import arrow.core.raise.accumulate
import arrow.core.raise.withError
import kotlin.reflect.KProperty

/**
 * The scope of a [validate] block. It is a [Raise] of the composite's error, so `ensure`
 * and `raise` work inside it; declare fields with [field] and its siblings.
 *
 * The DSL is built on Arrow's accumulation API, which is experimental; this class keeps
 * that API out of the library's public signatures.
 */
@OptIn(ExperimentalRaiseAccumulateApi::class)
public class ValidationScope<Error> internal constructor(
    private val accumulate: RaiseAccumulate<Error>,
) : Raise<Error> {
    override fun raise(r: Error): Nothing = accumulate.raise(r)

    internal inline fun <A> accumulating(crossinline block: RaiseAccumulate<Error>.() -> A): Validated<A> =
        Validated(accumulate.accumulating { block() })
}

/**
 * A field declared inside [validate]. Read it with `by`; reading a failed field
 * raises every error collected so far, so read fields only once all are declared,
 * or inside a dependent [field] block.
 */
@OptIn(ExperimentalRaiseAccumulateApi::class)
public class Validated<out A> internal constructor(private val delegate: RaiseAccumulate.Value<A>) {
    /** The field's value; reading it when the field failed raises every error collected so far. */
    public val value: A get() = delegate.value

    public operator fun getValue(thisRef: Any?, property: KProperty<*>): A = value
}

/**
 * Accumulating construction for composite types: every field declared in [block]
 * runs, and all failures are raised together in declaration order.
 */
@OptIn(ExperimentalRaiseAccumulateApi::class)
context(raise: Raise<NonEmptyList<Error>>)
public fun <Error, A> validate(block: ValidationScope<Error>.() -> A): A =
    raise.accumulate { ValidationScope(this).block() }

/** Validates one field, mapping its failure (including a whole error batch) with [onError]. */
context(scope: ValidationScope<Mapped>)
public fun <Input, Error, Value, Mapped> ValueFactory<Input, Error, Value>.field(
    value: Input,
    onError: (Error) -> Mapped,
): Validated<Value> = scope.accumulating { from(value, onError) }

/**
 * Validates one [required] field, mapping absence with [onMissing] and an invalid
 * value with [onError].
 */
context(scope: ValidationScope<Mapped>)
public fun <Input, Error, Value, Mapped> ValueFactory<Input, RequiredError<Error>, Value>.field(
    value: Input,
    onMissing: () -> Mapped,
    onError: (Error) -> Mapped,
): Validated<Value> = field(value) { failure ->
    when (failure) {
        RequiredError.Missing -> onMissing()
        is RequiredError.Invalid -> onError(failure.cause)
    }
}

/** Validates one field from an accumulating factory, mapping each nested error into the parent list. */
context(scope: ValidationScope<Mapped>)
public fun <Input, Error, Value, Mapped> ValueFactory<Input, NonEmptyList<Error>, Value>.flatField(
    value: Input,
    onError: (Error) -> Mapped,
): Validated<Value> = scope.accumulating {
    withNel { withError({ errors: NonEmptyList<Error> -> errors.map(onError) }) { from(value) } }
}

/** Validates every element, reporting each failure with its zero-based index. */
context(scope: ValidationScope<Mapped>)
public fun <Input, Error, Value, Mapped> ValueFactory<Input, Error, Value>.eachField(
    values: Iterable<Input>,
    onError: (index: Int, Error) -> Mapped,
): Validated<List<Value>> = scope.accumulating {
    withNel {
        mapOrAccumulate(values.withIndex()) { (index, raw) -> from(raw) { onError(index, it) } }
    }
}

/**
 * A field computed by [block]. A failure stops only this block; other fields still run.
 * Use it for steps that depend on earlier fields or for checks across fields.
 */
context(scope: ValidationScope<Error>)
public fun <Error, A> field(block: ValidationScope<Error>.() -> A): Validated<A> =
    scope.accumulating { ValidationScope(this).block() }

/**
 * A field declared with one of the path-aware helpers ([fieldAt], [eachFieldAt],
 * [flatFieldAt], [nestedFieldAt]). Declare it with `by`: the property's name becomes the
 * field's path unless the helper was given an explicit `name`, such as a JSON key.
 */
public class PathField<out A> internal constructor(
    private val name: String?,
    private val validate: (FieldPath) -> Validated<A>,
) {
    public operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Validated<A> =
        validate(FieldPath.of(name ?: property.name))
}

/**
 * Validates one field and records its failure at the field's path. The path is the name
 * of the property it is assigned to, or [name] when given:
 *
 * ```kotlin
 * val email by Email.fieldAt(input.email) { OrderError.Email(it) }   // path "email"
 * ```
 */
context(scope: ValidationScope<FieldError<Mapped>>)
public fun <Input, Error, Value, Mapped> ValueFactory<Input, Error, Value>.fieldAt(
    value: Input,
    name: String? = null,
    onError: (Error) -> Mapped,
): PathField<Value> = PathField(name) { path ->
    scope.accumulating { from(value) { FieldError(path, onError(it)) } }
}

/** Validates one [required] field at its path, mapping absence with [onMissing] and an invalid value with [onError]. */
context(scope: ValidationScope<FieldError<Mapped>>)
public fun <Input, Error, Value, Mapped> ValueFactory<Input, RequiredError<Error>, Value>.fieldAt(
    value: Input,
    name: String? = null,
    onMissing: () -> Mapped,
    onError: (Error) -> Mapped,
): PathField<Value> = fieldAt(value, name) { failure ->
    when (failure) {
        RequiredError.Missing -> onMissing()
        is RequiredError.Invalid -> onError(failure.cause)
    }
}

/** Validates every element, recording each failure at `field[index]`. */
context(scope: ValidationScope<FieldError<Mapped>>)
public fun <Input, Error, Value, Mapped> ValueFactory<Input, Error, Value>.eachFieldAt(
    values: Iterable<Input>,
    name: String? = null,
    onError: (Error) -> Mapped,
): PathField<List<Value>> = PathField(name) { path ->
    scope.accumulating {
        withNel {
            mapOrAccumulate(values.withIndex()) { (index, raw) ->
                from(raw) { FieldError(path.index(index), onError(it)) }
            }
        }
    }
}

/**
 * Validates one field from an accumulating factory without paths of its own, such as
 * `GeoPoint` or `Money.parser`, recording each of its errors at the field's path.
 */
context(scope: ValidationScope<FieldError<Mapped>>)
public fun <Input, Error, Value, Mapped> ValueFactory<Input, NonEmptyList<Error>, Value>.flatFieldAt(
    value: Input,
    name: String? = null,
    onError: (Error) -> Mapped,
): PathField<Value> = PathField(name) { path ->
    scope.accumulating {
        withNel {
            withError({ errors: NonEmptyList<Error> -> errors.map { FieldError(path, onError(it)) } }) { from(value) }
        }
    }
}

/**
 * Validates a nested composite whose factory reports [FieldError]s, prefixing each inner
 * path with this field's path: a `price` failure inside the `shipping` field is
 * reported at `shipping.price`.
 */
context(scope: ValidationScope<FieldError<Mapped>>)
public fun <Input, Error, Value, Mapped> ValueFactory<Input, NonEmptyList<FieldError<Error>>, Value>.nestedFieldAt(
    value: Input,
    name: String? = null,
    onError: (Error) -> Mapped,
): PathField<Value> = PathField(name) { path ->
    scope.accumulating {
        withNel {
            withError({ errors: NonEmptyList<FieldError<Error>> ->
                errors.map { it.under(path).map(onError) }
            }) { from(value) }
        }
    }
}

/**
 * A field computed by [block], with its failure recorded at the field's path. Use it for
 * steps that depend on earlier fields or check several at once; report a failure that
 * belongs to no single field with `raise(FieldError(FieldPath.ROOT, error))` in [field].
 */
context(scope: ValidationScope<FieldError<Mapped>>)
public fun <Mapped, A> fieldAt(name: String? = null, block: Raise<Mapped>.() -> A): PathField<A> =
    PathField(name) { path ->
        scope.accumulating { withError({ error: Mapped -> FieldError(path, error) }) { block() } }
    }
