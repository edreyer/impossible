package io.liquidsoftware.safedomain.money

import arrow.core.Either
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyOperationsTest {
    private val usd = Currency.fromTrusted("USD")
    private val eur = Currency.fromTrusted("EUR")
    private val jpy = Currency.fromTrusted("JPY")

    private fun money(amount: Decimal, currency: Currency): Money =
        either<Money.Error, Money> { Money.of(amount, currency) }.getOrNull()!!

    private fun usd(amount: String): Money = money(Decimal.parse(amount), usd)

    private fun usdMinor(minor: Long): Money = either<Money.Error, Money> { Money.ofMinor(minor, usd) }.getOrNull()!!

    @Test
    fun `currencies know their minor units`() {
        assertEquals(2, usd.minorUnits)
        assertEquals(0, jpy.minorUnits)
        assertEquals(3, Currency.fromTrusted("KWD").minorUnits)
        assertNull(Currency.fromTrusted("XAU").minorUnits)
    }

    @Test
    fun `addition subtraction negation and quantity are exact`() {
        assertEquals(Either.Right(usd("0.30")), calc { usd("0.10").add(usd("0.20")) })
        assertEquals(Either.Right(usd("-0.10")), calc { usd("0.10").subtract(usd("0.20")) })
        assertEquals(Either.Right(usd("-0.10")), calc { usd("0.10").negate() })
        assertEquals(Either.Right(usd("29.97")), calc { usd("9.99").multiply(PositiveInt.fromTrusted(3)) })
    }

    @Test
    fun `arithmetic overflow is a typed failure not a wraparound`() {
        val max = usdMinor(Long.MAX_VALUE)
        val min = usdMinor(Long.MIN_VALUE)
        assertEquals(Either.Left(Money.ArithmeticError.Overflow), calc { max.add(usdMinor(1)) })
        assertEquals(Either.Left(Money.ArithmeticError.Overflow), calc { min.subtract(usdMinor(1)) })
        assertEquals(Either.Left(Money.ArithmeticError.Overflow), calc { min.negate() })
        assertEquals(Either.Left(Money.ArithmeticError.Overflow), calc { max.multiply(PositiveInt.fromTrusted(2)) })
        assertEquals(Either.Right(usdMinor(Long.MAX_VALUE - 1)), calc { max.subtract(usdMinor(1)) })
    }

    @Test
    fun `fractional results need an explicit rounding mode`() {
        assertEquals(Either.Right(usd("5.00")), calc { usd("50.00").multiply(Percentage.fromTrusted(Decimal.of(10)), RoundingMode.HALF_EVEN) })
        assertEquals(Either.Right(usd("1.23")), calc { usd("9.87").multiply(Percentage.fromTrusted(Decimal.parse("12.5")), RoundingMode.HALF_EVEN) })
        assertEquals(Either.Right(usd("1.24")), calc { usd("9.87").multiply(Percentage.fromTrusted(Decimal.parse("12.5")), RoundingMode.UP) })
        assertEquals(Either.Right(usd("0.02")), calc { usd("0.10").multiply(Decimal.parse("0.15"), RoundingMode.HALF_UP) })
        assertEquals(Either.Right(usd("0.33")), calc { usd("1").divide(Decimal.parse("3"), RoundingMode.DOWN) })
        assertEquals(Either.Right(usd("0.34")), calc { usd("1").divide(Decimal.parse("3"), RoundingMode.UP) })
        assertEquals(Either.Left(Money.ArithmeticError.RoundingRequired), calc { usd("1").divide(Decimal.parse("3"), RoundingMode.UNNECESSARY) })
        assertEquals(Either.Left(Money.ArithmeticError.DivisionByZero), calc { usd("1").divide(Decimal.ZERO, RoundingMode.DOWN) })
        assertEquals(Either.Right(usd("0.50")), calc { usd("1").divide(Decimal.parse("2"), RoundingMode.UNNECESSARY) })
    }

    @Test
    fun `allocation distributes every minor unit and keeps the sum exact`() {
        val ones = nonEmptyListOf(PositiveInt.ONE, PositiveInt.ONE, PositiveInt.ONE)
        assertEquals(nonEmptyListOf(usd("3.34"), usd("3.33"), usd("3.33")), usd("10.00").allocate(ones))
        assertEquals(nonEmptyListOf(usd("-3.34"), usd("-3.33"), usd("-3.33")), usd("-10").allocate(ones))
        val weighted = nonEmptyListOf(PositiveInt.fromTrusted(70), PositiveInt.fromTrusted(30))
        assertEquals(nonEmptyListOf(usd("0.04"), usd("0.01")), usd("0.05").allocate(weighted))
        assertEquals(nonEmptyListOf(usd("0"), usd("0")), usd("0").split(PositiveInt.fromTrusted(2)))
        assertEquals(nonEmptyListOf(usd("100")), usd("100").split(PositiveInt.ONE))
        val parts = usd("1000.01").split(PositiveInt.fromTrusted(7))
        assertEquals(Either.Right(usd("1000.01")), calc { Money.sum(parts) })
        val extreme = usdMinor(Long.MAX_VALUE).split(PositiveInt.fromTrusted(3))
        assertEquals(Either.Right(usdMinor(Long.MAX_VALUE)), calc { Money.sum(extreme) })
    }

    @Test
    fun `sum and comparison require one currency`() {
        assertEquals(Either.Right(usd("3.50")), calc { Money.sum(nonEmptyListOf(usd("1"), usd("2.5"))) })
        val euros = money(Decimal.ONE, eur)
        val mismatch = Money.ArithmeticError.CurrencyMismatch(usd, eur)
        assertEquals(Either.Left(mismatch), calc { Money.sum(nonEmptyListOf(usd("1"), euros)) })
        assertEquals(Either.Left(mismatch), calc { usd("1").add(euros) })
        assertEquals(Either.Left(mismatch), calc { usd("1").subtract(euros) })
        assertEquals(Either.Left(mismatch), calc { usd("1").compare(euros) })
        assertEquals(Either.Right(-1), calc { usd("-1").compare(usd("0")) })
        assertEquals(Either.Right(0), calc { usd("1.00").compare(usd("1")) })
    }

    private inline fun <A> calc(block: Raise<Money.ArithmeticError>.() -> A): Either<Money.ArithmeticError, A> = either(block)
}
