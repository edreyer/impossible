# impossible

Make invalid domain states impossible to represent.

Small, immutable Kotlin value types for common business invariants, with
precise validation failures expressed through Arrow's `Raise` DSL.

A shared type establishes a narrow guarantee. A consuming application's
`CustomerName` or `OrderQuantity` can build on that guarantee while retaining
its own meaning and policies.

New here? The [tutorial](docs/tutorial/README.md) builds a small bookshop domain step
by step, from a single value to JSON and property tests.

## Types

Types are grouped by domain under `io.liquidsoftware.safedomain`. The root
package holds the construction contract shared by every type.

| Package | Contents |
| --- | --- |
| `io.liquidsoftware.safedomain` | `ValueFactory` with `optional()`, `required()`, `fromTrusted` and the combinators `map`, `mapError`, `andThen` and `ensure`, and the `validate { }` DSL (`field`, `flatField`, `eachField`, `ValidationScope`, `Validated`) |
| `.text` | `NonBlankString`, `Slug`, `Secret` |
| `.contact` | `Email`, `Phone` |
| `.numeric` | `PositiveInt`, `NegativeInt`, `PositiveLong`, `NegativeLong`, `BoundedInt`, `PositiveDecimal`, `NegativeDecimal`, `NonNegativeDecimal`, `Percentage`, `Decimal` |
| `.money` | `Currency`, `Money`, `PositiveMoney`, `NonNegativeMoney`, `NegativeMoney` |
| `.time` | `DateChecks`, `DateRange`, `EffectivePeriod`, `TimeWindow`, `PositiveDuration`, `TimeZoneId` |
| `.locale` | `CountryCode`, `LanguageTag` |
| `.identity` | `TypedId` |
| `.web` | `HttpUrl`, `DomainName`, `IpAddress` |
| `.geo` | `GeoPoint` |
| `.collections` | `NonEmptyList.factory()`, `NonEmptySet.factory()`, `NonEmptySet.distinctFactory()`, `UniqueList`, `BoundedList` |

| Type | Invariant | Validation error |
| --- | --- | --- |
| `NonBlankString` | At least one character is not Kotlin whitespace; optional maximum length | `NonBlankString.Error` (`Blank`, `TooLong`) |
| `Slug` | Lowercase ASCII letters and digits in hyphen-joined groups | `Slug.Error.InvalidFormat(value)` |
| `Secret` | Non-blank text that `toString` never shows | `Secret.Error.Blank` |
| `PositiveInt` | `1..Int.MAX_VALUE` | `PositiveInt.Error.NonPositive(value)` |
| `NegativeInt` | `Int.MIN_VALUE..-1` | `NegativeInt.Error.NonNegative(value)` |
| `PositiveLong` | `1..Long.MAX_VALUE` | `PositiveLong.Error.NonPositive(value)` |
| `NegativeLong` | `Long.MIN_VALUE..-1` | `NegativeLong.Error.NonNegative(value)` |
| `BoundedInt` | `Int` within the factory's inclusive range | `BoundedInt.Error.OutOfRange(value, bounds)` |
| `PositiveDecimal` | Exact decimal `> 0` | `PositiveDecimal.Error.NonPositive(value)` |
| `NegativeDecimal` | Exact decimal `< 0` | `NegativeDecimal.Error.NonNegative(value)` |
| `NonNegativeDecimal` | Exact decimal `>= 0` | `NonNegativeDecimal.Error.Negative(value)` |
| `Percentage` | Exact whole percentage units in `0..100` | `Percentage.Error.OutOfRange(value)` |
| `TypedId<Tag>` | UUID with a compile-time domain tag | `TypedId.Error.InvalidFormat(value)` |
| `HttpUrl` | Absolute HTTP/HTTPS URI with a server host and a port in `1..65535` | `HttpUrl.Error` |
| `DomainName` | RFC 1123 host name, at most 253 characters, not all-numeric at the end | `DomainName.Error.InvalidFormat(value)` |
| `IpAddress` (`V4`, `V6`) | Strict dotted-decimal IPv4 or RFC 4291 IPv6 text | `IpAddress.Error.InvalidFormat` (redacted) |
| `GeoPoint` | Latitude in `-90..90`, longitude in `-180..180`, finite | `GeoPoint.Error` (`LatitudeOutOfRange`, `LongitudeOutOfRange`), accumulated |
| `DateChecks.past()`, `future()` | `LocalDate` strictly before or after today from a context `Clock` | `DateChecks.Error` (`NotPast`, `NotFuture`) |
| `DateRange` | Inclusive endpoints with `start <= endInclusive` | `DateRange.Error.EndBeforeStart(...)` |
| `EffectivePeriod` | Open-ended from a date, or a bounded `DateRange` | none: built from valid parts |
| `TimeWindow` | Half-open instants with `start < endExclusive` | `TimeWindow.Error.NotAfterStart(...)` |
| `PositiveDuration` | Finite `Duration` `> 0` | `PositiveDuration.Error` (`NonPositive`, `Infinite`) |
| `TimeZoneId` | IANA region id or `UTC`, resolved by the platform's time zone database | `TimeZoneId.Error.UnknownZone(value)` |
| `Currency` | ISO 4217 code from the bundled code list | `Currency.Error.UnsupportedCode(value)` |
| `Money` | Signed `Long` of a currency's minor units | `Money.Error` |
| `PositiveMoney`, `NonNegativeMoney`, `NegativeMoney` | `Money` `> 0`, `>= 0`, `< 0` | `NonPositive`, `Negative`, `NonNegative` |
| `CountryCode` | Assigned ISO 3166-1 alpha-2 code from the bundled list | `CountryCode.Error.UnsupportedCode(value)` |
| `LanguageTag` | Canonical-case BCP 47 language, script, region and variants | `LanguageTag.Error.InvalidFormat(value)` |
| `Email` | ASCII dot-atom mailbox whose domain is a valid `DomainName` | `Email.Error.InvalidFormat(value)` |
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

