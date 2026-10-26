package io.liquidsoftware.safedomain.money

import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.RoundingMode
import io.liquidsoftware.safedomain.validate

/**
 * A signed whole number of a currency's minor units ([minor]), such as 1234 cents for
 * USD 12.34 or 1234 yen for JPY 1234. An amount finer than the minor unit cannot be
 * represented, and currencies without a minor unit (XAU, XXX) cannot be money.
 * Equality and hashing use [minor] and [currency]. Exact operations raise only on
 * currency mismatch or [Long] overflow; operations that can produce a fraction of a
 * minor unit take an explicit [RoundingMode]. Keep sub-minor values such as unit
 * prices and rates as [Decimal] and round into money once.
 */
public class Money private constructor(public val minor: Long, public val currency: Currency, private val scale: Int) {
    /** An already parsed amount and a currency code to validate. */
    public data class Input(val amount: Decimal, val currency: String)

    /** Amount and currency as text, for [parser]. */
    public data class Text(val amount: String, val currency: String)

    public sealed interface Error : ValidationError {
        public data class InvalidAmount(val value: String) : Error
        public data class InvalidCurrency(val cause: Currency.Error) : Error

        /** The currency has no minor unit (such as XAU or XXX). */
        public data class NoMinorUnits(val currency: Currency) : Error

        /** The amount has more decimal places than the currency's minor unit. */
        public data class NotInMinorUnits(val amount: Decimal, val minorUnits: Int) : Error

        /** The amount in minor units does not fit in a [Long]. */
        public data class OutOfRange(val amount: Decimal) : Error
    }

    public sealed interface ArithmeticError : ValidationError {
        public data class CurrencyMismatch(val expected: Currency, val actual: Currency) : ArithmeticError
        public data object DivisionByZero : ArithmeticError

        /** The result in minor units does not fit in a [Long]. */
        public data object Overflow : ArithmeticError

        /** The result needed rounding but [RoundingMode.UNNECESSARY] was requested. */
        public data object RoundingRequired : ArithmeticError
    }

    /** The amount in major units, at the currency's scale: USD 12.34, JPY 1234. */
    public val amount: Decimal get() = Decimal.of(minor, scale)

    /** -1, 0 or 1 for negative, zero or positive amounts. */
    public val sign: Int get() = minor.compareTo(0L)

    context(raise: Raise<ArithmeticError>)
    public fun add(other: Money): Money {
        requireSameCurrency(other)
        val sum = minor + other.minor
        // Overflow occurred when both operands share a sign the result lacks.
        raise.ensure(((minor xor sum) and (other.minor xor sum)) >= 0) { ArithmeticError.Overflow }
        return Money(sum, currency, scale)
    }

    context(raise: Raise<ArithmeticError>)
    public fun subtract(other: Money): Money {
        requireSameCurrency(other)
        return add(other.negate())
    }

    /** The same amount with the opposite sign. */
    context(raise: Raise<ArithmeticError>)
    public fun negate(): Money {
        raise.ensure(minor != Long.MIN_VALUE) { ArithmeticError.Overflow }
        return Money(-minor, currency, scale)
    }

    /** [negate] for callers that know [minor] is not [Long.MIN_VALUE]. */
    internal fun negateUnchecked(): Money = Money(-minor, currency, scale)

    /** Unit price times quantity, exactly. */
    context(raise: Raise<ArithmeticError>)
    public fun multiply(quantity: PositiveInt): Money =
        fromDecimal(amount * Decimal.of(quantity.value.toLong()), RoundingMode.UNNECESSARY)

    /** This amount times [factor], rounded to minor units with [roundingMode]. */
    context(raise: Raise<ArithmeticError>)
    public fun multiply(factor: Decimal, roundingMode: RoundingMode): Money =
        fromDecimal(amount * factor, roundingMode)

    /** The given share of this amount, rounded to minor units: 10% of USD 50.00 is USD 5.00. */
    context(raise: Raise<ArithmeticError>)
    public fun multiply(percentage: Percentage, roundingMode: RoundingMode): Money =
        fromDecimal(amount * percentage.ratio, roundingMode)

    context(raise: Raise<ArithmeticError>)
    public fun divide(divisor: Decimal, roundingMode: RoundingMode): Money {
        raise.ensure(divisor.sign != 0) { ArithmeticError.DivisionByZero }
        return calculate { amount.divide(divisor, scale, roundingMode) }
    }

