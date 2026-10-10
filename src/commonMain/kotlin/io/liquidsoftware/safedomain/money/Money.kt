package io.liquidsoftware.safedomain.money

import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveInt
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
    /** An already parsed amount and a currency code to validate. */
    data class Input(val amount: Decimal, val currency: String)

    /** Amount and currency as text, for [parser]. */
    data class Text(val amount: String, val currency: String)

    sealed interface Error {
        data class InvalidAmount(val value: String) : Error
        data class InvalidCurrency(val cause: Currency.Error) : Error
    }

    sealed interface ArithmeticError {
        data class CurrencyMismatch(val expected: Currency, val actual: Currency) : ArithmeticError
        data object DivisionByZero : ArithmeticError

        /** The currency has no minor unit (such as XAU), so minor-unit rounding is undefined. */
        data class NoMinorUnits(val currency: Currency) : ArithmeticError

        /** Allocation needs an amount already rounded to the currency's minor units. */
        data class NotInMinorUnits(val amount: Decimal, val minorUnits: Int) : ArithmeticError

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

    /** Unit price times quantity. */
    context(raise: Raise<ArithmeticError>)
    fun multiply(quantity: PositiveInt): Money = calculate { amount * Decimal.of(quantity.value.toLong()) }

    /** The given share of this amount, exactly: 10% of USD 50.00 is USD 5.0000. */
    context(raise: Raise<ArithmeticError>)
    fun multiply(percentage: Percentage): Money = calculate { amount * percentage.ratio }

    context(raise: Raise<ArithmeticError>)
    fun divide(divisor: Decimal, scale: Int, roundingMode: RoundingMode): Money {
        raise.ensure(divisor.sign != 0) { ArithmeticError.DivisionByZero }
        return calculate { amount.divide(divisor, scale, roundingMode) }
    }

    context(raise: Raise<ArithmeticError>)
    fun round(scale: Int, roundingMode: RoundingMode): Money =
        calculate { amount.setScale(scale, roundingMode) }

    /** Rounds to the currency's minor units, such as cents for USD or whole yen for JPY. */
    context(raise: Raise<ArithmeticError>)
    fun roundToMinorUnits(roundingMode: RoundingMode): Money = round(minorUnits(), roundingMode)

    /**
     * Splits this amount into parts proportional to [ratios], in minor units, so the parts
     * always sum exactly to this amount. Remainder units go one each to the first parts,
     * in order. `USD 10.00` allocated `1:1:1` is `3.34, 3.33, 3.33`. The amount must
     * already be in minor units; round first if needed.
     */
    context(raise: Raise<ArithmeticError>)
    fun allocate(ratios: NonEmptyList<PositiveInt>): NonEmptyList<Money> {
        val units = minorUnits()
        val total = try {
            amount.setScale(units, RoundingMode.UNNECESSARY)
        } catch (_: ArithmeticException) {
            raise.raise(ArithmeticError.NotInMinorUnits(amount, units))
        }
        val ratioSum = Decimal.of(ratios.sumOf { it.value.toLong() })
        val shares = ratios.map { ratio ->
            (total * Decimal.of(ratio.value.toLong())).divide(ratioSum, units, RoundingMode.DOWN)
        }.toMutableList()
        // Truncation toward zero leaves fewer than ratios.size units, with the total's sign.
        val step = Decimal.of(if (total.sign < 0) -1 else 1, units)
        var remainder = shares.fold(total) { left, share -> left - share }
        var index = 0
        while (remainder.sign != 0) {
            shares[index] = shares[index] + step
            remainder -= step
            index++
        }
        return NonEmptyList(Money(shares.first(), currency), shares.drop(1).map { Money(it, currency) })
    }

    /** Splits this amount into [parts] equal parts, as [allocate] does. */
    context(raise: Raise<ArithmeticError>)
    fun split(parts: PositiveInt): NonEmptyList<Money> =
        allocate(NonEmptyList(PositiveInt.ONE, List(parts.value - 1) { PositiveInt.ONE }))

    /** Numeric ordering is defined only within the same currency. */
    context(raise: Raise<ArithmeticError>)
    fun compare(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    operator fun unaryMinus(): Money = Money(-amount, currency)

    context(raise: Raise<ArithmeticError>)
    private fun minorUnits(): Int =
        raise.ensureNotNull(currency.minorUnits) { ArithmeticError.NoMinorUnits(currency) }

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

    companion object : ValueFactory<Input, Error, Money> {
        /** Validates the currency code; every signed decimal amount is valid. */
        context(raise: Raise<Error>)
        override fun from(value: Input): Money =
            Money(value.amount, Currency.from(value.currency) { Error.InvalidCurrency(it) })

        /** Parses decimal text and validates currency independently, accumulating both failures. */
        val parser: ValueFactory<Text, NonEmptyList<Error>, Money> =
            object : ValueFactory<Text, NonEmptyList<Error>, Money> {
                context(raise: Raise<NonEmptyList<Error>>)
                override fun from(value: Text): Money = validate {
                    val amount by field { parseAmount(value.amount) }
                    val currency by Currency.field(value.currency, Error::InvalidCurrency)
                    create(amount, currency)
                }
            }

        /** All signed decimal amounts are valid with an already validated currency. */
        fun create(amount: Decimal, currency: Currency): Money = Money(amount, currency)

        /** The sum of [values], which must all share one currency. */
        context(raise: Raise<ArithmeticError>)
        fun sum(values: NonEmptyList<Money>): Money =
            values.tail.fold(values.head) { total, next -> total.add(next) }

        context(raise: Raise<Error>)
        private fun parseAmount(value: String): Decimal =
            Decimal.parseOrNull(value) ?: raise.raise(Error.InvalidAmount(value))
    }
}