Outside a `Raise` scope, such as in a test, a script, a controller or Java code, ask
a factory for an `Either` or a nullable value directly:

```kotlin
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.fromEither
import io.liquidsoftware.safedomain.fromOrElse
import io.liquidsoftware.safedomain.fromOrNull

val parsed = Email.fromEither("ann@example.com") // Right(Email(ann@example.com)).
val maybe = Email.fromOrNull("nope")              // null; use it only when the reason does not matter.
val email = Email.fromOrElse(raw) { return badRequest(it) } // recover may return early.
```

`fromEither(x)` is exactly `either { from(x) }`. Use `either` itself when a block
constructs several values:

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

## Defining your own types with combinators

A consuming domain can define a refined type from existing factories instead of
writing a companion by hand. Each combinator returns a new `ValueFactory`:

| Combinator | Result |
| --- | --- |
| `map { ... }` | wraps each constructed value, typically with a private constructor; the function must not fail |
| `mapError { ... }` | maps every failure into another error model, usually the new type's own |
| `andThen(next)` | refines a constructed value through another factory with the same error model |
| `andThen(next) { ... }` | the same, mapping `next`'s failures into this error model |
| `ensure({ predicate }) { error }` | raises `error` for a constructed value that fails `predicate` |

```kotlin
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.ensure
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.map
import io.liquidsoftware.safedomain.mapError
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import kotlin.jvm.JvmInline

/** A stock-keeping unit: 1 to 12 uppercase ASCII letters, digits and hyphens. */
@JvmInline
value class Sku private constructor(val value: String) {
    sealed interface Error {
        data class InvalidText(val cause: NonBlankString.Error) : Error
        data object UnsupportedCharacter : Error
    }

    // Parentheses let the chain use trailing lambdas inside a class header.
    companion object : ValueFactory<String, Error, Sku> by (
        NonBlankString.bounded(PositiveInt.fromTrusted(12))
            .mapError(Error::InvalidText)
            .ensure({ text -> text.value.all { it in 'A'..'Z' || it in '0'..'9' || it == '-' } }) { Error.UnsupportedCharacter }
            .map { Sku(it.value) }
    )
}
```

`Sku` then works like any library type: `Sku.from(raw)`, `Sku.optional()`,
`Sku.required()`, the mapped `from(raw, onError = ...)` overload and `validate`
fields. The constructor stays private, so the factory chain is the only way in.

Every step runs in order and stops at the first failure: a later function never
sees a value an earlier step rejected, and `map`, `mapError` and `ensure`'s
functions run only for the outcome they handle. `mapError` on an accumulating
factory such as `GeoPoint` receives its whole error batch at once. Unexpected
exceptions propagate unchanged.

The combinators do not widen errors on their own. Because `ValueFactory` is
covariant in its error, Kotlin would infer a shared supertype (often `Any`) for
`ensure` or `andThen` with an unrelated error, so call `mapError` first and let
both steps share the new type's error model, as `Sku` does, or use the `onError`
form of `andThen`.

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

The DSL is built on Arrow's accumulate API internally. That API is experimental in
Arrow, so the library keeps it out of its own API: the block's receiver is the
library's `ValidationScope<Error>` (a `Raise<Error>`, so `ensure` and `raise` work in
it) and fields are the library's `Validated<A>`. No Arrow experimental type appears in
a public signature, callers need no opt-in, and Arrow is an ordinary pinned
dependency in [gradle/libs.versions.toml](gradle/libs.versions.toml).
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

