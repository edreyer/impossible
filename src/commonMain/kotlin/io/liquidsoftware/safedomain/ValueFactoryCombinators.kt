package io.liquidsoftware.safedomain

import arrow.core.raise.Raise
import arrow.core.raise.ensure

/*
 * Combinators that build a new factory from an existing one, so a consuming domain can
 * define a refined type without hand-writing a companion. Each returned factory runs the
 * original validation first and stops at its first failure; a later step never sees
 * input the earlier step rejected. Failures stay precise: the combinators never widen an
 * error to a shared supertype on their own, and [mapError] or the `onError` form of
 * [andThen] is how two error models meet.
 */

/**
 * Transforms each constructed value with [transform], typically a private constructor
 * that wraps the validated value: `NonBlankString.map { Name(it) }`. [transform] runs
 * only after validation succeeds and must not fail; use [andThen] for a step that can.
 */
fun <Input, Error, Value, Mapped> ValueFactory<Input, Error, Value>.map(
    transform: (Value) -> Mapped,
): ValueFactory<Input, Error, Mapped> {
    val factory = this
    return object : ValueFactory<Input, Error, Mapped> {
        context(raise: Raise<Error>)
        override fun from(value: Input): Mapped = transform(factory.from(value))
    }
}

/**
 * Maps every failure with [transform] into another error model, usually the consuming
 * type's own sealed error. [transform] runs only on failure, once per raised error, so
 * an accumulating factory's whole batch arrives as one value.
 */
fun <Input, Error, Value, MappedError> ValueFactory<Input, Error, Value>.mapError(
    transform: (Error) -> MappedError,
): ValueFactory<Input, MappedError, Value> {
    val factory = this
    return object : ValueFactory<Input, MappedError, Value> {
        context(raise: Raise<MappedError>)
        override fun from(value: Input): Value = factory.from(value, onError = transform)
    }
}

/**
 * Refines each constructed value further through [next], which shares this factory's
 * error model. [next] runs only on a value this factory accepted.
 */
fun <Input, Error, Value, Refined> ValueFactory<Input, Error, Value>.andThen(
    next: ValueFactory<Value, Error, Refined>,
): ValueFactory<Input, Error, Refined> {
    val factory = this
    return object : ValueFactory<Input, Error, Refined> {
        context(raise: Raise<Error>)
        override fun from(value: Input): Refined = next.from(factory.from(value))
    }
}

/**
 * Refines each constructed value further through [next], mapping its failures into this
 * factory's error model with [onError]. [next] runs only on a value this factory accepted.
 */
fun <Input, Error, Value, NextError, Refined> ValueFactory<Input, Error, Value>.andThen(
    next: ValueFactory<Value, NextError, Refined>,
    onError: (NextError) -> Error,
): ValueFactory<Input, Error, Refined> {
    val factory = this
    return object : ValueFactory<Input, Error, Refined> {
        context(raise: Raise<Error>)
        override fun from(value: Input): Refined = next.from(factory.from(value), onError)
    }
}

/**
 * Adds a check on each constructed value: a value failing [predicate] raises the error
 * that [error] builds from it. Neither function runs when this factory already failed.
 * Keep personal data out of the error, as the library's own errors do.
 */
fun <Input, Error, Value> ValueFactory<Input, Error, Value>.ensure(
    predicate: (Value) -> Boolean,
    error: (Value) -> Error,
): ValueFactory<Input, Error, Value> {
    val factory = this
    return object : ValueFactory<Input, Error, Value> {
        context(raise: Raise<Error>)
        override fun from(value: Input): Value {
            val constructed = factory.from(value)
            raise.ensure(predicate(constructed)) { error(constructed) }
            return constructed
        }
    }
}
