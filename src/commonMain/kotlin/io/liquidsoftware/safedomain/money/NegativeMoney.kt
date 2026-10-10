package io.liquidsoftware.safedomain.money

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.numeric.PositiveInt
import kotlin.jvm.JvmInline

/** [Money] strictly less than zero, such as a refund, credit, or adjustment. */
@JvmInline
value class NegativeMoney private constructor(val money: Money) {
    sealed interface Error {
        data class NonNegative(val money: Money) : Error
    }

    /** The sum of two negative amounts is negative; fails only on currency mismatch or overflow. */
    context(raise: Raise<Money.ArithmeticError>)
    fun add(other: NegativeMoney): NegativeMoney = NegativeMoney(money.add(other.money))

    context(raise: Raise<Money.ArithmeticError>)
    fun multiply(quantity: PositiveInt): NegativeMoney = NegativeMoney(money.multiply(quantity))

    /** The positive magnitude; fails with [Money.ArithmeticError.Overflow] only for [Long.MIN_VALUE] minor units. */
    context(raise: Raise<Money.ArithmeticError>)
    fun negate(): PositiveMoney = PositiveMoney.fromTrustedMoney(money.negate())

    /** Ordering within one currency, as [Money.compare]. */
    context(raise: Raise<Money.ArithmeticError>)
    fun compare(other: NegativeMoney): Int = money.compare(other.money)

    companion object : ValueFactory<Money, Error, NegativeMoney> {
        context(raise: Raise<Error>)
        override fun from(value: Money): NegativeMoney {
            raise.ensure(value.sign < 0) { Error.NonNegative(value) }
            return NegativeMoney(value)
        }

        internal fun fromTrustedMoney(value: Money): NegativeMoney = NegativeMoney(value)
    }
}
