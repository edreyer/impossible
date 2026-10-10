package io.liquidsoftware.safedomain.money

import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.NonNegativeInt
import io.liquidsoftware.safedomain.numeric.RoundingMode
import io.liquidsoftware.safedomain.validate

/**
 * A signed exact decimal [amount] in a validated [currency]. Sub-minor-unit amounts
 * are allowed for intermediate calculations. Construction preserves decimal scale;
 * equality and hashing use numeric amount and currency, so USD 1.0 equals USD 1.00.
 * No floating-point conversion, implicit rounding, or currency conversion is performed.
 * Arithmetic returns new values. Division and rounding require an explicit policy.
 */
class Money private constructor(val amount: Decimal, val currency: Currency) {
    data class Input(val amount: String, val currency: String)

    sealed interface Error {
        data class InvalidAmount(val value: String) : Error
        data class InvalidCurrency(val cause: Currency.Error) : Error
    }

    sealed interface ArithmeticError {
        data class CurrencyMismatch(val expected: Currency, val actual: Currency) : ArithmeticError
        data object DivisionByZero : ArithmeticError

        /**
         * Decimal scale overflow, a result requiring rounding when UNNECESSARY was requested,
         * or a result beyond [Decimal.MAX_EXPANSION_DIGITS].
         */
        data object UnrepresentableResult : ArithmeticError
    }

    context(raise: Raise<ArithmeticError>)
    fun add(other: Money): Money {
        requireSameCurrency(other)
        return calculate { amount + other.amount }
    }

    context(raise: Raise<ArithmeticError>)
    fun subtract(other: Money): Money {
        requireSameCurrency(other)
        return calculate { amount - other.amount }
    }

    context(raise: Raise<ArithmeticError>)
    fun multiply(factor: Decimal): Money = calculate { amount * factor }

    context(raise: Raise<ArithmeticError>)
    fun divide(divisor: Decimal, scale: NonNegativeInt, roundingMode: RoundingMode): Money {
        raise.ensure(divisor.sign != 0) { ArithmeticError.DivisionByZero }
        return calculate { amount.divide(divisor, scale.value, roundingMode) }
    }

    context(raise: Raise<ArithmeticError>)
    fun round(scale: NonNegativeInt, roundingMode: RoundingMode): Money =
        calculate { amount.setScale(scale.value, roundingMode) }

    /** Numeric ordering is defined only within the same currency. */
    context(raise: Raise<ArithmeticError>)
    fun compare(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    operator fun unaryMinus(): Money = Money(-amount, currency)

    context(raise: Raise<ArithmeticError>)
    private fun requireSameCurrency(other: Money) {
        raise.ensure(currency == other.currency) { ArithmeticError.CurrencyMismatch(currency, other.currency) }
    }

    context(raise: Raise<ArithmeticError>)
    private inline fun calculate(operation: () -> Decimal): Money {
        val result = try {
            operation()
        } catch (_: ArithmeticException) {
            raise.raise(ArithmeticError.UnrepresentableResult)
        }
        return Money(result, currency)
    }

    override fun equals(other: Any?): Boolean =
        other is Money && currency == other.currency && amount.compareTo(other.amount) == 0

    override fun hashCode(): Int = 31 * amount.numericHashCode() + currency.hashCode()

    override fun toString(): String = "Money(amount=$amount, currency=${currency.code})"

    companion object : ValueFactory<Input, NonEmptyList<Error>, Money> {
        /** Parses decimal text and validates currency independently, accumulating both failures. */
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: Input): Money = validate {
            val amount by field { parseAmount(value.amount) }
            val currency by Currency.field(value.currency, Error::InvalidCurrency)
            create(amount, currency)
        }

        /** All signed decimal amounts are valid with an already validated currency. */
        fun create(amount: Decimal, currency: Currency): Money = Money(amount, currency)

        context(raise: Raise<Error>)
        private fun parseAmount(value: String): Decimal =
            Decimal.parseOrNull(value) ?: raise.raise(Error.InvalidAmount(value))
    }
}
