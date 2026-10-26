package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An exact percentage in the inclusive range 0..100, in whole percentage units:
 * 12.5 means 12.5%, not a ratio of 12.5. Preserves input scale without rounding;
 * equality, hashing, and ordering use numeric value. Growth rates outside this
 * range require a different consuming-domain type.
 */
public class Percentage private constructor(public val value: Decimal) : Comparable<Percentage> {
    public sealed interface Error : ValidationError {
        public data class OutOfRange(val value: Decimal) : Error
    }

    /** The same value as a ratio, exactly: 12.5% is 0.125. */
    public val ratio: Decimal get() = value.movePointLeft(2)

    override fun compareTo(other: Percentage): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is Percentage && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.numericHashCode()

    override fun toString(): String = "Percentage(value=$value)"

    public companion object : ValueFactory<Decimal, Error, Percentage> {
        private val maximum = Decimal.of(100)

        context(raise: Raise<Error>)
        override fun from(value: Decimal): Percentage {
            raise.ensure(value.sign >= 0 && value <= maximum) { Error.OutOfRange(value) }
            return Percentage(value)
        }
    }
}
