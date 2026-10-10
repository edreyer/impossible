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
    private val jpy = Currency.fromTrusted("JPY")
    private val gold = Currency.fromTrusted("XAU")

    private fun usd(amount: String) = Money.create(Decimal.parse(amount), usd)

    @Test
    fun `currencies know their minor units`() {
        assertEquals(2, usd.minorUnits)
        assertEquals(0, jpy.minorUnits)
        assertEquals(3, Currency.fromTrusted("KWD").minorUnits)
        assertNull(gold.minorUnits)
    }

    @Test
    fun `unit price times quantity and a percentage share are exact`() {
        assertEquals(Either.Right(usd("29.97")), calc { usd("9.99").multiply(PositiveInt.fromTrusted(3)) })
        assertEquals(Either.Right(usd("5.0000")), calc { usd("50.00").multiply(Percentage.fromTrusted(Decimal.of(10))) })
        assertEquals(Either.Right(usd("1.2345")), calc { usd("9.876").multiply(Percentage.fromTrusted(Decimal.parse("12.5"))) })
    }

    @Test
    fun `rounding to minor units follows the currency`() {
        assertEquals(Either.Right(usd("1.01")), calc { usd("1.005").roundToMinorUnits(RoundingMode.HALF_UP) })
        val yen = Money.create(Decimal.parse("1234.5"), jpy)
        assertEquals("1234", calc { yen.roundToMinorUnits(RoundingMode.HALF_EVEN) }.getOrNull()?.amount?.toPlainString())
        assertEquals(Either.Left(Money.ArithmeticError.NoMinorUnits(gold)),
            calc { Money.create(Decimal.ONE, gold).roundToMinorUnits(RoundingMode.HALF_UP) })
    }

    @Test
    fun `allocation distributes every minor unit and keeps the sum exact`() {
        val ones = nonEmptyListOf(PositiveInt.ONE, PositiveInt.ONE, PositiveInt.ONE)
        assertEquals(Either.Right(nonEmptyListOf(usd("3.34"), usd("3.33"), usd("3.33"))), calc { usd("10.00").allocate(ones) })
        assertEquals(Either.Right(nonEmptyListOf(usd("-3.34"), usd("-3.33"), usd("-3.33"))), calc { usd("-10").allocate(ones) })
        val weighted = nonEmptyListOf(PositiveInt.fromTrusted(70), PositiveInt.fromTrusted(30))
        assertEquals(Either.Right(nonEmptyListOf(usd("0.04"), usd("0.01"))), calc { usd("0.05").allocate(weighted) })
        assertEquals(Either.Right(nonEmptyListOf(usd("0.00"), usd("0.00"))), calc { usd("0").split(PositiveInt.fromTrusted(2)) })
        assertEquals(Either.Right(nonEmptyListOf(usd("100"))), calc { usd("100").split(PositiveInt.ONE) })
        val parts = calc { usd("1000.01").split(PositiveInt.fromTrusted(7)) }.getOrNull()!!
        assertEquals(Either.Right(usd("1000.01")), calc { Money.sum(parts) })
    }

    @Test
    fun `allocation rejects amounts below minor units and currencies without them`() {
        assertEquals(Either.Left(Money.ArithmeticError.NotInMinorUnits(Decimal.parse("1.005"), 2)),
            calc { usd("1.005").split(PositiveInt.fromTrusted(2)) })
        assertEquals(Either.Left(Money.ArithmeticError.NoMinorUnits(gold)),
            calc { Money.create(Decimal.ONE, gold).split(PositiveInt.fromTrusted(2)) })
    }

    @Test
    fun `sum requires one currency`() {
        assertEquals(Either.Right(usd("3.50")), calc { Money.sum(nonEmptyListOf(usd("1"), usd("2.5"))) })
        val yen = Money.create(Decimal.ONE, jpy)
        assertEquals(Either.Left(Money.ArithmeticError.CurrencyMismatch(usd, jpy)), calc { Money.sum(nonEmptyListOf(usd("1"), yen)) })
    }

    private inline fun <A> calc(block: Raise<Money.ArithmeticError>.() -> A): Either<Money.ArithmeticError, A> = either(block)
}
