package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An exact decimal strictly greater than zero. Preserves input scale without
 * rounding; equality, hashing, and ordering use numeric value (1.0 equals 1.00).
 */
class PositiveDecimal private constructor(val value: Decimal) : Comparable<PositiveDecimal> {
    sealed interface Error {
        data class NonPositive(val value: Decimal) : Error
    }

    override fun compareTo(other: PositiveDecimal): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is PositiveDecimal && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.numericHashCode()

    override fun toString(): String = "PositiveDecimal(value=$value)"

    companion object : ValueFactory<Decimal, Error, PositiveDecimal> {
        context(raise: Raise<Error>)
        override fun from(value: Decimal): PositiveDecimal {
            raise.ensure(value.sign > 0) { Error.NonPositive(value) }
            return PositiveDecimal(value)
        }
    }
}