`Decimal` arithmetic is plain arithmetic, not validation, so it follows
`java.math.BigDecimal` and throws `ArithmeticException` on division by zero, on
`UNNECESSARY` rounding that would lose digits, and on scale overflow. `Money`
is different: its operations raise a typed `Money.ArithmeticError`. To make a decimal
operation total inside a `Raise` scope, catch the exception and raise your own error:

```kotlin
val perUnit = Either.catch { total.divide(count, 2, RoundingMode.HALF_EVEN) }
    .mapLeft { PriceError.NotDivisible }.bind()
```

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
`NonNegativeDecimal + NonNegativeDecimal`, `NegativeDecimal + NegativeDecimal`,
`PositiveDecimal.toNonNegativeDecimal()`, and negation between `PositiveDecimal` and
`NegativeDecimal`.
`Percentage.ratio` is the exact ratio (12.5% is `0.125`), and `Money` accepts a
`Percentage` directly. Other arithmetic on extracted values has ordinary `Decimal` behavior.

`PositiveInt` and `PositiveLong` add with a typed `Overflow` failure, compare, and widen
with `toPositiveLong()` and `toPositiveDecimal()`. `NegativeInt` and `NegativeLong`
mirror them (`MINUS_ONE`, `plus`, `toNegativeLong()`, `toNegativeDecimal()`). Negating a
positive integer is total; negating a negative one raises `Overflow` only for
`MIN_VALUE`, which has no positive counterpart. There is deliberately no
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

## Network names and addresses

`DomainName` is a DNS host name as RFC 1123 defines it: ASCII letters, digits, and
inner hyphens in labels of at most 63 characters, 253 in total. Single labels such as
`localhost` are accepted; a final all-numeric label is not, so `1.2.3.4` is never a
domain name. A trailing root dot, empty labels, and Unicode are rejected (encode IDNs
to `xn--` first). `value` keeps the spelling; equality ignores ASCII case.

`IpAddress.from(raw)` parses either family, and `IpAddress.V4.from` or
`IpAddress.V6.from` accept only one. IPv4 parts may not have leading zeros, since
`010` means 8 to some parsers and 10 to others. IPv6 accepts `::` and a trailing
dotted IPv4 part, but no zone id or brackets. Equality uses the address bytes, so
`::1` equals `0:0:0:0:0:0:0:1`, and `toString()` is the canonical RFC 5952 text. An
address can identify a person, so `InvalidFormat` redacts its input in `toString`.

## Secrets and coordinates

`Secret` holds a password, API key, or token. Its `toString` is always
`Secret(<redacted>)`, so it can sit in a data class that gets logged; `reveal()` reads
the content at the point of use. Equality takes time independent of where two secrets
first differ, and the hash code uses only the length.

The redaction rule across the library: an error's `toString` never shows rejected
personal data, such as the text of an invalid email address or phone number. A valid
value prints as written, so `Email` and `Phone` show their addresses and numbers;
they are data the application already stores and displays. Text that must never be
printed, logged, or serialized belongs in `Secret`.

`GeoPoint` is a WGS 84 latitude and longitude in decimal degrees. Both are checked and
reported together, and nothing is wrapped or clamped: `181.0` fails rather than becoming
`-179.0`. Distance and bearing belong to the consuming domain.

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

`TimeZoneId` is an IANA region id such as `Europe/Berlin`, or `UTC`, spelled exactly as
the time zone database does (`europe/berlin` fails). It exposes the resolved
kotlinx-datetime `timeZone`. Fixed offsets such as `+02:00` are rejected; store a
`UtcOffset` for those. Each platform resolves ids with its own database: the JDK's on
the JVM, the operating system's on native targets, and the bundled
`@js-joda/timezone` data on JS and Wasm.

## Money and currencies

`Currency.from("USD")` accepts ISO 4217 alphabetic codes from the library's
bundled list, including historic and fund codes such as `XDR`. The list is the
same on every platform, so validation never depends on the runtime's currency
data. Codes are case-sensitive and are never trimmed or repaired. This is not a
guarantee of current legal tender. `Currency.minorUnits` gives the decimal places of
the minor unit (2 for USD, 0 for JPY, 3 for KWD) and is null for codes without one,
such as `XAU` and `XXX`.

