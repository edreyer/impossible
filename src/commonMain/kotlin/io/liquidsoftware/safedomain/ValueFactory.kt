package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import arrow.core.raise.withError

/**
 * Validated construction of [Value] from [Input], with precise failures of type [Error].
 *
 * A companion object can implement this contract to support both direct construction
 * and mapping its failures into a consuming domain's error model.
 */
public interface ValueFactory<in Input, out Error, out Value> {
    /** Constructs a value, raising an [Error] when the input is invalid. */
    context(raise: Raise<Error>)
    public fun from(value: Input): Value

    /**
     * Constructs a value and maps a logical failure with [onError] into the caller's context.
     *
     * Preserves the underlying factory's validation strategy, including accumulation.
     * The mapper runs only on logical failure; unexpected exceptions propagate unchanged.
     */
    context(raise: Raise<MappedError>)
    public fun <MappedError> from(value: Input, onError: (Error) -> MappedError): Value =
        raise.withError(onError) { from(value) }
}

/**
 * Lifts this factory to nullable input: null becomes a null value, and any other input
 * is validated in full (so blank text is still rejected, never treated as absent).
 * This is the explicit way to model a genuinely optional field; the result type is
 * nullable, so callers keep Kotlin's null checks.
 */
public fun <Input : Any, Error, Value : Any> ValueFactory<Input, Error, Value>.optional(): ValueFactory<Input?, Error, Value?> {
    val factory = this
    return object : ValueFactory<Input?, Error, Value?> {
        context(raise: Raise<Error>)
        override fun from(value: Input?): Value? = value?.let { factory.from(it) }
    }
}

/** Why a [required] value could not be constructed. */
public sealed interface RequiredError<out Error> {
    /** The input was null. */
    public data object Missing : RequiredError<Nothing>, ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.missing()
    }

    /** The input was present but invalid. */
    public data class Invalid<out Error>(val cause: Error) : RequiredError<Error>
}

/**
 * Lifts this factory to nullable input that must be present: null raises
 * [RequiredError.Missing], so nullable boundary data (a DTO field, a form value)
 * becomes a non-null domain value without `!!` or a silent default.
 */
public fun <Input : Any, Error, Value> ValueFactory<Input, Error, Value>.required(): ValueFactory<Input?, RequiredError<Error>, Value> {
    val factory = this
    return object : ValueFactory<Input?, RequiredError<Error>, Value> {
        context(raise: Raise<RequiredError<Error>>)
        override fun from(value: Input?): Value {
            val present = raise.ensureNotNull(value) { RequiredError.Missing }
            return factory.from(present) { RequiredError.Invalid(it) }
        }
    }
}

/**
 * Constructs a value from input the caller already knows is valid: a literal, a
 * constant, or data validated earlier. Invalid input is a programmer error and throws
 * [IllegalArgumentException]. Never use this for untrusted input.
 */
public fun <Input, Error, Value> ValueFactory<Input, Error, Value>.fromTrusted(value: Input): Value =
    either { from(value) }.getOrElse { throw IllegalArgumentException("Invalid trusted input: $it") }

/**
 * The value, or the error, as an [Either], for callers outside a [Raise] scope such as
 * tests, scripts, controllers and Java code. The same as `either { from(value) }`.
 */
public fun <Input, Error, Value> ValueFactory<Input, Error, Value>.fromEither(value: Input): Either<Error, Value> =
    either { from(value) }

/** The value, or null when the input is invalid; use it only when the reason does not matter. */
public fun <Input, Error, Value : Any> ValueFactory<Input, Error, Value>.fromOrNull(value: Input): Value? =
    fromEither(value).getOrNull()

/**
 * The value, or [recover] applied to the error, so a caller can substitute a value or
 * fail its own way. [recover] may return from the enclosing function:
 *
 * ```kotlin
 * val email = Email.fromOrElse(raw) { return badRequest(it) }
 * ```
 */
public inline fun <Input, Error, Value> ValueFactory<Input, Error, Value>.fromOrElse(
    value: Input,
    recover: (Error) -> Value,
): Value = fromEither(value).getOrElse(recover)
