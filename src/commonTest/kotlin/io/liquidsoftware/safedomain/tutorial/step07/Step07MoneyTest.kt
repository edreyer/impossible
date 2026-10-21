package io.liquidsoftware.safedomain.tutorial.step07

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.flatField
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.PositiveMoney
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.RoundingMode
import io.liquidsoftware.safedomain.optional
import io.liquidsoftware.safedomain.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// Code for docs/tutorial/07-decimals-and-money.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

val usd: Currency = Currency.fromTrusted("USD")

fun dollars(amount: String): Money = Money.fromTrusted(Money.Input(Decimal.parse(amount), "USD"))

context(raise: Raise<Money.Error>)
fun price(amount: String, currency: Currency): Money = Money.of(Decimal.parse(amount), currency)

data class PriceTagInput(val price: Money.Text, val discount: Decimal?)

class PriceTag private constructor(val price: PositiveMoney, val discount: Percentage?) {
    sealed interface Error {
        data class InvalidPrice(val cause: Money.Error) : Error
        data class NotPositive(val cause: PositiveMoney.Error) : Error
        data class InvalidDiscount(val cause: Percentage.Error) : Error
    }

    companion object : ValueFactory<PriceTagInput, NonEmptyList<Error>, PriceTag> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: PriceTagInput): PriceTag = validate {
            val price by field {
                val money by Money.parser.flatField(value.price, Error::InvalidPrice)
                PositiveMoney.from(money, Error::NotPositive)
            }
            val discount by Percentage.optional().field(value.discount, Error::InvalidDiscount)
            PriceTag(price, discount)
        }
    }
}

context(raise: Raise<Money.ArithmeticError>)
fun lineTotal(unitPrice: Money, copies: PositiveInt, discount: Percentage): Money {
    val gross = unitPrice.multiply(copies)
    return gross.subtract(gross.multiply(discount, RoundingMode.HALF_EVEN))
}

class Step07MoneyTest {
    private fun tag(input: PriceTagInput): Either<NonEmptyList<PriceTag.Error>, PriceTag> =
        either { PriceTag.from(input) }

    @Test
    fun `decimals keep their scale and compare by value`() {
        val a = Decimal.parse("12.30")
        val b = Decimal.parse("12.3")
        assertEquals("12.30", a.toString())
        assertNotEquals(a, b)
        assertEquals(0, a.compareTo(b))
        assertEquals(Decimal.parse("0.125"), Percentage.fromTrusted(Decimal.parse("12.5")).ratio)
    }

    @Test
    fun `money is exact in minor units and never rounds on the way in`() {
        assertEquals(1299L, either<Money.Error, Money> { price("12.99", usd) }.getOrNull()?.minor)
        assertEquals(
            Either.Left(Money.Error.NotInMinorUnits(Decimal.parse("12.995"), 2)),
            either<Money.Error, Money> { price("12.995", usd) },
        )
    }

    @Test
    fun `flatField merges a nested batch into the parent list`() {
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    PriceTag.Error.InvalidPrice(Money.Error.InvalidAmount("twelve")),
                    PriceTag.Error.InvalidPrice(Money.Error.InvalidCurrency(Currency.Error.UnsupportedCode("usd"))),
                    PriceTag.Error.InvalidDiscount(Percentage.Error.OutOfRange(Decimal.parse("150"))),
                ),
            ),
            tag(PriceTagInput(Money.Text("twelve", "usd"), Decimal.parse("150"))),
        )
    }

    @Test
    fun `a sign refinement is checked once the money is valid`() {
        assertEquals(
            Either.Left(nonEmptyListOf(PriceTag.Error.NotPositive(PositiveMoney.Error.NonPositive(dollars("0.00"))))),
            tag(PriceTagInput(Money.Text("0.00", "USD"), null)),
        )
        assertEquals(dollars("12.99"), tag(PriceTagInput(Money.Text("12.99", "USD"), null)).getOrNull()?.price?.money)
    }

    @Test
    fun `arithmetic rounds only where you say so`() {
        val total: Either<Money.ArithmeticError, Money> =
            either {
                lineTotal(dollars("12.99"), PositiveInt.fromTrusted(3), Percentage.fromTrusted(Decimal.parse("10")))
            }
        assertEquals(Either.Right(dollars("35.07")), total)
    }

    @Test
    fun `currencies never mix`() {
        val euros = Money.fromTrusted(Money.Input(Decimal.parse("1.00"), "EUR"))
        val mixed: Either<Money.ArithmeticError, Money> = either { dollars("1.00").add(euros) }
        assertEquals(Either.Left(Money.ArithmeticError.CurrencyMismatch(expected = usd, actual = euros.currency)), mixed)
    }

    @Test
    fun `splitting never loses a cent`() {
        val shares = dollars("10.00").split(PositiveInt.fromTrusted(3))
        assertEquals(listOf(dollars("3.34"), dollars("3.33"), dollars("3.33")), shares.toList())
    }
}