`Money` stores a signed `Long` count of the currency's minor units (`minor`) plus the
`Currency`: USD 12.34 is 1234 cents, JPY 1234 is 1234 yen. An amount finer than the
minor unit cannot exist, and currencies without a minor unit (`XAU`, `XXX`) cannot be
money. `amount` gives the value in major units as a `Decimal` at the currency's
scale, for display and interop. Equality and hashing use `minor` and `currency`.

| Construction | Behavior |
| --- | --- |
| `Money.of(amount: Decimal, currency)` | Requires the amount to be exact in minor units: `12.345` USD raises `NotInMinorUnits`, never rounds |
| `Money.ofMinor(minor: Long, currency)`, `Money.zero(currency)` | From minor units directly |
| `Money.from(Money.Input(amount, currency = "EUR"))` | Validates the currency code, then as `of` |
| `Money.parser.from(Money.Text("12.34", "EUR"))` | Parses text and validates the currency independently, accumulating both, then checks minor units |

Construction raises `Money.Error`: `InvalidAmount`, `InvalidCurrency`, `NoMinorUnits`,
`NotInMinorUnits`, or `OutOfRange` (beyond a `Long` of minor units, about 92
quadrillion USD). Decimal text follows `Decimal.parse`, which is the JDK's
`BigDecimal` syntax with ASCII digits, including scientific notation; whitespace,
grouping separators, NaN, and infinity are rejected.

Keep values finer than a minor unit, such as a per-unit price of USD 0.0035, a tax
rate or an exchange rate, as `Decimal` (or `Percentage`) and round into `Money` once.

| Operation | Behavior |
| --- | --- |
| `add`, `subtract`, `negate`, `Money.sum(values)` | Exact; require one currency; raise `Overflow` instead of wrapping |
| `multiply(PositiveInt)` | Exact unit price times quantity |
| `multiply(Decimal, mode)`, `multiply(Percentage, mode)`, `divide(Decimal, mode)` | Round to minor units with an explicit `RoundingMode` |
| `allocate(ratios)`, `split(parts)` | Total; parts always sum exactly to the original |
| `compare(other)` | Requires one currency; there is no ordering across currencies |

`allocate` gives each part its proportional share truncated toward zero, then hands
the remaining minor units out one at a time to the first parts in order: USD `10.00`
split three ways is `3.34, 3.33, 3.33`.

### Signed money

`Money` itself is signed, so balances and adjustments can go either way. When a field
needs a sign guarantee, wrap it in a refinement and the composite never re-checks it:

| Type | Use for | Operations that keep the sign |
| --- | --- | --- |
| `NonNegativeMoney` (`>= 0`) | Prices, fees, discounts, totals; zero is valid | `add`, `multiply(PositiveInt)`, `multiply(Percentage, mode)`, `allocate`, `split`, `NonNegativeMoney.zero(currency)` |
| `PositiveMoney` (`> 0`) | Amounts that must be charged or paid | `add`, `multiply(PositiveInt)`, `negate()` to `NegativeMoney`, `toNonNegativeMoney()` |
| `NegativeMoney` (`< 0`) | Refunds, credits | `add`, `multiply(PositiveInt)`, `negate()` to `PositiveMoney` |

Each factory takes an already built `Money`, so compose them in a field:
`field { PositiveMoney.from(Money.from(input, Error::InvalidAmount), Error::NotPositive) }`.
Each wrapper exposes the underlying `money` for anything else. Arithmetic still raises
`Money.ArithmeticError` for currency mismatch and overflow; `NegativeMoney.negate()`
raises `Overflow` only for `Long.MIN_VALUE` minor units.

```kotlin
import arrow.core.raise.Raise
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.RoundingMode

context(raise: Raise<Money.ArithmeticError>)
fun lineTotal(price: Money, quantity: PositiveInt, discount: Percentage): Money {
    val gross = price.multiply(quantity)
    return gross.subtract(gross.multiply(discount, RoundingMode.HALF_EVEN))
}
```

Arithmetic raises `Money.ArithmeticError.CurrencyMismatch`, `DivisionByZero`,
`Overflow`, or `RoundingRequired` (when `UNNECESSARY` was requested and the result
needed rounding). A consuming application can map these with `withError`,
separately from input failures. Currency conversion, cash rounding, and formatting
belong to the consuming domain.

## Email and phone syntax

