package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/** An integer in the inclusive range 1..[Int.MAX_VALUE]. */
@JvmInline
public value class PositiveInt private constructor(public val value: Int) : Comparable<PositiveInt> {
    public sealed interface Error {
        public data class NonPositive(val value: Int) : Error
    }

    /** The sum exceeded [Int.MAX_VALUE]. */
    public data object Overflow

    override fun compareTo(other: PositiveInt): Int = value.compareTo(other.value)

    /** The sum of two positive integers is positive unless it overflows. */
    context(raise: Raise<Overflow>)
    public operator fun plus(other: PositiveInt): PositiveInt {
        val sum = value + other.value
        raise.ensure(sum > 0) { Overflow }
        return PositiveInt(sum)
    }

    /** The negation of a positive integer always fits. */
    public operator fun unaryMinus(): NegativeInt = NegativeInt.fromTrustedInt(-value)

    public fun toPositiveLong(): PositiveLong = PositiveLong.fromTrustedLong(value.toLong())

    public fun toPositiveDecimal(): PositiveDecimal = PositiveDecimal.fromTrustedDecimal(Decimal.of(value.toLong()))

    public companion object : ValueFactory<Int, Error, PositiveInt> {
        public val ONE: PositiveInt = PositiveInt(1)

        /** Returns the wrapped [value], or raises [Error.NonPositive] if it is zero or negative. */
        context(raise: Raise<Error>)
        override fun from(value: Int): PositiveInt {
            raise.ensure(value > 0) { Error.NonPositive(value) }
            return PositiveInt(value)
        }

        internal fun fromTrustedInt(value: Int): PositiveInt = PositiveInt(value)
    }
}
