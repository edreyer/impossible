package io.liquidsoftware.safedomain

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import java.math.BigDecimal

/**
 * An exact percentage in the inclusive range 0..100, in whole percentage units:
 * 12.5 means 12.5%, not a ratio of 12.5. Preserves input scale without rounding;
 * equality, hashing, and ordering use numeric value. Growth rates outside this
 * range require a different consuming-domain type.
 */
class Percentage private constructor(val value: BigDecimal) : Comparable<Percentage> {
    sealed interface Error {
        data class OutOfRange(val value: BigDecimal) : Error
    }

    override fun compareTo(other: Percentage): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is Percentage && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.numericHashCode()

    override fun toString(): String = "Percentage(value=$value)"

    companion object : ValueFactory<BigDecimal, Error, Percentage> {
        private val maximum = BigDecimal("100")

        context(raise: Raise<Error>)
        override fun from(value: BigDecimal): Percentage {
            raise.ensure(value.signum() >= 0 && value <= maximum) { Error.OutOfRange(value) }
            return Percentage(value)
        }
    }
}
