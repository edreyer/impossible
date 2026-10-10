# safe-domain

Small, immutable Kotlin value types for common business invariants, with
precise validation failures expressed through Arrow's `Raise` DSL.

A shared type establishes a narrow guarantee. A consuming application's
`CustomerName` or `OrderQuantity` can build on that guarantee while retaining
its own meaning and policies.

## Types

Types are grouped by domain under `io.liquidsoftware.safedomain`. The root
package holds the construction contract shared by every type.

| Package | Contents |
| --- | --- |
| `io.liquidsoftware.safedomain` | `ValueFactory` with `optional()`, `required()` and `fromTrusted`, and the `validate { }` DSL (`field`, `flatField`, `eachField`, `Validated`) |
| `.text` | `NonBlankString`, `Slug` |
| `.contact` | `Email`, `Phone` |
| `.numeric` | `PositiveInt`, `PositiveLong`, `BoundedInt`, `PositiveDecimal`, `NonNegativeDecimal`, `Percentage`, `Decimal` |
| `.money` | `Currency`, `Money` |
| `.time` | `DateChecks`, `DateRange`, `EffectivePeriod`, `TimeWindow`, `PositiveDuration` |
| `.locale` | `CountryCode`, `LanguageTag` |
| `.identity` | `TypedId` |
| `.web` | `HttpUrl` |
| `.collections` | `NonEmptyList.factory()`, `NonEmptySet.factory()`, `NonEmptySet.distinctFactory()`, `UniqueList`, `BoundedList` |

| Type | Invariant | Validation error |
| --- | --- | --- |
| `NonBlankString` | At least one character is not Kotlin whitespace; optional maximum length | `NonBlankString.Error` (`Blank`, `TooLong`) |
| `Slug` | Lowercase ASCII letters and digits in hyphen-joined groups | `Slug.Error.InvalidFormat(value)` |
| `PositiveInt` | `1..Int.MAX_VALUE` | `PositiveInt.Error.NonPositive(value)` |
| `PositiveLong` | `1..Long.MAX_VALUE` | `PositiveLong.Error.NonPositive(value)` |
| `BoundedInt` | `Int` within the factory's inclusive range | `BoundedInt.Error.OutOfRange(value, bounds)` |
| `PositiveDecimal` | Exact decimal `> 0` | `PositiveDecimal.Error.NonPositive(value)` |
| `NonNegativeDecimal` | Exact decimal `>= 0` | `NonNegativeDecimal.Error.Negative(value)` |
| `Percentage` | Exact whole percentage units in `0..100` | `Percentage.Error.OutOfRange(value)` |
| `TypedId<Tag>` | UUID with a compile-time domain tag | `TypedId.Error.InvalidFormat(value)` |
| `HttpUrl` | Absolute HTTP/HTTPS URI with a server host and a port in `1..65535` | `HttpUrl.Error` |
| `DateChecks.past()`, `future()` | `LocalDate` strictly before or after today from a context `Clock` | `DateChecks.Error` (`NotPast`, `NotFuture`) |
| `DateRange` | Inclusive endpoints with `start <= endInclusive` | `DateRange.Error.EndBeforeStart(...)` |
| `EffectivePeriod` | Open-ended from a date, or a bounded `DateRange` | none: built from valid parts |
| `TimeWindow` | Half-open instants with `start < endExclusive` | `TimeWindow.Error.NotAfterStart(...)` |
| `PositiveDuration` | Finite `Duration` `> 0` | `PositiveDuration.Error` (`NonPositive`, `Infinite`) |
| `Currency` | ISO 4217 code from the bundled code list | `Currency.Error.UnsupportedCode(value)` |
| `Money` | Exact signed decimal amount and validated currency | `Money.Error` |
| `CountryCode` | Assigned ISO 3166-1 alpha-2 code from the bundled list | `CountryCode.Error.UnsupportedCode(value)` |
| `LanguageTag` | Canonical-case BCP 47 language, script, region and variants | `LanguageTag.Error.InvalidFormat(value)` |
| `Email` | ASCII dot-atom mailbox and DNS-label domain | `Email.Error.InvalidFormat(value)` |
| `Phone` | International E.164-shaped text | `Phone.Error.InvalidFormat(value)` |
| `NonEmptyList.factory<A>()` | Arrow `NonEmptyList<A>` from any iterable | `NonEmptyError.Empty` |
| `NonEmptySet.factory<A>()` | Arrow `NonEmptySet<A>`; repeats collapse | `NonEmptyError.Empty` |
| `NonEmptySet.distinctFactory<A>()` | Arrow `NonEmptySet<A>`; repeats are errors | `NonEmptyList<DistinctError>` |
| `UniqueList<A>` | Ordered elements with no equal duplicates; empty allowed | `NonEmptyList<UniqueList.Error>` |
| `BoundedList<A>` | List size within retained inclusive bounds; duplicates allowed | `BoundedList.Error` |

