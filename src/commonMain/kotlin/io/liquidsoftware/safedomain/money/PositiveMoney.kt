package io.liquidsoftware.safedomain.money

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.numeric.PositiveInt
import kotlin.jvm.JvmInline

/**
 * [Money] strictly greater than zero, for amounts that must actually be charged or
 * paid. Use [NonNegativeMoney] where zero is valid, such as prices and totals.
 */
@JvmInline
public value class PositiveMoney private constructor(public val money: Money) {
    public sealed interface Error : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)

        public data class NonPositive(val money: Money) : Error
    }

    /** The sum of two positive amounts is positive; fails only on currency mismatch or overflow. */
    context(raise: Raise<Money.ArithmeticError>)
    public fun add(other: PositiveMoney): PositiveMoney = PositiveMoney(money.add(other.money))

    context(raise: Raise<Money.ArithmeticError>)
    public fun multiply(quantity: PositiveInt): PositiveMoney = PositiveMoney(money.multiply(quantity))

    /** The negation of a positive amount always fits. */
    public fun negate(): NegativeMoney = NegativeMoney.fromTrustedMoney(money.negateUnchecked())

    public fun toNonNegativeMoney(): NonNegativeMoney = NonNegativeMoney.fromTrustedMoney(money)

    /** Ordering within one currency, as [Money.compare]. */
    context(raise: Raise<Money.ArithmeticError>)
    public fun compare(other: PositiveMoney): Int = money.compare(other.money)

    public companion object : ValueFactory<Money, Error, PositiveMoney> {
        context(raise: Raise<Error>)
        override fun from(value: Money): PositiveMoney {
            raise.ensure(value.sign > 0) { Error.NonPositive(value) }
            return PositiveMoney(value)
        }

        internal fun fromTrustedMoney(value: Money): PositiveMoney = PositiveMoney(value)
    }
}
