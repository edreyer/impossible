package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/** An integer in the inclusive range [Int.MIN_VALUE]..-1. */
@JvmInline
public value class NegativeInt private constructor(public val value: Int) : Comparable<NegativeInt> {
    public sealed interface Error : ValidationError {
        public data class NonNegative(val value: Int) : Error
    }

    /** The result is below [Int.MIN_VALUE], or is the negation of [Int.MIN_VALUE]. */
    public data object Overflow : ValidationError

    override fun compareTo(other: NegativeInt): Int = value.compareTo(other.value)

    /** The sum of two negative integers is negative unless it overflows. */
    context(raise: Raise<Overflow>)
    public operator fun plus(other: NegativeInt): NegativeInt {
        val sum = value + other.value
        raise.ensure(sum < 0) { Overflow }
        return NegativeInt(sum)
    }

    /** The positive magnitude; only [Int.MIN_VALUE] has none. */
    context(raise: Raise<Overflow>)
    public operator fun unaryMinus(): PositiveInt {
        raise.ensure(value != Int.MIN_VALUE) { Overflow }
        return PositiveInt.fromTrustedInt(-value)
    }

    public fun toNegativeLong(): NegativeLong = NegativeLong.fromTrustedLong(value.toLong())

    public fun toNegativeDecimal(): NegativeDecimal = NegativeDecimal.fromTrustedDecimal(Decimal.of(value.toLong()))

    public companion object : ValueFactory<Int, Error, NegativeInt> {
        public val MINUS_ONE: NegativeInt = NegativeInt(-1)

        /** Returns the wrapped [value], or raises [Error.NonNegative] if it is zero or positive. */
        context(raise: Raise<Error>)
        override fun from(value: Int): NegativeInt {
            raise.ensure(value < 0) { Error.NonNegative(value) }
            return NegativeInt(value)
        }

        internal fun fromTrustedInt(value: Int): NegativeInt = NegativeInt(value)
    }
}
