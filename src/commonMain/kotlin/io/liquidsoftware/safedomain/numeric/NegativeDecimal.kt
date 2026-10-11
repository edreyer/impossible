package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An exact decimal strictly less than zero. Preserves input scale without
 * rounding; equality, hashing, and ordering use numeric value (-1.0 equals -1.00).
 */
public class NegativeDecimal private constructor(public val value: Decimal) : Comparable<NegativeDecimal> {
    public sealed interface Error {
        public data class NonNegative(val value: Decimal) : Error
    }

    override fun compareTo(other: NegativeDecimal): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is NegativeDecimal && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.numericHashCode()

    override fun toString(): String = "NegativeDecimal(value=$value)"

    /** The sum of two negative decimals is always negative. */
    public operator fun plus(other: NegativeDecimal): NegativeDecimal = NegativeDecimal(value + other.value)

    /** The positive magnitude. */
    public operator fun unaryMinus(): PositiveDecimal = PositiveDecimal.fromTrustedDecimal(-value)

    public companion object : ValueFactory<Decimal, Error, NegativeDecimal> {
        context(raise: Raise<Error>)
        override fun from(value: Decimal): NegativeDecimal {
            raise.ensure(value.sign < 0) { Error.NonNegative(value) }
            return NegativeDecimal(value)
        }

        internal fun fromTrustedDecimal(value: Decimal): NegativeDecimal = NegativeDecimal(value)
    }
}
