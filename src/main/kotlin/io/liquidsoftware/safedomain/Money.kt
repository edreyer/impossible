package io.liquidsoftware.safedomain

import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A signed exact decimal [amount] in a validated [currency]. Sub-minor-unit amounts
 * are allowed for intermediate calculations. Construction preserves decimal scale;
 * equality and hashing use numeric amount and currency, so USD 1.0 equals USD 1.00.
 * No floating-point conversion, implicit rounding, or currency conversion is performed.
 * Arithmetic returns new values. Division and rounding require an explicit policy.
 */
class Money private constructor(val amount: BigDecimal, val currency: Currency) {
    data class Input(val amount: String, val currency: String)

    sealed interface Error {
        data class InvalidAmount(val value: String) : Error
        data class InvalidCurrency(val cause: Currency.Error) : Error
    }

    sealed interface ArithmeticError {
        data class CurrencyMismatch(val expected: Currency, val actual: Currency) : ArithmeticError
        data object DivisionByZero : ArithmeticError

        /** Decimal scale overflow, or a result requiring rounding when UNNECESSARY was requested. */
        data object UnrepresentableResult : ArithmeticError
    }

    context(raise: Raise<ArithmeticError>)
    fun add(other: Money): Money {
        requireSameCurrency(other)
        return calculate { amount.add(other.amount) }
    }

    context(raise: Raise<ArithmeticError>)
    fun subtract(other: Money): Money {
        requireSameCurrency(other)
        return calculate { amount.subtract(other.amount) }
    }

    context(raise: Raise<ArithmeticError>)
    fun multiply(factor: BigDecimal): Money = calculate { amount.multiply(factor) }

    context(raise: Raise<ArithmeticError>)
    fun divide(divisor: BigDecimal, scale: NonNegativeInt, roundingMode: RoundingMode): Money {
        raise.ensure(divisor.signum() != 0) { ArithmeticError.DivisionByZero }
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

    operator fun unaryMinus(): Money = Money(amount.negate(), currency)

    context(raise: Raise<ArithmeticError>)
    private fun requireSameCurrency(other: Money) {
        raise.ensure(currency == other.currency) { ArithmeticError.CurrencyMismatch(currency, other.currency) }
    }

    context(raise: Raise<ArithmeticError>)
    private inline fun calculate(operation: () -> BigDecimal): Money {
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
        /** Parses BigDecimal text and validates currency independently, accumulating both failures. */
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: Input): Money = validate {
            val amount by field { parseAmount(value.amount) }
            val currency by Currency.field(value.currency, Error::InvalidCurrency)
            create(amount, currency)
        }

        /** All signed BigDecimal amounts are valid with an already validated currency. */
        fun create(amount: BigDecimal, currency: Currency): Money = Money(amount, currency)

        context(raise: Raise<Error>)
        private fun parseAmount(value: String): BigDecimal = try {
            BigDecimal(value)
        } catch (_: NumberFormatException) {
            raise.raise(Error.InvalidAmount(value))
        }
    }
}
