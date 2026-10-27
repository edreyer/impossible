package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An exact decimal greater than or equal to zero. Preserves input scale without
 * rounding; equality, hashing, and ordering use numeric value (1.0 equals 1.00).
 */
public class NonNegativeDecimal private constructor(public val value: Decimal) : Comparable<NonNegativeDecimal> {
    public sealed interface Error : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)

        public data class Negative(val value: Decimal) : Error
    }

    override fun compareTo(other: NonNegativeDecimal): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is NonNegativeDecimal && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.numericHashCode()

    override fun toString(): String = "NonNegativeDecimal(value=$value)"

    /** The sum of two non-negative decimals is always non-negative. */
    public operator fun plus(other: NonNegativeDecimal): NonNegativeDecimal = NonNegativeDecimal(value + other.value)

    public companion object : ValueFactory<Decimal, Error, NonNegativeDecimal> {
        public val ZERO: NonNegativeDecimal = NonNegativeDecimal(Decimal.ZERO)

        context(raise: Raise<Error>)
        override fun from(value: Decimal): NonNegativeDecimal {
            raise.ensure(value.sign >= 0) { Error.Negative(value) }
            return NonNegativeDecimal(value)
        }

        internal fun fromTrustedDecimal(value: Decimal): NonNegativeDecimal = NonNegativeDecimal(value)
    }
}
