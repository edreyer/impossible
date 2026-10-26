package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An integer within the inclusive [bounds] of the factory that created it, such as a
 * 1..5 rating or a 1..100 page size. Equality and hashing include the bounds.
 * Wrap it in a consuming-domain type when the bounds carry a name (`Rating`).
 */
public class BoundedInt private constructor(public val value: Int, public val bounds: IntRange) : Comparable<BoundedInt> {
    public sealed interface Error : ValidationError {
        public data class OutOfRange(val value: Int, val bounds: IntRange) : Error
    }

    /** Numeric ordering of [value]; bounds are not compared. */
    override fun compareTo(other: BoundedInt): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is BoundedInt && value == other.value && bounds == other.bounds

    override fun hashCode(): Int = 31 * value + bounds.hashCode()

    override fun toString(): String = "BoundedInt(value=$value, bounds=$bounds)"

    public companion object {
        /**
         * Returns a factory accepting [bounds]. Bounds are programmer configuration, so an
         * empty range throws [IllegalArgumentException].
         */
        public fun factory(bounds: IntRange): ValueFactory<Int, Error, BoundedInt> {
            require(!bounds.isEmpty()) { "Bounds must not be empty: $bounds" }
            return object : ValueFactory<Int, Error, BoundedInt> {
                context(raise: Raise<Error>)
                override fun from(value: Int): BoundedInt {
                    raise.ensure(value in bounds) { Error.OutOfRange(value, bounds) }
                    return BoundedInt(value, bounds)
                }
            }
        }
    }
}