`Email` accepts ASCII dot-atom local parts (including plus tags and apostrophes)
and any domain [`DomainName`](#network-names-and-addresses) accepts, including single-label domains and already
encoded ASCII IDN labels; like `DomainName`, it rejects an all-digit last label, so
`user@123` and `user@127.0.0.1` are invalid. Limits are 64 characters for the local part, 63 per domain label, and
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
`localPart` and `domain` expose the two parts as written, and `domainName` gives the
domain as a `DomainName`. Their `InvalidFormat`
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

## Field paths, messages, and problem details

Inside `validate`, the `...At` helpers record where each failure happened. The path
is the name of the property the field is assigned to, so it costs nothing to keep
in step with the code; pass `name =` when the wire name differs:

```kotlin
data class Shipment(val contact: Email, val address: Address, val quantities: List<PositiveInt>, val price: Money) {
    companion object : ValueFactory<Input, NonEmptyList<FieldError<Error>>, Shipment> {
        context(raise: Raise<NonEmptyList<FieldError<Error>>>)
        override fun from(value: Input): Shipment = validate {
            val contact by Email.required().fieldAt(value.contact, onMissing = { Error.ContactMissing }) { Error.Contact(it) }
            val address by Address.nestedFieldAt(value.address) { Error.Address(it) }   // address.street, ...
            val quantities by PositiveInt.eachFieldAt(value.quantities) { Error.Quantity(it) } // quantities[2]
            val price by Money.parser.flatFieldAt(value.price, name = "unit_price") { Error.Price(it) }
            Shipment(contact, address, quantities, price)
        }
    }
}
```

| Helper | Use it for | Path of a failure |
| --- | --- | --- |
| `fieldAt(value) { ... }` | one value from a factory (also with `onMissing` for `required()`) | `contact` |
| `eachFieldAt(values) { ... }` | every element of a list | `quantities[2]` |
| `flatFieldAt(value) { ... }` | a factory that accumulates errors without paths (`GeoPoint`, `Money.parser`) | `unit_price`, once per error |
| `nestedFieldAt(value) { ... }` | a composite whose factory reports `FieldError`s | `address.street` |
| `fieldAt { ... }` | a dependent step or a check across fields | the property's name |

A composite built this way raises `NonEmptyList<FieldError<Error>>`. `FieldError`
pairs your precise error with a `FieldPath`, which renders as `lines[2].price` or, for
APIs, as the JSON Pointer `/lines/2/price`. The original `field` helpers are unchanged
for composites that don't need paths.

`ErrorMessages.describe(error)` gives every error this library defines a stable code,
such as `email.invalid_format`, a default English sentence, and the arguments a
translation needs. Descriptions never quote rejected text, which may be personal data
or a secret. Describe your own errors by delegating to it. To localize, look up the code
in your own catalog.

`toProblemDetails(describe)` turns a batch of field errors into an RFC 9457 problem
(`422` by default) whose `errors` list each field's pointer, code, message, and the
message's arguments, so a client can show the problem in its own language. The
problem's `detail` defaults to an English count such as "The request has 3 problems.";
pass `detail =` your own text, or `null` to leave it out. It is plain data; render it as
`application/problem+json` with your JSON library.
[PathValidationTest](src/commonTest/kotlin/io/liquidsoftware/safedomain/PathValidationTest.kt)
shows the whole flow.

### Boundary mode

Every error this library defines implements the marker interface `ValidationError`.
A composite that only exists to check a request or a form can raise
`NonEmptyList<FieldError<ValidationError>>` and declare its fields with no wrapper
errors and no `onError` lambdas; each library error is recorded as it is:

```kotlin
data class SignupRequest(val email: Email, val name: NonBlankString, val country: CountryCode, val seats: PositiveInt) {
    data class Input(val email: String?, val name: String, val country: String, val seats: Int)

    companion object : ValueFactory<Input, NonEmptyList<FieldError<ValidationError>>, SignupRequest> {
        context(raise: Raise<NonEmptyList<FieldError<ValidationError>>>)
        override fun from(value: Input): SignupRequest = validate {
            val email by Email.required().fieldAt(value.email) // RequiredError.Missing or an Email.Error
            val name by NonBlankString.fieldAt(value.name)
            val country by CountryCode.fieldAt(value.country)
            val seats by PositiveInt.fieldAt(value.seats)
            SignupRequest(email, name, country, seats)
        }
    }
}

val problem = SignupRequest.fromEither(input).mapLeft { it.toProblemDetails() }
```

`toProblemDetails()` without a `describe` argument uses
`ErrorMessages.describe(ValidationError)`, which describes any library error. The
other boundary-mode helpers are `eachFieldAt(values)`, `flatFieldAt(value)` and
`nestedFieldAt(value)` (for a nested boundary composite), and the precise forms with a
lambda still work in the same block.

Which to use: keep precise per-composite errors (the `Shipment` above) when the domain
branches on them, such as retrying, compensating, or choosing an outcome. Use boundary
mode when the only consumer of the errors is a message or a response. To add your own
failure to a boundary composite, give it a type that implements `ValidationError` and
describe it yourself, delegating everything else:

```kotlin
data class SeatsOverLimit(val limit: Int) : ValidationError

val describe = { error: ValidationError ->
    when (error) {
        is SeatsOverLimit -> ErrorMessage("signup.seats_over_limit", "Book at most ${error.limit} seats.")
        else -> ErrorMessages.describe(error)
    }
}
failures.toProblemDetails(describe)
```

`ErrorMessages.describe(ValidationError)` throws `IllegalArgumentException` for an error
the library does not define, so a consumer error always needs its own branch.

## JSON and other formats

The `impossible-serialization` module (in `serialization/`) has a
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) serializer for
every type. Decoding runs the type's real factory, so invalid data never becomes a
value; there is no `fromTrusted` hidden in a decoder.

