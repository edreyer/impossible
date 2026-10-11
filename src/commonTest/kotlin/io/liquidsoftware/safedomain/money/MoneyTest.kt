package io.liquidsoftware.safedomain.money

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.numeric.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class MoneyTest {
    private val usd = Currency.fromTrusted("USD")
    private val eur = Currency.fromTrusted("EUR")
    private val jpy = Currency.fromTrusted("JPY")
    private val kwd = Currency.fromTrusted("KWD")
    private val gold = Currency.fromTrusted("XAU")

    private fun money(amount: String, currency: Currency = usd): Money =
        assertIs<Either.Right<Money>>(either<Money.Error, Money> { Money.of(Decimal.parse(amount), currency) }).value

    @Test
    fun `amounts are stored as whole minor units of the currency`() {
        assertEquals(1234L, money("12.34").minor)
        assertEquals(1234L, money("1234", jpy).minor)
        assertEquals(12345L, money("12.345", kwd).minor)
        assertEquals(-1230L, money("-12.3").minor)
        assertEquals(1_000_000L, money("1E+4").minor)
        assertEquals(Decimal.parse("12.30"), money("12.3").amount)
        assertEquals(Decimal.parse("1234"), money("1234", jpy).amount)
    }

    @Test
    fun `amounts finer than the minor unit are rejected never rounded`() {
        assertEquals(
            Either.Left(Money.Error.NotInMinorUnits(Decimal.parse("1.005"), 2)),
            either<Money.Error, Money> { Money.of(Decimal.parse("1.005"), usd) },
        )
        assertEquals(
            Either.Left(Money.Error.NotInMinorUnits(Decimal.parse("0.5"), 0)),
            either<Money.Error, Money> { Money.of(Decimal.parse("0.5"), jpy) },
        )
        assertEquals(money("1.00"), money("1.000"))
    }

    @Test
    fun `currencies without minor units and amounts beyond Long are rejected`() {
        assertEquals(
            Either.Left(Money.Error.NoMinorUnits(gold)),
            either<Money.Error, Money> {
                Money.of(Decimal.ONE, gold)
            },
        )
        assertEquals(Either.Left(Money.Error.NoMinorUnits(gold)), either<Money.Error, Money> { Money.ofMinor(1, gold) })
        val huge = Decimal.parse("92233720368547758.08")
        assertEquals(Either.Left(Money.Error.OutOfRange(huge)), either<Money.Error, Money> { Money.of(huge, usd) })
        assertEquals(Long.MAX_VALUE, money("92233720368547758.07").minor)
    }

    @Test
    fun `minor unit construction and zero`() {
        val result = either<Money.Error, Unit> {
            assertEquals(money("12.34"), Money.ofMinor(1234, usd))
            assertEquals(money("0"), Money.zero(usd))
            assertEquals(0, Money.zero(usd).sign)
            assertEquals(-1, Money.ofMinor(-1, usd).sign)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `parser accumulates amount and currency failures then checks minor units`() {
        val result: Either<NonEmptyList<Money.Error>, Money> = either { Money.parser.from(Money.Text("oops", "ZZZ")) }
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    Money.Error.InvalidAmount("oops"),
                    Money.Error.InvalidCurrency(Currency.Error.UnsupportedCode("ZZZ")),
                ),
            ),
            result,
        )
        for (raw in listOf("", "NaN", "Infinity", " 1.00", "1.00 ", "1,00", "1e2147483649")) {
            val invalid: Either<NonEmptyList<Money.Error>, Money> = either { Money.parser.from(Money.Text(raw, "USD")) }
            assertEquals(Either.Left(nonEmptyListOf(Money.Error.InvalidAmount(raw))), invalid)
        }
        assertEquals(
            Either.Left(nonEmptyListOf(Money.Error.NotInMinorUnits(Decimal.parse("0.001"), 2))),
            either<NonEmptyList<Money.Error>, Money> { Money.parser.from(Money.Text("0.001", "USD")) },
        )
        assertEquals(
            Either.Right(money("12.34")),
            either<NonEmptyList<Money.Error>, Money> { Money.parser.from(Money.Text("12.340", "USD")) },
        )
    }

    @Test
    fun `typed input validates the currency and minor units`() {
        assertEquals(
            Either.Right(money("-1.50")),
            either<Money.Error, Money> {
                Money.from(Money.Input(Decimal.parse("-1.50"), "USD"))
            },
        )
        assertEquals(
            Either.Left(Money.Error.InvalidCurrency(Currency.Error.UnsupportedCode("usd"))),
            either<Money.Error, Money> { Money.from(Money.Input(Decimal.ONE, "usd")) },
        )
        assertEquals(
            Either.Left(Money.Error.NoMinorUnits(Currency.fromTrusted("XXX"))),
            either<Money.Error, Money> { Money.from(Money.Input(Decimal.ONE, "XXX")) },
        )
    }

    @Test
    fun `equality and hashing use minor units and currency`() {
        assertEquals(money("12.30"), money("12.3000"))
        assertEquals(money("12.30").hashCode(), money("12.3000").hashCode())
        assertEquals(1, setOf(money("12.30"), money("12.3")).size)
        assertNotEquals(money("12.30"), money("12.30", eur))
        assertEquals(money("-1000"), money("-1E3"))
        assertEquals("Money(amount=12.30, currency=USD)", money("12.3").toString())
    }
}
