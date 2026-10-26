package io.liquidsoftware.safedomain.money

import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.RoundingMode
import kotlin.jvm.JvmInline

/**
 * [Money] greater than or equal to zero: prices, fees, discounts, and totals, where a
 * free item or an empty cart is valid.
 */
@JvmInline
public value class NonNegativeMoney private constructor(public val money: Money) {
    public sealed interface Error : ValidationError {
        public data class Negative(val money: Money) : Error
    }

    /** The sum of two non-negative amounts is non-negative; fails only on currency mismatch or overflow. */
    context(raise: Raise<Money.ArithmeticError>)
    public fun add(other: NonNegativeMoney): NonNegativeMoney = NonNegativeMoney(money.add(other.money))

    context(raise: Raise<Money.ArithmeticError>)
    public fun multiply(quantity: PositiveInt): NonNegativeMoney = NonNegativeMoney(money.multiply(quantity))

    /** A share of this amount, such as a discount or tax; rounding never makes it negative. */
    context(raise: Raise<Money.ArithmeticError>)
    public fun multiply(percentage: Percentage, roundingMode: RoundingMode): NonNegativeMoney =
        NonNegativeMoney(money.multiply(percentage, roundingMode))

    /** [Money.allocate]; every part of a non-negative amount is non-negative. */
    public fun allocate(ratios: NonEmptyList<PositiveInt>): NonEmptyList<NonNegativeMoney> =
        money.allocate(ratios).map(::NonNegativeMoney)

    /** [Money.split]; every part of a non-negative amount is non-negative. */
    public fun split(parts: PositiveInt): NonEmptyList<NonNegativeMoney> = money.split(parts).map(::NonNegativeMoney)

    /** Ordering within one currency, as [Money.compare]. */
    context(raise: Raise<Money.ArithmeticError>)
    public fun compare(other: NonNegativeMoney): Int = money.compare(other.money)

    public companion object : ValueFactory<Money, Error, NonNegativeMoney> {
        context(raise: Raise<Error>)
        override fun from(value: Money): NonNegativeMoney {
            raise.ensure(value.sign >= 0) { Error.Negative(value) }
            return NonNegativeMoney(value)
        }

        /** Zero in [currency], such as an empty cart's total. */
        context(raise: Raise<Money.Error>)
        public fun zero(currency: Currency): NonNegativeMoney = NonNegativeMoney(Money.zero(currency))

        internal fun fromTrustedMoney(value: Money): NonNegativeMoney = NonNegativeMoney(value)
    }
}