```kotlin
@Serializable
data class Signup(
    @Serializable(with = EmailSerializer::class) val email: Email,
    @Serializable(with = SecretSerializer::class) val password: Secret,
    @Serializable(with = TypedIdSerializer::class) val referrer: TypedId<User>? = null,
)

try {
    Json.decodeFromString<Signup>(body)
} catch (e: InvalidValueException) {
    e.error     // Email.Error.InvalidFormat, the factory's typed error
    e.messages  // [ErrorMessage(code = "email.invalid_format", ...)]
}
```

Or register `safeDomainSerializersModule` once and mark fields `@Contextual`:
`Json { serializersModule = safeDomainSerializersModule }`.

| Type | Wire form |
| --- | --- |
| Text, contact, locale, web types, `Currency`, `TimeZoneId` | a string |
| `TypedId` | canonical UUID text |
| `PositiveInt`, `NegativeInt`, `PositiveLong`, `NegativeLong`, `BoundedInt` | a number |
| `Decimal` and its refinements, `Percentage` | a string such as `"12.30"`, so no platform rounds it through a double |
| `Money` and its sign refinements | `{"amount": "12.30", "currency": "EUR"}` |
| `DateRange`, `TimeWindow` | `{"start": ..., "endInclusive": ...}` and `{"start": ..., "endExclusive": ...}` with ISO 8601 text |
| `PositiveDuration` | ISO 8601 duration text such as `"PT1H30M"` |
| `GeoPoint` | `{"latitude": 52.37, "longitude": 4.89}` |
| `UniqueList`, `BoundedList` | an array |

Some things to know:
- **Failures.** A value the factory rejects throws `InvalidValueException`, a
  `SerializationException` carrying the typed error and its `ErrorMessages` codes.
  Data the format cannot read, such as a malformed date, throws a plain
  `SerializationException`. Neither message quotes the rejected input.
- **Secrets.** `SecretSerializer` reads secrets but refuses to write one, so a
  password cannot end up in a response or a log by accident.
- **Configured types.** `BoundedInt` and `BoundedList` need their bounds, so declare a
  serializer per use, e.g. `object RatingSerializer : KSerializer<BoundedInt> by BoundedIntSerializer(1..5)`.
- **`TypedId` and `@Contextual`.** The serialization plugin in Kotlin 2.4.21 crashes on
  `@Contextual` for a generic value class, so mark `TypedId` fields
  `@Serializable(with = TypedIdSerializer::class)`.
- **Your own types.** `asSerializer` turns any `ValueFactory` into a validating
  serializer: `Sku.asSerializer("com.example.Sku", String.serializer(), Sku::value) { listOf(describe(it)) }`.