    /**
     * Splits this amount into parts proportional to [ratios], so the parts always sum
     * exactly to this amount. Each part gets its share truncated toward zero, then the
     * remaining minor units go one each to the first parts, in order: `USD 10.00`
     * allocated `1:1:1` is `3.34, 3.33, 3.33`. Never fails.
     */
    public fun allocate(ratios: NonEmptyList<PositiveInt>): NonEmptyList<Money> {
        val ratioSum = Decimal.of(ratios.sumOf { it.value.toLong() })
        val total = Decimal.of(minor)
        val shares = ratios.map { ratio ->
            // |share| <= |minor|, so it always fits in a Long.
            val share = (total * Decimal.of(ratio.value.toLong())).divide(ratioSum, 0, RoundingMode.DOWN)
            share.unscaledLongOrNull()!!
        }.toMutableList()
        // Truncation leaves fewer than ratios.size units, with the total's sign.
        var remainder = minor - shares.sum()
        val step = if (remainder < 0) -1L else 1L
        var index = 0
        while (remainder != 0L) {
            shares[index] += step
            remainder -= step
            index++
        }
        val parts = shares.map { Money(it, currency, scale) }
        return NonEmptyList(parts.first(), parts.drop(1))
    }

    /** Splits this amount into [parts] equal parts, as [allocate] does. */
    public fun split(parts: PositiveInt): NonEmptyList<Money> =
        allocate(NonEmptyList(PositiveInt.ONE, List(parts.value - 1) { PositiveInt.ONE }))

    /** Numeric ordering, defined only within the same currency; there is no ordering across currencies. */
    context(raise: Raise<ArithmeticError>)
    public fun compare(other: Money): Int {
        requireSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    context(raise: Raise<ArithmeticError>)
    private fun requireSameCurrency(other: Money) {
        raise.ensure(currency == other.currency) { ArithmeticError.CurrencyMismatch(currency, other.currency) }
    }

    context(raise: Raise<ArithmeticError>)
    private fun fromDecimal(value: Decimal, roundingMode: RoundingMode): Money =
        calculate { value.setScale(scale, roundingMode) }

    /** Runs [operation], which must return a value at this currency's scale. */
    context(raise: Raise<ArithmeticError>)
    private inline fun calculate(operation: () -> Decimal): Money {
        val result = try {
            operation()
        } catch (_: ArithmeticException) {
            raise.raise(ArithmeticError.RoundingRequired)
        }
        val minorUnits = raise.ensureNotNull(result.unscaledLongOrNull()) { ArithmeticError.Overflow }
        return Money(minorUnits, currency, scale)
    }

    override fun equals(other: Any?): Boolean = other is Money && minor == other.minor && currency == other.currency

    override fun hashCode(): Int = 31 * minor.hashCode() + currency.hashCode()

    override fun toString(): String = "Money(amount=${amount.toPlainString()}, currency=${currency.code})"

    public companion object : ValueFactory<Input, Error, Money> {
        /** Validates the currency, then requires the amount to be exact in its minor units. */
        context(raise: Raise<Error>)
        override fun from(value: Input): Money =
            of(value.amount, Currency.from(value.currency) { Error.InvalidCurrency(it) })

        /**
         * Parses decimal text and validates the currency independently, accumulating both
         * failures, then requires the amount to be exact in the currency's minor units.
         */
        public val parser: ValueFactory<Text, NonEmptyList<Error>, Money> =
            object : ValueFactory<Text, NonEmptyList<Error>, Money> {
                context(raise: Raise<NonEmptyList<Error>>)
                override fun from(value: Text): Money = validate {
                    val amount by field { parseAmount(value.amount) }
                    val currency by Currency.field(value.currency, Error::InvalidCurrency)
                    val money by field { of(amount, currency) }
                    money
                }
            }

        /** An amount in major units, such as `12.34` USD; `12.345` USD is rejected, not rounded. */
        context(raise: Raise<Error>)
        public fun of(amount: Decimal, currency: Currency): Money {
            val scale = minorUnitsOf(currency)
            val exact = try {
                amount.setScale(scale, RoundingMode.UNNECESSARY)
            } catch (_: ArithmeticException) {
                raise.raise(Error.NotInMinorUnits(amount, scale))
            }
            val minor = raise.ensureNotNull(exact.unscaledLongOrNull()) { Error.OutOfRange(amount) }
            return Money(minor, currency, scale)
        }

        /** An amount in minor units, such as 1234 cents. */
        context(raise: Raise<Error>)
        public fun ofMinor(minor: Long, currency: Currency): Money = Money(minor, currency, minorUnitsOf(currency))

        /** Zero in [currency]. */
        context(raise: Raise<Error>)
        public fun zero(currency: Currency): Money = ofMinor(0, currency)

        /** The sum of [values], which must all share one currency. */
        context(raise: Raise<ArithmeticError>)
        public fun sum(values: NonEmptyList<Money>): Money =
            values.tail.fold(values.head) { total, next -> total.add(next) }

        context(raise: Raise<Error>)
        private fun minorUnitsOf(currency: Currency): Int =
            raise.ensureNotNull(currency.minorUnits) { Error.NoMinorUnits(currency) }

        context(raise: Raise<Error>)
        private fun parseAmount(value: String): Decimal =
            Decimal.parseOrNull(value) ?: raise.raise(Error.InvalidAmount(value))
    }
}