Simple wrappers are value classes with private constructors and read-only `value` properties,
and a validated `from(...)` factory. Equality and hashing use the exact
underlying value within the same type unless a type documents otherwise.
Construction returns the success type directly and requires a named `Raise`
context for that type's closed error model.

`NonBlankString` uses Kotlin's `isBlank` definition. Empty strings, tabs,
line breaks, and whitespace such as nonbreaking spaces are rejected. Accepted
input is preserved exactly: no trimming, case folding, or Unicode normalization.
Nonblank does not imply visible or safe-to-render text; for example, a zero-width
space (`U+200B`) is accepted because Kotlin does not classify it as whitespace.

`NonBlankString.bounded(maxLength)` returns a factory that also limits length,
counted in Unicode code points (the unit SQL character columns use), and raises
`TooLong(maximum, actual)`. `bounded(maxLength, trim = true)` trims leading and
trailing whitespace before both checks and stores the trimmed text; trimming
happens only when asked for. `Slug` accepts identifiers such as
`summer-sale-2026` and rejects anything else, including uppercase, without
repairing it.

## Usage

Use `either` when a result needs to exist as a value:

```kotlin
import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString

val name: Either<NonBlankString.Error, NonBlankString> =
    either { NonBlankString.from("  Alice  ") } // Preserves surrounding spaces.

val quantity: Either<PositiveInt.Error, PositiveInt> =
    either { PositiveInt.from(3) }

val invalid: Either<PositiveInt.Error, PositiveInt> =
    either { PositiveInt.from(0) } // Left(Error.NonPositive(0)).
```

Functions that compose validation can return the success type directly. At a
boundary, consume the result explicitly and translate failures into the
application's own outcomes:

```kotlin
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.numeric.PositiveInt

context(raise: Raise<PositiveInt.Error>)
fun quantity(raw: Int): PositiveInt = PositiveInt.from(raw)

sealed interface QuantityOutcome {
    data class Accepted(val quantity: PositiveInt) : QuantityOutcome
    data object MustBePositive : QuantityOutcome
}

fun acceptQuantity(raw: Int): QuantityOutcome {
    val value = either { quantity(raw) }.getOrElse { error ->
        return when (error) {
            is PositiveInt.Error.NonPositive -> QuantityOutcome.MustBePositive
        }
    }
    return QuantityOutcome.Accepted(value)
}
```

The types deliberately do not share a universal error hierarchy. When an
application combines different types, it should map their failures into its
own meaningful error or outcome model.

## Composing types with mapped factories

Validated factories follow `ValueFactory<Input, Error, Value>`. Ordinary type
companions implement it; `TypedId.factory<Tag>()` supplies a tag-specific factory.
The mapped
`from` overload reduces a repeated `raise.withError(...) { ... }` block to:

```kotlin
import arrow.core.raise.Raise
import io.liquidsoftware.safedomain.numeric.PositiveInt

sealed interface OrderError {
    data class InvalidQuantity(val cause: PositiveInt.Error) : OrderError
}

context(raise: Raise<OrderError>)
fun orderQuantity(raw: Int): PositiveInt =
    PositiveInt.from(raw, onError = OrderError::InvalidQuantity)
```

A consuming project's composite types can implement the same small contract
and inherit the mapped overload. Each layer owns its error model:

```kotlin
import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString

data class OrderInput(val customer: String, val quantity: Int)

class Order private constructor(
    val customer: NonBlankString,
    val quantity: PositiveInt,
) {
    sealed interface Error {
        data class InvalidCustomer(val cause: NonBlankString.Error) : Error
        data class InvalidQuantity(val cause: PositiveInt.Error) : Error
    }

    companion object : ValueFactory<OrderInput, Error, Order> {
        context(raise: Raise<Error>)
        override fun from(value: OrderInput): Order = Order(
            customer = NonBlankString.from(value.customer, onError = Error::InvalidCustomer),
            quantity = PositiveInt.from(value.quantity, onError = Error::InvalidQuantity),
        )
    }
}

sealed interface CheckoutError {
    data class InvalidOrder(val cause: Order.Error) : CheckoutError
}

val checkoutOrder: Either<CheckoutError, Order> = either {
    Order.from(OrderInput("Alice", 0), onError = CheckoutError::InvalidOrder)
}
```

Here, checkout receives `InvalidOrder` containing the precise quantity failure.
The wrapper constructors map the entire lower-level error type without
enumerating its branches. Additional failures within that lower-level error
model do not require these mappings to change.

