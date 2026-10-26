package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/** An integer in the inclusive range [Long.MIN_VALUE]..-1. */
@JvmInline
public value class NegativeLong private constructor(public val value: Long) : Comparable<NegativeLong> {
    public sealed interface Error : ValidationError {
        public data class NonNegative(val value: Long) : Error
    }

    /** The result is below [Long.MIN_VALUE], or is the negation of [Long.MIN_VALUE]. */
    public data object Overflow : ValidationError

    override fun compareTo(other: NegativeLong): Int = value.compareTo(other.value)

    /** The sum of two negative integers is negative unless it overflows. */
    context(raise: Raise<Overflow>)
    public operator fun plus(other: NegativeLong): NegativeLong {
        val sum = value + other.value
        raise.ensure(sum < 0) { Overflow }
        return NegativeLong(sum)
    }

    /** The positive magnitude; only [Long.MIN_VALUE] has none. */
    context(raise: Raise<Overflow>)
    public operator fun unaryMinus(): PositiveLong {
        raise.ensure(value != Long.MIN_VALUE) { Overflow }
        return PositiveLong.fromTrustedLong(-value)
    }

    public fun toNegativeDecimal(): NegativeDecimal = NegativeDecimal.fromTrustedDecimal(Decimal.of(value))

    public companion object : ValueFactory<Long, Error, NegativeLong> {
        public val MINUS_ONE: NegativeLong = NegativeLong(-1L)

        /** Returns the wrapped [value], or raises [Error.NonNegative] if it is zero or positive. */
        context(raise: Raise<Error>)
        override fun from(value: Long): NegativeLong {
            raise.ensure(value < 0) { Error.NonNegative(value) }
            return NegativeLong(value)
        }

        internal fun fromTrustedLong(value: Long): NegativeLong = NegativeLong(value)
    }
}
