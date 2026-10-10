package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/** An integer in the inclusive range 1..[Long.MAX_VALUE]. */
@JvmInline
value class PositiveLong private constructor(val value: Long) : Comparable<PositiveLong> {
    sealed interface Error {
        data class NonPositive(val value: Long) : Error
    }

    /** The sum exceeded [Long.MAX_VALUE]. */
    data object Overflow

    override fun compareTo(other: PositiveLong): Int = value.compareTo(other.value)

    /** The sum of two positive integers is positive unless it overflows. */
    context(raise: Raise<Overflow>)
    operator fun plus(other: PositiveLong): PositiveLong {
        val sum = value + other.value
        raise.ensure(sum > 0) { Overflow }
        return PositiveLong(sum)
    }

    fun toPositiveDecimal(): PositiveDecimal = PositiveDecimal.fromTrustedDecimal(Decimal.of(value))

    companion object : ValueFactory<Long, Error, PositiveLong> {
        val ONE: PositiveLong = PositiveLong(1)

        /** Returns the wrapped [value], or raises [Error.NonPositive] if it is zero or negative. */
        context(raise: Raise<Error>)
        override fun from(value: Long): PositiveLong {
            raise.ensure(value > 0) { Error.NonPositive(value) }
            return PositiveLong(value)
        }

        internal fun fromTrustedLong(value: Long): PositiveLong = PositiveLong(value)
    }
}
