# Step 7: Decimals and money

**You will learn** how to work with exact decimals and percentages, how `Money` keeps
amounts exact in a currency's minor units, how to require a positive price, and how
to do arithmetic that rounds only where you say so.

Imports for this step: `numeric.Decimal`, `numeric.Percentage`, `numeric.PositiveInt`,
`numeric.RoundingMode`, `money.Currency`, `money.Money`, `money.PositiveMoney`, and
from the root package `field`, `flatField`, `optional`, `validate`, `fromTrusted` and
`ValueFactory`.

## Exact decimals

Prices never go through `Double`. The library's own `Decimal` behaves the same on
every platform and mirrors `java.math.BigDecimal`: `Decimal.parse("12.30")` keeps its
scale of 2, `equals` includes the scale, and `compareTo` compares the number. So
`12.30` and `12.3` are not equal but compare as the same amount.

`Percentage` is a `Decimal` from 0 to 100: `Percentage.from(Decimal.parse("12.5"))`
means 12.5%, and its `ratio` is exactly `0.125`.

## Money is counted in minor units

`Money` stores a whole number of the currency's minor units (cents for USD, yen for
JPY) plus the `Currency`. An amount finer than a cent can't exist, so construction
never rounds:

```kotlin
val usd: Currency = Currency.fromTrusted("USD")

fun dollars(amount: String): Money = Money.fromTrusted(Money.Input(Decimal.parse(amount), "USD"))

context(raise: Raise<Money.Error>)
fun price(amount: String, currency: Currency): Money = Money.of(Decimal.parse(amount), currency)
```

`price("12.99", usd)` is 1299 cents. `price("12.995", usd)` raises
`NotInMinorUnits(12.995, 2)`: deciding how to round is your job, not the parser's.
(`dollars` is a helper for literals in the tests; `Decimal.parse` throws on text that
isn't a number, so use it only on literals too.)

## A price tag: parse, refine, accumulate

Prices arrive as text. `Money.parser` takes a `Money.Text(amount, currency)` and
checks the amount and the currency *independently*, so it can raise two errors at once:
its error type is `NonEmptyList<Money.Error>`. Inside `validate`, `flatField` merges
such a nested batch into the parent's list, one parent error per nested error. (Plain
`field` would keep the batch together as one parent error; choose per field.)

A price must also be greater than zero, which `PositiveMoney` guarantees. It wraps an
already-built `Money`, so it goes in a dependent block:

```kotlin
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
```

`PriceTagInput(Money.Text("twelve", "usd"), Decimal.parse("150"))` reports three
errors: `InvalidPrice(InvalidAmount("twelve"))`,
`InvalidPrice(InvalidCurrency(UnsupportedCode("usd")))` (codes are case-sensitive) and
`InvalidDiscount(OutOfRange(150))`. A price of `0.00` is valid money but gives
`NotPositive`.

There are three sign refinements: `PositiveMoney` (`> 0`, for amounts to charge),
`NonNegativeMoney` (`>= 0`, for prices and totals where zero is fine) and
`NegativeMoney` (`< 0`, for refunds).

## Arithmetic that tells you what can go wrong

Money arithmetic raises `Money.ArithmeticError` instead of silently doing something
surprising: adding euros to dollars raises `CurrencyMismatch`, and overflowing a `Long`
raises `Overflow` instead of wrapping around. Anything that could produce a fraction
of a cent takes an explicit `RoundingMode`:

```kotlin
context(raise: Raise<Money.ArithmeticError>)
fun lineTotal(unitPrice: Money, copies: PositiveInt, discount: Percentage): Money {
    val gross = unitPrice.multiply(copies)
    return gross.subtract(gross.multiply(discount, RoundingMode.HALF_EVEN))
}
```

Three copies at 12.99 with 10% off: the gross is 38.97, the discount 3.897 rounds
(half-even) to 3.90, and the total is 35.07. Multiplying by a `PositiveInt` is exact,
so it needs no rounding mode.

Some operations can't fail and need no context. `split` and `allocate` divide an
amount into parts that always add up to the original: splitting 10.00 three ways with
`split(PositiveInt.fromTrusted(3))` gives `3.34, 3.33, 3.33`, with no cent lost.

## What you have now

Prices that are exact, positive and in a real currency; optional discounts that are
real percentages; and arithmetic whose every failure and rounding decision is in its
signature.

The code and its tests are in
[Step07MoneyTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/Step07MoneyTest.kt).

Next: [Step 8, identifiers and time](08-ids-and-time.md).
