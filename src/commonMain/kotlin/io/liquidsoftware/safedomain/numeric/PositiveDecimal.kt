package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An exact decimal strictly greater than zero. Preserves input scale without
 * rounding; equality, hashing, and ordering use numeric value (1.0 equals 1.00).
 */
public class PositiveDecimal private constructor(public val value: Decimal) : Comparable<PositiveDecimal> {
    public sealed interface Error : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)

        public data class NonPositive(val value: Decimal) : Error
    }

    override fun compareTo(other: PositiveDecimal): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is PositiveDecimal && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.numericHashCode()

    override fun toString(): String = "PositiveDecimal(value=$value)"

    /** The sum of two positive decimals is always positive. */
    public operator fun plus(other: PositiveDecimal): PositiveDecimal = PositiveDecimal(value + other.value)

    public operator fun unaryMinus(): NegativeDecimal = NegativeDecimal.fromTrustedDecimal(-value)

    public fun toNonNegativeDecimal(): NonNegativeDecimal = NonNegativeDecimal.fromTrustedDecimal(value)

    public companion object : ValueFactory<Decimal, Error, PositiveDecimal> {
        context(raise: Raise<Error>)
        override fun from(value: Decimal): PositiveDecimal {
            raise.ensure(value.sign > 0) { Error.NonPositive(value) }
            return PositiveDecimal(value)
        }

        internal fun fromTrustedDecimal(value: Decimal): PositiveDecimal = PositiveDecimal(value)
    }
}
