package io.liquidsoftware.safedomain

import arrow.core.raise.Raise
import arrow.core.raise.withError

/**
 * Validated construction of [Value] from [Input], with precise failures of type [Error].
 *
 * A companion object can implement this contract to support both direct construction
 * and mapping its failures into a consuming domain's error model.
 */
interface ValueFactory<in Input, out Error, out Value> {
    /** Constructs a value, raising an [Error] when the input is invalid. */
    context(raise: Raise<Error>)
    fun from(value: Input): Value

    /**
     * Constructs a value and maps a logical failure with [onError] into the caller's context.
     *
     * Preserves the underlying factory's validation strategy, including accumulation.
     * The mapper runs only on logical failure; unexpected exceptions propagate unchanged.
     */
    context(raise: Raise<MappedError>)
    fun <MappedError> from(value: Input, onError: (Error) -> MappedError): Value =
        raise.withError(onError) { from(value) }
}
