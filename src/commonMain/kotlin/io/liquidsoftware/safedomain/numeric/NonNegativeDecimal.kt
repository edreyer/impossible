package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An exact decimal greater than or equal to zero. Preserves input scale without
 * rounding; equality, hashing, and ordering use numeric value (1.0 equals 1.00).
 */
class NonNegativeDecimal private constructor(val value: Decimal) : Comparable<NonNegativeDecimal> {
    sealed interface Error {
        data class Negative(val value: Decimal) : Error
    }

    override fun compareTo(other: NonNegativeDecimal): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is NonNegativeDecimal && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.numericHashCode()

    override fun toString(): String = "NonNegativeDecimal(value=$value)"

    companion object : ValueFactory<Decimal, Error, NonNegativeDecimal> {
        context(raise: Raise<Error>)
        override fun from(value: Decimal): NonNegativeDecimal {
            raise.ensure(value.sign >= 0) { Error.Negative(value) }
            return NonNegativeDecimal(value)
        }
    }
}
