package io.liquidsoftware.safedomain.money

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import io.liquidsoftware.safedomain.numeric.NonNegativeInt
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class MoneyTest {
    private val usd = assertIs<Either.Right<Currency>>(either { Currency.from("USD") }).value
    private val eur = assertIs<Either.Right<Currency>>(either { Currency.from("EUR") }).value
    private val cents = assertIs<Either.Right<NonNegativeInt>>(either { NonNegativeInt.from(2) }).value

    @Test
    fun `money accepts signed exact decimals and preserves input precision`() {
        for (raw in listOf("0", "-12.30", "0.001", "1E+4")) {
            val money = assertIs<Either.Right<Money>>(either { Money.from(Money.Input(raw, "USD")) }).value
            assertEquals(BigDecimal(raw), money.amount)
            assertEquals(usd, money.currency)
        }
    }

    @Test
    fun `money accumulates amount and currency failures independently`() {
        val result: Either<NonEmptyList<Money.Error>, Money> = either { Money.from(Money.Input("oops", "ZZZ")) }
        assertEquals(Either.Left(nonEmptyListOf(
            Money.Error.InvalidAmount("oops"),
            Money.Error.InvalidCurrency(Currency.Error.UnsupportedCode("ZZZ")),
        )), result)
        for (raw in listOf("", "NaN", "Infinity", " 1.00", "1.00 ", "1,00", "1e2147483649")) {
            val invalid: Either<NonEmptyList<Money.Error>, Money> = either { Money.from(Money.Input(raw, "USD")) }
            assertEquals(Either.Left(nonEmptyListOf(Money.Error.InvalidAmount(raw))), invalid)
        }
    }

    @Test
    fun `validated currency construction retains exact amounts without another parse`() {
        val money = Money.create(BigDecimal("12.3400"), usd)
        assertEquals(BigDecimal("12.3400"), money.amount)
        assertEquals(usd, money.currency)
    }

    @Test
    fun `numeric equality and hashing ignore decimal scale but include currency`() {
        val first = Money.create(BigDecimal("12.30"), usd)
        val second = Money.create(BigDecimal("12.3000"), usd)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(1, setOf(first, second).size)
        assertEquals("balance", mapOf(first to "balance")[second])
        assertNotEquals(first, Money.create(BigDecimal("12.30"), eur))
        assertEquals(Money.create(BigDecimal("0.00"), usd), Money.create(BigDecimal("0"), usd))
        assertEquals(Money.create(BigDecimal("0.00"), usd).hashCode(), Money.create(BigDecimal("0"), usd).hashCode())
        val negative = Money.create(BigDecimal("-1000"), usd)
        assertEquals(negative, Money.create(BigDecimal("-1E3"), usd))
        assertEquals(negative.hashCode(), Money.create(BigDecimal("-1E3"), usd).hashCode())
    }

    @Test
    fun `equality and hashing support extreme BigDecimal scales without normalization overflow`() {
        val first = Money.create(BigDecimal(BigInteger.TEN, Int.MIN_VALUE), usd)
        val second = Money.create(BigDecimal(BigInteger.valueOf(100), Int.MIN_VALUE + 1), usd)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `money addition subtraction multiplication and negation are exact and immutable`() {
        val first = Money.create(BigDecimal("0.10"), usd)
        val second = Money.create(BigDecimal("0.20"), usd)
        val result = either {
            assertEquals(BigDecimal("0.30"), first.add(second).amount)
            assertEquals(BigDecimal("-0.10"), first.subtract(second).amount)
            assertEquals(BigDecimal("0.0150"), first.multiply(BigDecimal("0.15")).amount)
            assertEquals(BigDecimal("-0.10"), (-first).amount)
            assertEquals(BigDecimal("0.10"), first.amount)
            assertEquals(usd, first.multiply(BigDecimal("2")).currency)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `currency mismatches are rejected for addition subtraction and comparison`() {
        val dollars = Money.create(BigDecimal("10"), usd)
        val euros = Money.create(BigDecimal("10"), eur)
        val expected = Money.ArithmeticError.CurrencyMismatch(usd, eur)
        val add: Either<Money.ArithmeticError, Money> = either { dollars.add(euros) }
        val subtract: Either<Money.ArithmeticError, Money> = either { dollars.subtract(euros) }
        val compare: Either<Money.ArithmeticError, Int> = either { dollars.compare(euros) }
        assertEquals(Either.Left(expected), add)
        assertEquals(Either.Left(expected), subtract)
        assertEquals(Either.Left(expected), compare)
    }

    @Test
    fun `comparison uses numeric amounts within a currency`() {
        val result = either {
            assertEquals(-1, Money.create(BigDecimal("-1"), usd).compare(Money.create(BigDecimal.ZERO, usd)))
            assertEquals(0, Money.create(BigDecimal("1.00"), usd).compare(Money.create(BigDecimal.ONE, usd)))
            assertEquals(1, Money.create(BigDecimal("2"), usd).compare(Money.create(BigDecimal.ONE, usd)))
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `division and rounding require an explicit scale and rounding mode`() {
        val result = either {
            val one = Money.create(BigDecimal.ONE, usd)
            assertEquals(BigDecimal("0.33"), one.divide(BigDecimal("3"), cents, RoundingMode.DOWN).amount)
            assertEquals(BigDecimal("0.34"), one.divide(BigDecimal("3"), cents, RoundingMode.UP).amount)
            val half = Money.create(BigDecimal("1.005"), usd)
            assertEquals(BigDecimal("1.00"), half.round(cents, RoundingMode.HALF_EVEN).amount)
            assertEquals(BigDecimal("1.01"), half.round(cents, RoundingMode.HALF_UP).amount)
            assertEquals(BigDecimal("1.005"), half.amount)
            assertEquals(BigDecimal("1.00"), one.round(cents, RoundingMode.UNNECESSARY).amount)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `division by zero and unrepresentable results remain typed failures`() {
        val one = Money.create(BigDecimal.ONE, usd)
        val zero: Either<Money.ArithmeticError, Money> = either { one.divide(BigDecimal.ZERO, cents, RoundingMode.DOWN) }
        assertEquals(Either.Left(Money.ArithmeticError.DivisionByZero), zero)
        val third: Either<Money.ArithmeticError, Money> = either { one.divide(BigDecimal("3"), cents, RoundingMode.UNNECESSARY) }
        assertEquals(Either.Left(Money.ArithmeticError.UnrepresentableResult), third)
        val rounding: Either<Money.ArithmeticError, Money> = either {
            Money.create(BigDecimal("1.005"), usd).round(cents, RoundingMode.UNNECESSARY)
        }
        assertEquals(Either.Left(Money.ArithmeticError.UnrepresentableResult), rounding)
        val overflow: Either<Money.ArithmeticError, Money> = either {
            Money.create(BigDecimal(BigInteger.ONE, Int.MAX_VALUE), usd).multiply(BigDecimal("0.1"))
        }
        assertEquals(Either.Left(Money.ArithmeticError.UnrepresentableResult), overflow)
    }
}