A decoder stops at the first invalid field. When a client needs every problem at once,
decode into a DTO of plain strings and numbers and build the domain value with the
path-aware helpers under [Field paths, messages, and problem details](#field-paths-messages-and-problem-details).
[SerializersTest](serialization/src/commonTest/kotlin/io/liquidsoftware/safedomain/serialization/SerializersTest.kt)
shows each case, and `SerializationLawsTest` checks that every serializer reads back
what it writes and turns arbitrary text into a value or an `InvalidValueException`.

## Property-based testing

The `impossible-testing` module (in `testing/`) publishes [kotest](https://kotest.io/docs/proptest/property-based-testing.html)
generators for every type, so you can property-test the business types you build
from them. Each generator is an extension on the type's companion and produces
values through the type's real factory, so a generated value has passed the same
validation as production input.

`impossible-testing` depends on kotest-property 6.x as an API dependency, since its
generators are kotest `Arb`s. A project still on kotest 5 cannot use it until it
upgrades.

```kotlin
import io.liquidsoftware.safedomain.testing.arb

// Your type, built from already validated parts.
data class OrderLine(val sku: Slug, val quantity: PositiveInt, val unitPrice: NonNegativeMoney)

val usd = Currency.fromTrusted("USD")
val orderLines = arbitrary {
    OrderLine(Slug.arb().bind(), PositiveInt.arb(1..100).bind(), NonNegativeMoney.arb(Arb.constant(usd)).bind())
}
checkAll(orderLines) { line -> /* your invariants */ }
```

Generators cover the edge cases each type documents: zero, both ends of a range,
`Int.MIN_VALUE`, the nil UUID, `::` and `::1`, the poles and the antimeridian, and so
on. `Money.arb()` defaults to amounts of about a billion major units at most, so sums
of a few never overflow; pass a wider `minor` range to test overflow handling.
`Arb.untrustedText()` and `Arb.nearMiss(valid)` produce input for exercising your own
parsers: mixed scripts, odd whitespace and surrogate pairs, or valid text one edit away.

Every error type has a generator too, such as `Email.errorArb()`,
`Money.arithmeticErrorArb()` or `BoundedList.errorArb(1..10)`. Each one runs the real
factory or operation on input built to fail, so it yields exactly the errors callers
receive, every variant included. Use them to test how your application describes,
translates, or reports failures:

```kotlin
checkAll(Money.errorArb()) { error -> assertNotNull(myCatalog[ErrorMessages.describe(error).code]) }
```

The module's own tests are laws the library promises on every platform:

| Law | Test |
| --- | --- |
| Rendering and reparsing gives an equal value with the same hash | `RoundTripLawsTest` |
| Every parser returns a value or a typed error on any text, never an exception, and reparses its own output | `ParserLawsTest` |
| Equality and hashing agree with each normalization (email domains, DNS case, IP text forms, decimal scale), and rejected personal data never reaches error text | `ValueSemanticsLawsTest` |
| Error descriptions have well-formed codes and never quote rejected text; field paths survive JSON Pointer encoding | `MessageLawsTest` |
| Every error has a well-formed description, and the codes produced are exactly the published set, so a renamed or missing code fails | `ErrorLawsTest` |
| Sign-refined arithmetic is exact or reports overflow; rounding brackets the exact value | `NumericLawsTest` |
| Money addition is commutative and associative, currencies never mix, and allocation and splitting never lose a minor unit | `MoneyLawsTest` |
| Date ranges and half-open time windows overlap consistently; past and future checks split dates around today | `TimeLawsTest` |
| Collection invariants survive their operations, and validation reports one error per failure | `CollectionLawsTest` |

The tests use a fixed seed, so runs are deterministic. These laws found a JS-only
bug on their first run: `LanguageTag` rejected `de-1996` on JS while the JVM accepted
it, because Kotlin/JS `Regex.matches` does not backtrack to a longer match.

## Current scope

This library supplies validated construction, mapped factories, value semantics,
date range operations, collection invariants, and minor-unit money arithmetic on every
Kotlin Multiplatform target listed under [Build](#build), plus a few JVM
conversions. Serialization adapters live in the separate `impossible-serialization`
module (see [JSON and other formats](#json-and-other-formats)). Arithmetic on an extracted
`Int` has ordinary Kotlin semantics; the wrappers' own operations preserve their
invariants and report overflow explicitly. `TimeZoneId` is the one type whose
validation depends on platform data: each target resolves ids with its own time zone
database, so a zone added recently may be known on one platform and not another.

External data, including model-produced structured output, should pass through
the factories. The serializers in `impossible-serialization` do; any other adapter
must enforce the same validation.

## Build

The library is Kotlin Multiplatform, built with Kotlin **2.4**, a **Java 17**
toolchain for the JVM target, and the Gradle wrapper. Java 17 is the floor: the JVM
artifacts contain Java 17 bytecode and use only the JDK 17 API, the JVM tests run on
JDK 17, and the artifacts run on JDK 17 and later. Gradle downloads JDK 17 when it is
not installed. Targets:

| Target | Tests |
| --- | --- |
| `jvm` | `./gradlew jvmTest` |
| `js` (browser and Node.js) | `./gradlew jsNodeTest`; browser tests are disabled because they would need a local browser |
| `wasmJs` (browser and Node.js) | `./gradlew wasmJsNodeTest`; browser tests are disabled, as for `js` |
| `linuxX64` | `./gradlew linuxX64Test` |
| `macosArm64`, `iosArm64`, `iosSimulatorArm64` | `./gradlew macosArm64Test iosSimulatorArm64Test`; Apple targets build only on macOS |

Every pull request runs the [Checks](.github/workflows/checks.yml) workflow:
detekt, the JVM, JS, Wasm and Linux tests with a coverage floor on Linux, and the macOS and
iOS simulator tests on macOS. The same checks run locally:

| Check | Command |
| --- | --- |
| Lint and formatting ([detekt](https://detekt.dev) with ktlint's rules) | `./gradlew detekt`; `./gradlew detekt -PdetektAutoCorrect` fixes formatting |
| Coverage ([Kover](https://github.com/Kotlin/kotlinx-kover)), at least 90% of lines | `./gradlew koverVerify`; `./gradlew koverHtmlReport` writes `build/reports/kover/html` |
| Binary compatibility (the Kotlin Gradle plugin's [ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html)) | `./gradlew checkKotlinAbi`; `./gradlew updateKotlinAbi` rewrites the dumps |

The settings shared by all three modules (targets, toolchain, JVM bytecode level,
explicit API mode, ABI validation, group and version) live in one convention plugin,
[build-logic/src/main/kotlin/impossible.kotlin-library.gradle.kts](build-logic/src/main/kotlin/impossible.kotlin-library.gradle.kts).
Each module applies `id("impossible.kotlin-library")` and declares only its own
dependencies.

[config/detekt/detekt.yml](config/detekt/detekt.yml) lists this repository's changes
to detekt's defaults, each with its reason.

All three modules compile in Kotlin's
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode):
every declaration visible outside the module states its visibility (`public`,
`internal` or `private`) and every public function or property states its type,
so nothing becomes public API by default. A missing modifier fails compilation.

Each module's public API is recorded in its `api/` folder: a `.api` file for the JVM
and a `.klib.api` file for the JS, Wasm and native targets. `checkKotlinAbi` fails when the
compiled API differs from those files, so every addition, removal or signature change
shows up in review as a diff to them. After an intended API change, run
`./gradlew updateKotlinAbi` and commit the updated files with the change. The
validation is an experimental Kotlin Gradle plugin feature, built in, with no extra
dependency.

Common code lives in `src/commonMain` and `src/commonTest`; it uses no JDK types,
so validation behaves the same on every platform. Every dependency and plugin version
is declared once in [gradle/libs.versions.toml](gradle/libs.versions.toml) (the npm
package below is the exception, declared in the build script), and
[Dependabot](.github/dependabot.yml) proposes all updates together in one weekly pull
request. Versions are listed only there, not here, so this README cannot fall behind.
Dependencies:

- [Arrow Core](https://github.com/arrow-kt/arrow), an API
  dependency because `Raise` appears in the public signatures.
- [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime), an API
  dependency for `LocalDate`. `Clock`, `Instant` and `Duration` come from the
  standard library's `kotlin.time`.
- [bignum](https://github.com/ionspin/kotlin-multiplatform-bignum), an
  implementation dependency behind `Decimal`.
- [@js-joda/timezone 2.23.0](https://www.npmjs.com/package/@js-joda/timezone), an npm
  dependency of the JS and Wasm targets only that gives `TimeZoneId` the IANA database there. It is the newest
  release compatible with the `@js-joda/core` version kotlinx-datetime uses. The library
  loads the full database, about 730 KB minified (about 40 KB gzipped), so every JS and
  Wasm bundle that depends on the library carries it, even one that never uses
  `TimeZoneId`.
- [kotest-property](https://kotest.io/docs/proptest/property-based-testing.html) 6.x,
  an API dependency of `impossible-testing` only; the library itself does not use it.
  That module's tests also use kotlinx-coroutines-test to run suspending checks.
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), an
  API dependency of `impossible-serialization` only (core; its tests use the JSON
  format). That module applies the Kotlin serialization compiler plugin.
- Build tooling only, not shipped: [detekt](https://detekt.dev) with its
  ktlint rules (a detekt 2 pre-release, built on the Kotlin 2.4 compiler), and
  [Kover](https://github.com/Kotlin/kotlinx-kover) for coverage.

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
`fromTrusted`. `ValueFactoryCombinatorsTest` defines a type from combinators and
covers their ordering, short-circuiting, error mapping and exception propagation. `BusinessValueCompositionTest` demonstrates a composite with
clock-checked dates, a range relationship failure alongside contact failures, and a
nested money error batch. `DecimalAndIdentityCompositionTest` demonstrates
accumulating construction with decimals, percentages, tagged IDs, and HTTP URLs.
Money tests cover minor-unit construction, exact and rounded arithmetic, overflow,
allocation, and typed failures. Collection tests cover empty inputs, duplicate positions,
inclusive size bounds, snapshot immutability, invariant-preserving operations,
and accumulating composition. The library is not yet
configured for publication.

Contributor rules are in [AGENTS.md](AGENTS.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
