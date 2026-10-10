package io.liquidsoftware.safedomain

import arrow.core.NonEmptyList
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.Raise
import arrow.core.raise.RaiseAccumulate
import arrow.core.raise.accumulate
import arrow.core.raise.withError

/**
 * A field declared inside [validate]. Read it with `by`; reading a failed field
 * raises every error collected so far, so read fields only once all are declared,
 * or inside a dependent [field] block.
 */
typealias Validated<A> = RaiseAccumulate.Value<A>

/**
 * Accumulating construction for composite types: every field declared in [block]
 * runs, and all failures are raised together in declaration order.
 *
 * This is the only entry point to Arrow's experimental accumulation API; callers
 * need no opt-in.
 */
@OptIn(ExperimentalRaiseAccumulateApi::class)
context(raise: Raise<NonEmptyList<Error>>)
inline fun <Error, A> validate(block: RaiseAccumulate<Error>.() -> A): A = raise.accumulate(block)

/** Validates one field, mapping its failure (including a whole error batch) with [onError]. */
@OptIn(ExperimentalRaiseAccumulateApi::class)
context(scope: RaiseAccumulate<Mapped>)
fun <Input, Error, Value, Mapped> ValueFactory<Input, Error, Value>.field(
    value: Input,
    onError: (Error) -> Mapped,
): Validated<Value> = scope.accumulating { from(value, onError) }

/**
 * Validates one [required] field, mapping absence with [onMissing] and an invalid
 * value with [onError].
 */
@OptIn(ExperimentalRaiseAccumulateApi::class)
context(scope: RaiseAccumulate<Mapped>)
fun <Input, Error, Value, Mapped> ValueFactory<Input, RequiredError<Error>, Value>.field(
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
@OptIn(ExperimentalRaiseAccumulateApi::class)
context(scope: RaiseAccumulate<Mapped>)
fun <Input, Error, Value, Mapped> ValueFactory<Input, NonEmptyList<Error>, Value>.flatField(
    value: Input,
    onError: (Error) -> Mapped,
): Validated<Value> = scope.accumulating {
    withNel { withError({ errors: NonEmptyList<Error> -> errors.map(onError) }) { from(value) } }
}

/** Validates every element, reporting each failure with its zero-based index. */
@OptIn(ExperimentalRaiseAccumulateApi::class)
context(scope: RaiseAccumulate<Mapped>)
fun <Input, Error, Value, Mapped> ValueFactory<Input, Error, Value>.eachField(
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
@OptIn(ExperimentalRaiseAccumulateApi::class)
context(scope: RaiseAccumulate<Error>)
fun <Error, A> field(block: RaiseAccumulate<Error>.() -> A): Validated<A> = scope.accumulating(block)