The mapped overload delegates to Arrow's
[`withError`](https://apidocs.arrow-kt.io/arrow-core/arrow.core.raise/with-error.html).
It preserves the factory's validation strategy and invokes `onError` only for a
logical failure. The `Order` example stops at its first failure; `Money.parser`
accumulates its failures with `validate`, and the mapped overload wraps its
complete nonempty error batch in one call. Unexpected exceptions from a factory
or mapper propagate unchanged. Use `validate` (below) for independent
validations that should all run.
Exception-based integrations can explicitly map a materialized result using
`getOrElse { error -> throw ... }` at the framework boundary.

`ValueFactory` is optional for consuming domain types; ordinary named `Raise`
functions and `withError` remain available. No experimental callable-reference
features or additional dependencies are required.

## Accumulating composites with `validate`

When every field of a composite should be checked and all failures reported
together, use `validate { }`. Each field is declared by name with `by`; the
constructor call at the end reads the validated values. All declared fields run,
and their failures are raised together in declaration order:

```kotlin
import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.validate

data class CustomerInput(val name: String, val email: String, val credit: Money.Text)

class Customer private constructor(val name: NonBlankString, val email: Email, val credit: Money) {
    sealed interface Error {
        data class InvalidName(val cause: NonBlankString.Error) : Error
        data class InvalidEmail(val cause: Email.Error) : Error
        data class InvalidCredit(val causes: NonEmptyList<Money.Error>) : Error
    }

    companion object : ValueFactory<CustomerInput, NonEmptyList<Error>, Customer> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: CustomerInput): Customer = validate {
            val name by NonBlankString.field(value.name, Error::InvalidName)
            val email by Email.field(value.email, Error::InvalidEmail)
            val credit by Money.parser.field(value.credit, Error::InvalidCredit)
            Customer(name, email, credit)
        }
    }
}
```

| Declaration | Result |
| --- | --- |
| `Factory.field(raw, Error::X)` | One field. A failure, including a nested factory's whole error batch, is mapped once with `onError`. |
| `Factory.flatField(raw, Error::X)` | One field from an accumulating factory such as `Money.parser`. Each nested error is mapped and added to the parent list individually. |
| `Factory.eachField(raws) { index, error -> ... }` | Every element of an iterable. Each failure is reported with its zero-based index. |
| `field { ... }` | A field computed by a block, for steps that depend on other fields or checks across fields. A failure stops only this block. |

Choose `field` or `flatField` per nested factory: `field` keeps a nested batch
together as one parent error, while `flatField` merges it into the parent's list.

Reading a field's value is only safe once its validation has succeeded. Reading
a failed field raises every error collected so far and ends the block, so read
fields in the final constructor call, or inside a `field { }` block that depends
on them. Inside such a block, ordinary `ensure` expresses a relationship between
fields; it runs only when its inputs are valid:

```kotlin
val limits by field {
    val minimum by PositiveInt.field(value.minimum, Error::InvalidMinimum)
    val maximum by PositiveInt.field(value.maximum, Error::InvalidMaximum)
    ensure(minimum <= maximum) { Error.MaximumBelowMinimum }
    minimum to maximum
}
```

`validate` requires `Raise<NonEmptyList<Error>>`, so composites built with it
expose a `NonEmptyList<Error>` factory, as `Money.parser` does. Use the mapped
`from(raw, onError = ...)` overload directly for composites that should stop at
their first failure. `validate` has no field-count limit.

`validate` wraps Arrow's `accumulate` API, which Arrow 2.2.3 marks
`@ExperimentalRaiseAccumulateApi`. The opt-in is confined to
[Validation.kt](src/commonMain/kotlin/io/liquidsoftware/safedomain/Validation.kt);
callers need none. `Validated<A>` is an alias for Arrow's
`RaiseAccumulate.Value<A>`.
[ValidationTest](src/commonTest/kotlin/io/liquidsoftware/safedomain/ValidationTest.kt)
covers ordering, flattening, indexing, dependent blocks, and early reads.

## Optional and required fields

Factories take non-null input, so a nullable value does not compile where a
factory expects one. Optionality is opted into explicitly on the factory:

```kotlin
val email by Email.required().field(value.email, { Error.MissingEmail }, Error::InvalidEmail)
val middleName by NonBlankString.optional().field(value.middleName, Error::InvalidMiddleName)
```

- `optional()` turns `ValueFactory<I, E, V>` into `ValueFactory<I?, E, V?>`. Null
  input becomes a null value; anything else is validated in full, so `""` is still
  `NonBlankString.Error.Blank`, never treated as absent. The result is `V?`, so the
  composite's property must be declared nullable and callers keep Kotlin's null checks.
- `required()` turns `ValueFactory<I, E, V>` into `ValueFactory<I?, RequiredError<E>, V>`.
  Null raises `RequiredError.Missing` and invalid input raises `RequiredError.Invalid(cause)`,
  so nullable boundary data (a DTO field, a form value) becomes a non-null domain value
  without `!!` or a silent default. The three-argument `field(value, onMissing, onError)`
  maps both cases.

Both are ordinary factories, so they also work with `from`, `eachField` and the mapped
overload. Use a nullable field only when absence means nothing more than "not given".
When absence carries meaning (opted out, not yet scheduled), model it as a sealed type
such as `EffectivePeriod`. When at least one of several values must be present, use a
sealed type or Arrow's `Ior` instead of several nullable fields.

`fromTrusted(value)` constructs from input the caller already knows is valid, such as a
literal or a constant, and throws `IllegalArgumentException` if it is not. It is for
programmer-supplied values only, never untrusted input. Common constants are provided:
`PositiveInt.ONE`, `PositiveLong.ONE`, and `NonNegativeDecimal.ZERO`.

## Decimals and percentages

Exact decimals use the library's own `Decimal`, which works the same on every
platform. It stores an arbitrary-precision unscaled integer and an `Int` scale,
and mirrors `java.math.BigDecimal`: `Decimal.parse("12.30")` keeps scale 2,
`equals` includes scale while `compareTo` is numeric, and `toString` matches
`BigDecimal.toString()`. `+`, `-`, `*` and unary minus are exact; `divide` and
`setScale` take an explicit `RoundingMode` with the JDK's eight modes. Operations
throw `ArithmeticException` when the scale leaves the `Int` range, when
`UNNECESSARY` would have to round, or when aligning scales would add more than
`Decimal.MAX_EXPANSION_DIGITS` (10,000) digits. A JVM test checks it against
`BigDecimal`. The arbitrary-precision integer comes from
[bignum](https://github.com/ionspin/kotlin-multiplatform-bignum), an internal
dependency that is not part of the public API.

`PositiveDecimal.from(Decimal.parse(...))` and `NonNegativeDecimal.from(Decimal.parse(...))`
enforce their sign invariants. `Percentage.from(Decimal.parse("12.5"))` means **12.5%**
and accepts the inclusive range `0..100`. It is suitable for bounded proportions,
discounts, and progress; growth rates above 100% require a different domain type.
These factories accept already parsed `Decimal` values, like the integer
factories accept already parsed `Int` values.

All three types preserve decimal scale without rounding. Equality, hashing, and
ordering are numeric within each type, so `1.0` equals `1.00`. Their constructors
are private, and they expose no public copy operation that bypasses validation.
They supply validated construction and ordering, plus the operations whose result
always keeps the invariant: `PositiveDecimal + PositiveDecimal`,
`NonNegativeDecimal + NonNegativeDecimal`, and `PositiveDecimal.toNonNegativeDecimal()`.
`Percentage.ratio` is the exact ratio (12.5% is `0.125`), and `Money` accepts a
`Percentage` directly. Other arithmetic on extracted values has ordinary `Decimal` behavior.

`PositiveInt` and `PositiveLong` add with a typed `Overflow` failure, compare, and widen
with `toPositiveLong()` and `toPositiveDecimal()`. There is deliberately no
`NonNegativeInt`: use `PositiveInt` for counts that cannot be zero, and
`BoundedInt.factory(0..n)` or a consuming-domain type where zero is valid.
`BoundedInt.factory(1..5)` validates a range such as a rating; bounds are programmer
configuration, so an empty range throws.

```kotlin
import arrow.core.raise.either
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.NonNegativeDecimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveDecimal

val quantity = either { PositiveDecimal.from(Decimal.parse("2.500")) }
val balance = either { NonNegativeDecimal.from(Decimal.parse("0.00")) }
val discount = either { Percentage.from(Decimal.parse("12.50")) }
```

## Typed identifiers

`TypedId<Tag>` is a value class over `kotlin.uuid.Uuid` with a phantom `Tag` type.
Define a distinct marker class or interface for each identifier kind. Kotlin callers
cannot pass a `TypedId<OrderTag>` where a `TypedId<CustomerTag>` is required. The tag
exists only at compile time: equality and hashing use the UUID, so ids with different
tags and the same UUID are equal if compared as `Any`. That keeps the type free at
runtime and simple to store and serialize.

`TypedId.factory<Tag>()` returns a reusable `ValueFactory` with the usual
`from(raw, onError = ...)` overload. Input must have the full `8-4-4-4-12` UUID
hexadecimal shape. Uppercase hexadecimal is accepted; shortened forms such as
`1-1-1-1-1`, braces, and whitespace are rejected. The stored `Uuid` renders
canonical lowercase. `TypedId.of<Tag>(uuid)` tags an already parsed
`kotlin.uuid.Uuid` (code holding a `java.util.UUID` can convert it with
`toKotlinUuid()`), and `TypedId.random<Tag>()` creates a new version 4 id.
Nil UUIDs and all versions are accepted; entity existence and additional ID
policies belong to the consuming domain.

```kotlin
import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.identity.TypedId

interface CustomerTag
interface OrderTag

val customerIds = TypedId.factory<CustomerTag>()
val customer: Either<TypedId.Error, TypedId<CustomerTag>> = either {
    customerIds.from("550e8400-e29b-41d4-a716-446655440000")
}

sealed interface AccountError {
    data class InvalidCustomerId(val cause: TypedId.Error) : AccountError
}

context(raise: Raise<AccountError>)
fun customerId(raw: String): TypedId<CustomerTag> =
    customerIds.from(raw, onError = AccountError::InvalidCustomerId)
```

## HTTP URLs

`HttpUrl.from(raw)` accepts absolute HTTP/HTTPS addresses with a server authority.
The parser is written in common Kotlin and follows RFC 3986 with the rules of
the JDK's `java.net.URI`, so the same input is accepted or rejected identically
on every platform; a JVM test checks it against `java.net.URI` on a corpus of
valid and invalid addresses. Hosts may be ASCII DNS names (including single-label
and encoded IDN names), IPv4 addresses, or bracketed IPv6 addresses. Ports are
optional; supplied numeric ports must be in `1..65535`. User information,
queries, fragments, and legal Unicode path characters are accepted. Unicode host
names must be encoded before construction.

Input spelling is preserved without trimming, escaping, or path normalization:
`value` is the original text, and `scheme`, `userInfo`, `host`, `port`, `path`,
`query`, and `fragment` expose the parsed components as written. Equality and
hashing follow `java.net.URI`: scheme/host and percent-escape hex case are
ignored, path case is significant, and omitted default ports and redundant path
segments are not normalized. No DNS lookup or network request occurs. A valid
value establishes address syntax; reachability, trusted hosts, credentials
policy, and outbound request authorization remain application concerns.

Failures distinguish `InvalidSyntax`, `NotAbsolute`, `UnsupportedScheme`,
`MissingHost`, and `InvalidPort`; each carries the rejected input or component, and
the mapped factory adapts this closed model to the consuming domain.

```kotlin
import arrow.core.raise.either
import io.liquidsoftware.safedomain.web.HttpUrl

val endpoint = either { HttpUrl.from("https://example.com:8443/offers?active=true") }
```

## Dates and ranges

"In the past" and "in the future" are relationships to a moment, not properties of
a date, so there are no past or future date types. `DateChecks.past()` and
`DateChecks.future()` are factories that read today from a `kotlin.time.Clock` in a
`TimeZone`, both supplied as context parameters, and return the `LocalDate`
unchanged. A composite stores a plain `LocalDate`; the check held at construction.
Today is excluded by both strict comparisons. Supply a fixed clock in tests.

```kotlin
context(clock: Clock, timeZone: TimeZone, raise: Raise<NonEmptyList<Error>>)
fun from(value: TripInput): Trip = validate {
    val born by DateChecks.past().field(value.born, Error::InvalidBirthDate)
    val departs by DateChecks.future().field(value.departs, Error::InvalidDeparture)
    Trip(born, departs)
}
```

`DateRange` includes both endpoints and accepts a single-day range. It supplies
`date in range`, `range.overlaps(other)` (sharing an endpoint is an overlap),
`days` (counting both endpoints), and `dates()`, a sequence of every date in order.
`EffectivePeriod` is either `OpenEnded(start)` or `Bounded(range)`, so "no end date"
is an explicit state rather than a nullable end.

`TimeWindow` is a half-open interval of `kotlin.time.Instant`s, `start` inclusive and
`endExclusive` exclusive, and is never empty. Adjacent windows do not overlap, so
bookings ending and starting at 10:00 do not collide. It supplies `contains`,
`overlaps`, `duration`, and `TimeWindow.starting(start, duration)`.
`PositiveDuration` is a finite `Duration` greater than zero, for timeouts and slot
lengths.

Inputs are already parsed `LocalDate`, `Instant` and `Duration` values, so these
factories report domain relationships rather than parse text. Composite types have
private constructors and no public copy method that could bypass their invariants.

```kotlin
import arrow.core.raise.either
import io.liquidsoftware.safedomain.time.DateRange
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

val start = LocalDate(2026, 10, 5)
val period = either { DateRange.from(DateRange.Input(start, start.plus(DatePeriod(days = 7)))) }
```

## Money and currencies

`Currency.from("USD")` accepts ISO 4217 alphabetic codes from the library's
bundled list, including historic and fund codes such as `XDR`. The list is the
same on every platform, so validation never depends on the runtime's currency
data. Codes are case-sensitive and are never trimmed or repaired. This is not a
guarantee of current legal tender. `Currency.minorUnits` gives the decimal places of
the minor unit (2 for USD, 0 for JPY, 3 for KWD) and is null for codes without one,
such as `XAU` and `XXX`.

`Money.from(Money.Input(amount, currency = "EUR"))` takes an already parsed
`Decimal` and validates only the currency code, raising `Money.Error.InvalidCurrency`.
`Money.parser.from(Money.Text(amount = "12.3400", currency = "EUR"))` parses decimal
text and validates the currency independently using `validate`, raising a
`NonEmptyList<Money.Error>`. Decimal text follows `Decimal.parse`, which is the JDK's
`BigDecimal` syntax with ASCII digits, including scientific notation; whitespace,
grouping separators, NaN, and infinity are rejected. Already typed components use
`Money.create(amount: Decimal, currency: Currency)` without redundant validation.

Amounts can be positive, zero, or negative and can retain sub-minor-unit precision
for intermediate calculations. Input scale is preserved, while equality and
hashing use numeric amount and currency: USD `1.0` equals USD `1.00`, but EUR
`1.00` is different. No floating-point conversion or implicit rounding occurs.
This deliberately differs from scale-sensitive `Decimal.equals`.

| Operation | Behavior |
| --- | --- |
| `add`, `subtract`, `compare`, `Money.sum(values)` | Require one currency |
| `multiply(Decimal)`, `multiply(PositiveInt)`, `multiply(Percentage)` | Exact: unit price times quantity, or a percentage share |
| `divide(divisor, scale, mode)`, `round(scale, mode)` | Explicit scale and `RoundingMode` |
| `roundToMinorUnits(mode)` | Rounds to the currency's minor units |
| `allocate(ratios)`, `split(parts)` | Splits into parts that always sum exactly to the original, in minor units |
| unary minus | Exact |

`allocate` gives each part its proportional share truncated to minor units, then
hands the remaining units out one at a time to the first parts in order: USD `10.00`
split three ways is `3.34, 3.33, 3.33`. The amount must already be in minor units.

```kotlin
import arrow.core.raise.Raise
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.RoundingMode

context(raise: Raise<Money.ArithmeticError>)
fun lineTotal(price: Money, quantity: PositiveInt, discount: Percentage): Money {
    val gross = price.multiply(quantity)
    return gross.subtract(gross.multiply(discount)).roundToMinorUnits(RoundingMode.HALF_EVEN)
}
```

Arithmetic raises `Money.ArithmeticError.CurrencyMismatch`, `DivisionByZero`,
`NoMinorUnits`, `NotInMinorUnits`, or `UnrepresentableResult` (decimal scale
overflow, rounding needed when `UNNECESSARY` was requested, or a result beyond
`Decimal`'s expansion limit). A consuming application can map these with
`withError`, separately from input failures. Currency conversion, cash rounding,
and formatting belong to the consuming domain.

## Email and phone syntax

`Email` accepts ASCII dot-atom local parts (including plus tags and apostrophes)
and DNS-label domains, including single-label domains and already encoded ASCII
IDN labels. Limits are 64 characters for the local part, 63 per domain label, and
254 for the address. Quoted local parts, IP address literals, and Unicode text
are outside this first-pass contract. This is a documented subset of mailbox
syntax, not complete [SMTP address support](https://www.rfc-editor.org/rfc/rfc5321.html).

`Phone` accepts `+` followed by 1–15 ASCII digits with a nonzero first digit. It
rejects spacing, punctuation, extensions, and national formats. It checks the
shape of an [E.164 number](https://www.itu.int/rec/T-REC-E.164/en), not country
code allocation or numbering plans; even `+1` satisfies this narrow syntax rule.

Both types preserve input exactly. `Phone` uses exact string equality. `Email`
equality and hashing ignore ASCII case in the domain, which DNS treats
case-insensitively, and keep the local part case-sensitive as RFC 5321 permits:
`User@Example.com` equals `User@example.com` but not `user@example.com`.
`localPart` and `domain` expose the two parts as written. Their `InvalidFormat`
errors keep the rejected input for callers but redact it from `toString`, so it does
not reach logs. They establish syntax, not contact reachability or ownership. Any normalization,
DNS lookup, delivery check, or verification workflow is an explicit application
operation.

## Countries and languages

`CountryCode.from("DE")` accepts the 249 officially assigned ISO 3166-1 alpha-2 codes
from a bundled list, so validation is the same on every platform. Lowercase codes and
reserved or user-assigned codes such as `EU`, `XK` and `ZZ` are rejected.

`LanguageTag.from("zh-Hant-TW")` accepts the common subset of BCP 47: a 2-3 letter
language, an optional script, an optional 2-letter or 3-digit region, and variants.
Case must be canonical (`en-US`, not `EN-us`), so equality is plain text equality.
Extensions, private use and grandfathered tags are rejected. It checks syntax only,
not that subtags are registered. `language` gives the primary language subtag.

## Collections

`NonEmptyList.factory<A>()` and `NonEmptySet.factory<A>()` (extensions on Arrow's
companions, imported from `io.liquidsoftware.safedomain.collections`) adapt
construction to `ValueFactory` and named `Raise` contexts while returning Arrow's
actual collection types. Empty input raises `NonEmptyError.Empty`. The list
preserves order and duplicates. The set applies ordinary element equality,
collapses duplicates, and retains first-occurrence iteration order.
`NonEmptySet.distinctFactory<A>()` covers "at least one, no duplicates": it reports
`DistinctError.Empty` or every repeat as `DistinctError.Duplicate(firstIndex,
duplicateIndex)` instead of collapsing it. Use `UniqueList` when an empty result is
allowed.

`UniqueList.factory<A>()` accepts an ordered, possibly empty collection. It
reports every repeat in encounter order as `Error.Duplicate(firstIndex,
duplicateIndex)` with zero-based positions. Each repeat refers to the first
equal element. Validation uses element equality and hashing; it does not trim,
normalize, sort, or remove elements. Uniqueness by a business key belongs to an
explicit consuming-domain policy in this first pass.

```kotlin
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import io.liquidsoftware.safedomain.collections.NonEmptyError
import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.collections.factory

val channels: Either<NonEmptyError, NonEmptyList<String>> = either {
    NonEmptyList.factory<String>().from(listOf("email", "sms"))
}

val labels: Either<NonEmptyList<UniqueList.Error>, UniqueList<String>> = either {
    UniqueList.factory<String>().from(listOf("urgent", "urgent", "urgent"))
} // Left containing Duplicate(0, 1) and Duplicate(0, 2).
```

`BoundedList.factory<A>(1..10)` validates input size against an inclusive
`IntRange`. Bounds are programmer configuration, so an empty range or a negative
minimum throws `IllegalArgumentException` when the factory is created. The factory
then raises `TooSmall(minimum, actual)` or `TooLarge(maximum, actual)`. Both endpoints
are inclusive; `0..0` permits exactly an empty list, and equal positive bounds
require an exact size. Elements retain their order and values, including
duplicates.

These factories inherit the same mapped `from` overload as individual values:

```kotlin
import arrow.core.raise.Raise
import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.contact.Email

sealed interface InvitationError {
    data class InvalidRecipients(val cause: BoundedList.Error) : InvitationError
}

private val recipients = BoundedList.factory<Email>(1..10)

context(raise: Raise<InvitationError>)
fun invitationRecipients(raw: List<Email>): BoundedList<Email> =
    recipients.from(raw, onError = InvitationError::InvalidRecipients)
```

`UniqueList` and `BoundedList` have private constructors, implement `Iterable`,
and expose an unmodifiable `value: List<A>` snapshot and `size`. Their equality
uses ordered elements within the same wrapper type. `BoundedList` also includes
its bounds because they determine subsequent operations. Neither has a public
copy operation that bypasses validation.

| Operation | `UniqueList` | `BoundedList` |
| --- | --- | --- |
| `map` | Requires `Raise<NonEmptyList<UniqueList.Error>>`; validates all collisions | Preserves size and bounds without validation |
| `filter` | Preserves uniqueness, including an empty result | Requires `Raise<BoundedList.Error>`; validates minimum size |
| `append` | Requires `Raise<NonEmptyList<UniqueList.Error>>`; rejects duplicates | Requires `Raise<BoundedList.Error>`; validates maximum size |

All these operations return new snapshots. The `value` list is an interop view;
ordinary operations on it return ordinary collections with their own contracts.
The wrappers also implement `Iterable`, so Kotlin's ordinary collection
extensions remain available and return ordinary collections. Use the member
operations within their required `Raise` contexts to retain the wrapper's
guarantees.

All the collection factories copy collection structure into unmodifiable storage;
subsequent changes to an input collection cannot change the constructed value.
Elements themselves are retained, including nullable elements where the
application deliberately models optional data. They are not deeply copied or
frozen. Domain elements should be immutable; `UniqueList` and sets additionally
require stable equality and hashing. The Arrow adapters protect the storage of
their initial result; subsequent Arrow transformations follow
[Arrow's collection contracts](https://arrow-kt.io/learn/collections-functions/non-empty/).

For raw elements, use `eachField` inside `validate` to retain error positions,
then construct the collection in a `field { }` block once its elements are valid.
An empty input can pass element validation and still fail nonempty collection
validation:

```kotlin
val names by field {
    val elements by NonBlankString.eachField(value.recipients, Error::InvalidRecipient)
    NonEmptyList.factory<NonBlankString>().from(elements, onError = Error::InvalidRecipients)
}
```

Independent collection fields are declared alongside it; parent errors can wrap
an entire `UniqueList` duplicate batch without matching each branch.
[CollectionCompositionTest](src/commonTest/kotlin/io/liquidsoftware/safedomain/CollectionCompositionTest.kt)
demonstrates indexed element validation, nonempty lists and sets, uniqueness,
size bounds, and accumulation across all four fields. Relationships that depend
on valid elements run after their prerequisites succeed.

## Current scope

This library supplies validated construction, mapped factories, value semantics,
date range operations, collection invariants, and decimal money arithmetic on every
Kotlin Multiplatform target listed under [Build](#build), plus a few JVM
conversions. It does not yet include serialization adapters. Arithmetic on an extracted
`Int` has ordinary Kotlin semantics; the wrappers' own operations preserve their
invariants and report overflow explicitly. There is no time zone id type yet:
kotlinx-datetime resolves region ids on JavaScript only with an extra time zone
database, so validation would differ by platform.

External data, including model-produced structured output, should pass through
the factories. Future serialization adapters must enforce the same validation.

## Build

The library is Kotlin Multiplatform, built with Kotlin **2.4.20**, a **Java 26**
toolchain for the JVM target, and the **Gradle 9.6.0** wrapper. Targets:

| Target | Tests |
| --- | --- |
| `jvm` | `./gradlew jvmTest` |
| `js` (browser and Node.js) | `./gradlew jsNodeTest`; browser tests are disabled because they would need a local browser |
| `linuxX64` | `./gradlew linuxX64Test` |
| `macosArm64`, `iosArm64`, `iosSimulatorArm64` | `./gradlew macosArm64Test iosSimulatorArm64Test`; Apple targets build only on macOS |

There is no Wasm target, because bignum does not publish one.

Common code lives in `src/commonMain` and `src/commonTest`; it uses no JDK types,
so validation behaves the same on every platform. Dependencies:

- [Arrow Core 2.2.3](https://github.com/arrow-kt/arrow/releases/tag/2.2.3), an API
  dependency because `Raise` appears in the public signatures.
- [kotlinx-datetime 0.8.0](https://github.com/Kotlin/kotlinx-datetime), an API
  dependency for `LocalDate`. `Clock`, `Instant` and `Duration` come from the
  standard library's `kotlin.time`.
- [bignum 0.3.10](https://github.com/ionspin/kotlin-multiplatform-bignum), an
  implementation dependency behind `Decimal`.

JVM code can convert to the JDK types with the adapters in `src/jvmMain`:
`Decimal.toJavaBigDecimal()` and `BigDecimal.toDecimal()`,
`RoundingMode.toJavaRoundingMode()`, `HttpUrl.toJavaUri()`, and
`Currency.toJavaCurrencyOrNull()` (null when the running JDK lacks a bundled
code). kotlinx-datetime supplies `LocalDate.toJavaLocalDate()` and the standard
library supplies `Uuid.toJavaUuid()`. JVM-only tests check `Decimal` against
`java.math.BigDecimal` and `HttpUrl` against `java.net.URI`.

Named context parameters are stable in Kotlin 2.4; no experimental context
compiler flag is needed. See the
[Kotlin context-parameter documentation](https://kotlinlang.org/docs/context-parameters.html)
and [Arrow's typed-error guide](https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/).

```sh
./gradlew allTests   # every target this machine can run
./gradlew build
```

The tests cover invalid values, boundaries, preservation, equality and hashing,
and composition through named `Raise` contexts. Factory tests cover mapping
through multiple domain layers, accumulation, short-circuiting, and exception
propagation. `ValidationTest` covers the `validate` DSL: declaration order,
flattened and indexed nested errors, dependent field blocks, cross-field checks,
and early reads. `OptionalityTest` covers `optional()`, `required()` and
`fromTrusted`. `BusinessValueCompositionTest` demonstrates a composite with
clock-checked dates, a range relationship failure alongside contact failures, and a
nested money error batch. `DecimalAndIdentityCompositionTest` demonstrates
accumulating construction with decimals, percentages, tagged IDs, and HTTP URLs.
Money tests cover exact arithmetic, minor units, allocation, rounding decisions,
immutability, and typed failures. Collection tests cover empty inputs, duplicate positions,
inclusive size bounds, snapshot immutability, invariant-preserving operations,
and accumulating composition. The library is not yet
configured for publication.

Contributor rules are in [AGENTS.md](AGENTS.md).
