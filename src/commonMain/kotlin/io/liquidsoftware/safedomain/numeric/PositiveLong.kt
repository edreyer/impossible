package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/** An integer in the inclusive range 1..[Long.MAX_VALUE]. */
@JvmInline
public value class PositiveLong private constructor(public val value: Long) : Comparable<PositiveLong> {
    public sealed interface Error : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)

        public data class NonPositive(val value: Long) : Error
    }

    /** The sum exceeded [Long.MAX_VALUE]. */
    public data object Overflow : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)
    }

    override fun compareTo(other: PositiveLong): Int = value.compareTo(other.value)

    /** The sum of two positive integers is positive unless it overflows. */
    context(raise: Raise<Overflow>)
    public operator fun plus(other: PositiveLong): PositiveLong {
        val sum = value + other.value
        raise.ensure(sum > 0) { Overflow }
        return PositiveLong(sum)
    }

    /** The negation of a positive integer always fits. */
    public operator fun unaryMinus(): NegativeLong = NegativeLong.fromTrustedLong(-value)

    public fun toPositiveDecimal(): PositiveDecimal = PositiveDecimal.fromTrustedDecimal(Decimal.of(value))

    public companion object : ValueFactory<Long, Error, PositiveLong> {
        public val ONE: PositiveLong = PositiveLong(1)

        /** Returns the wrapped [value], or raises [Error.NonPositive] if it is zero or negative. */
        context(raise: Raise<Error>)
        override fun from(value: Long): PositiveLong {
            raise.ensure(value > 0) { Error.NonPositive(value) }
            return PositiveLong(value)
        }

        internal fun fromTrustedLong(value: Long): PositiveLong = PositiveLong(value)
    }
}
