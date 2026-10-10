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
| `io.liquidsoftware.safedomain` | `ValueFactory`, and the `validate { }` DSL (`field`, `flatField`, `eachField`, `Validated`) |
| `.text` | `NonBlankString` |
| `.contact` | `Email`, `Phone` |
| `.numeric` | `PositiveInt`, `NonNegativeInt`, `PositiveDecimal`, `NonNegativeDecimal`, `Percentage` |
| `.money` | `Currency`, `Money` |
| `.time` | `FutureDate`, `PastDate`, `DateRange` |
| `.identity` | `TypedId` |
| `.web` | `HttpUrl` |
| `.collections` | `NonEmptyLists`, `NonEmptySets`, `UniqueList`, `BoundedList` |

| Type | Invariant | Validation error |
| --- | --- | --- |
| `NonBlankString` | At least one character is not Kotlin whitespace | `NonBlankString.Error.Blank` |
| `PositiveInt` | `1..Int.MAX_VALUE` | `PositiveInt.Error.NonPositive(value)` |
| `NonNegativeInt` | `0..Int.MAX_VALUE` | `NonNegativeInt.Error.Negative(value)` |
| `PositiveDecimal` | Exact decimal `> 0` | `PositiveDecimal.Error.NonPositive(value)` |
| `NonNegativeDecimal` | Exact decimal `>= 0` | `NonNegativeDecimal.Error.Negative(value)` |
| `Percentage` | Exact whole percentage units in `0..100` | `Percentage.Error.OutOfRange(value)` |
| `TypedId<Tag>` | UUID with a retained domain tag | `TypedId.Error.InvalidFormat(value)` |
| `HttpUrl` | Absolute HTTP/HTTPS URI with a server host and bounded port | `HttpUrl.Error` |
| `FutureDate` | `LocalDate` strictly after an explicit `asOf` | `FutureDate.Error.NotFuture(value, asOf)` |
| `PastDate` | `LocalDate` strictly before an explicit `asOf` | `PastDate.Error.NotPast(value, asOf)` |
| `DateRange` | Inclusive endpoints with `start <= endInclusive` | `DateRange.Error.EndBeforeStart(...)` |
| `Currency` | ISO 4217 code supported by the running JDK | `Currency.Error.UnsupportedCode(value)` |
| `Money` | Exact signed decimal amount and validated currency | `NonEmptyList<Money.Error>` |
| `Email` | ASCII dot-atom mailbox and DNS-label domain | `Email.Error.InvalidFormat(value)` |
| `Phone` | International E.164-shaped text | `Phone.Error.InvalidFormat(value)` |
| `NonEmptyLists` | Factory adapter returning Arrow's `NonEmptyList<A>` | `NonEmptyLists.Error.Empty` |
| `NonEmptySets` | Factory adapter returning Arrow's `NonEmptySet<A>` | `NonEmptySets.Error.Empty` |
| `UniqueList<A>` | Ordered elements with no equal duplicates; empty allowed | `NonEmptyList<UniqueList.Error>` |
| `BoundedList<A>` | List size within retained inclusive bounds; duplicates allowed | `BoundedList.Error` |

The three primitive wrappers are value classes with private constructors and read-only `value` properties,
and a validated `from(...)` factory. Equality and hashing use the exact
underlying value within the same type. Construction returns the success type
directly and requires a named `Raise` context for that type's closed error model.

`NonBlankString` uses Kotlin's `isBlank` definition. Empty strings, tabs,
line breaks, and whitespace such as nonbreaking spaces are rejected. Accepted
input is preserved exactly: no trimming, case folding, or Unicode normalization.
Nonblank does not imply visible or safe-to-render text; for example, a zero-width
space (`U+200B`) is accepted because Kotlin does not classify it as whitespace.

## Usage

Use `either` when a result needs to exist as a value:

```kotlin
import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.numeric.NonNegativeInt
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString

val name: Either<NonBlankString.Error, NonBlankString> =
    either { NonBlankString.from("  Alice  ") } // Preserves surrounding spaces.

val quantity: Either<PositiveInt.Error, PositiveInt> =
    either { PositiveInt.from(3) }

val count: Either<NonNegativeInt.Error, NonNegativeInt> =
    either { NonNegativeInt.from(0) }

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
logical failure. The `Order` example stops at its first failure; `Money.from`
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

data class CustomerInput(val name: String, val email: String, val credit: Money.Input)

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
            val credit by Money.field(value.credit, Error::InvalidCredit)
            Customer(name, email, credit)
        }
    }
}
```

| Declaration | Result |
| --- | --- |
| `Factory.field(raw, Error::X)` | One field. A failure, including a nested factory's whole error batch, is mapped once with `onError`. |
| `Factory.flatField(raw, Error::X)` | One field from an accumulating factory such as `Money`. Each nested error is mapped and added to the parent list individually. |
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
val window by field {
    val start by PastDate.field(value.start, Error::InvalidStart)
    val end by PastDate.field(value.end, Error::InvalidEnd)
    ensure(start.value <= end.value) { Error.EndBeforeStart }
    start to end
}
```

`validate` requires `Raise<NonEmptyList<Error>>`, so composites built with it
expose a `NonEmptyList<Error>` factory, as `Money` does. Use the mapped
`from(raw, onError = ...)` overload directly for composites that should stop at
their first failure. `validate` has no field-count limit.

`validate` wraps Arrow's `accumulate` API, which Arrow 2.2.3 marks
`@ExperimentalRaiseAccumulateApi`. The opt-in is confined to
[Validation.kt](src/main/kotlin/io/liquidsoftware/safedomain/Validation.kt);
callers need none. `Validated<A>` is an alias for Arrow's
`RaiseAccumulate.Value<A>`.
[ValidationTest](src/test/kotlin/io/liquidsoftware/safedomain/ValidationTest.kt)
covers ordering, flattening, indexing, dependent blocks, and early reads.

## Decimals and percentages

`PositiveDecimal.from(BigDecimal(...))` and `NonNegativeDecimal.from(BigDecimal(...))`
enforce their sign invariants. `Percentage.from(BigDecimal("12.5"))` means **12.5%**
and accepts the inclusive range `0..100`. It is suitable for bounded proportions,
discounts, and progress; growth rates above 100% require a different domain type.
These factories accept already parsed `BigDecimal` values, like the integer
factories accept already parsed `Int` values.

All three types preserve decimal scale without rounding. Equality, hashing, and
ordering are numeric within each type, so `1.0` equals `1.00`. Their constructors
are private, and they expose no public copy operation that bypasses validation.
They supply validated construction and ordering; arithmetic on their extracted
values has ordinary `BigDecimal` behavior. Money supplies separate typed arithmetic.

```kotlin
import arrow.core.raise.either
import io.liquidsoftware.safedomain.numeric.NonNegativeDecimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveDecimal
import java.math.BigDecimal

val quantity = either { PositiveDecimal.from(BigDecimal("2.500")) }
val balance = either { NonNegativeDecimal.from(BigDecimal("0.00")) }
val discount = either { Percentage.from(BigDecimal("12.50")) }
```

## Typed identifiers

`TypedId<Tag>` is UUID-backed in this first pass. Define a distinct marker class
or interface for each domain identifier kind. The tag is invariant in the type
system and retained as a runtime Kotlin type, including generic arguments, for
equality and hashing. Customer and order IDs with the same UUID remain distinct
when stored in a heterogeneous collection.

`TypedId.factory<Tag>()` returns a reusable `ValueFactory` with the usual
`from(raw, onError = ...)` overload. Input must have the full `8-4-4-4-12` UUID
hexadecimal shape. Uppercase hexadecimal is accepted; shortened forms such as
`1-1-1-1-1`, braces, and whitespace are rejected. The stored `Uuid` renders
canonical lowercase. `TypedId.create<Tag>(uuid)` wraps an already typed
`kotlin.uuid.Uuid`; code holding a `java.util.UUID` can convert it with
the standard library's `toKotlinUuid()`.
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

The tag uses standard-library `typeOf`; no `kotlin-reflect` dependency is needed.
Kotlin callers cannot pass `TypedId<OrderTag>` where `TypedId<CustomerTag>` is
required. See [Kotlin typeOf](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.reflect/type-of.html).

## HTTP URLs

`HttpUrl.from(raw)` accepts absolute HTTP/HTTPS addresses with a server authority
recognized by the JDK's `URI.parseServerAuthority`. Hosts may be ASCII DNS names
(including single-label and encoded IDN names), IPv4 addresses, or bracketed IPv6
addresses. Ports are optional; supplied numeric ports must be in `0..65535`.
User information, queries, fragments, and legal Unicode path characters are
accepted. Unicode host names must be encoded before construction.

Input spelling is preserved without trimming, escaping, or path normalization.
The read-only `value` is an immutable `URI`. Equality and hashing follow the
[JDK URI contract](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/net/URI.html):
scheme/host and percent-escape hex case are ignored, path case is significant,
and omitted default ports and redundant path segments are not normalized.
No DNS lookup or network request occurs. A valid value establishes address syntax;
reachability, trusted hosts, credentials policy, and outbound request authorization
remain application concerns.

Failures distinguish `InvalidSyntax`, `NotAbsolute`, `UnsupportedScheme`,
`MissingHost`, and `InvalidPort`; the mapped factory adapts this closed model to
the consuming domain.

```kotlin
import arrow.core.raise.either
import io.liquidsoftware.safedomain.web.HttpUrl

val endpoint = either { HttpUrl.from("https://example.com:8443/offers?active=true") }
```

## Dates and ranges

`FutureDate` and `PastDate` store a `LocalDate` and an explicit `asOf` date.
Today is excluded by both strict comparisons. They do not consult a clock or
time zone. The comparison remains true against the stored reference; a
`FutureDate` is not proof that a date remains in the future as time passes.
Consuming applications select the reference date and revalidate when current
time matters. Equality and hashing include both the value and reference date.

`DateRange` includes both endpoints and accepts a single-day range. It supplies
`date in range` and `range.overlaps(other)`; sharing an endpoint is an overlap.
Dates are already parsed `LocalDate` values, so these factories report domain
relationships rather than parse text. Composite date types have private
constructors and no public copy method that could bypass their invariants.

```kotlin
import arrow.core.raise.either
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.FutureDate
import java.time.LocalDate

val asOf = LocalDate.of(2026, 10, 5)
val delivery = either { FutureDate.from(FutureDate.Input(asOf.plusDays(1), asOf)) }
val period = either { DateRange.from(DateRange.Input(asOf, asOf.plusDays(7))) }
```

## Money and currencies

`Currency.from("USD")` accepts codes supported by the running JDK's currency
data, including historic and pseudo-currencies. Codes are case-sensitive and
are never trimmed or repaired. This is not a guarantee of current legal tender.
See the [JDK currency contract](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/Currency.html).

`Money.from(Money.Input(amount = "12.3400", currency = "EUR"))` validates decimal
text and currency independently using `validate`. Its mapped factory can
wrap the complete `NonEmptyList<Money.Error>` into a parent domain's error.
Decimal text follows the JDK's `BigDecimal` syntax, including scientific notation;
whitespace, grouping separators, NaN, and infinity are rejected. Already typed
components use `Money.create(amount: BigDecimal, currency: Currency)` without
parsing or redundant validation.

Amounts can be positive, zero, or negative and can retain sub-minor-unit precision
for intermediate calculations. Input scale is preserved, while equality and
hashing use numeric amount and currency: USD `1.0` equals USD `1.00`, but EUR
`1.00` is different. No floating-point conversion or implicit rounding occurs.
This deliberately differs from scale-sensitive
[`BigDecimal.equals`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/math/BigDecimal.html#equals(java.lang.Object)).

`add`, `subtract`, and `compare` require the same currency. `multiply` accepts
an exact decimal scalar. `divide` and `round` require a `NonNegativeInt` scale
and an explicit `RoundingMode`; unary minus is exact. All operations preserve
currency and return new values. There is no global ordering between currencies.

```kotlin
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.NonNegativeInt
import io.liquidsoftware.safedomain.numeric.PositiveInt
import java.math.BigDecimal
import java.math.RoundingMode

val parsed: Either<NonEmptyList<Money.Error>, Money> = either {
    Money.from(Money.Input("1.005", "USD"))
}

context(raise: Raise<Money.ArithmeticError>)
fun total(price: Money, quantity: PositiveInt, scale: NonNegativeInt): Money =
    price.multiply(BigDecimal(quantity.value)).round(scale, RoundingMode.HALF_EVEN)
```

Arithmetic raises `Money.ArithmeticError.CurrencyMismatch`, `DivisionByZero`, or
`UnrepresentableResult` (decimal scale overflow or rounding needed when
`UNNECESSARY` was requested). A consuming application can map these with
`withError`, separately from input failures. Currency conversion, cash rounding,
allocation, formatting, and nonnegative-balance policies belong to future
explicit APIs or the consuming domain.

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

Both types preserve input exactly and use exact string equality and hashing.
They establish syntax, not contact reachability or ownership. Any normalization,
DNS lookup, delivery check, or verification workflow is an explicit application
operation.

## Collections

`NonEmptyLists.factory<A>()` and `NonEmptySets.factory<A>()` adapt construction
to `ValueFactory` and named `Raise` contexts while returning Arrow's actual
collection types. Empty input raises the adapter's `Error.Empty`. The list
preserves order and duplicates. The set applies ordinary element equality,
collapses duplicates, and retains first-occurrence iteration order. Use
`UniqueList` when duplicate input is an error rather than set semantics.

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
import io.liquidsoftware.safedomain.collections.NonEmptyLists
import io.liquidsoftware.safedomain.collections.UniqueList

val channels: Either<NonEmptyLists.Error, NonEmptyList<String>> = either {
    NonEmptyLists.factory<String>().from(listOf("email", "sms"))
}

val labels: Either<NonEmptyList<UniqueList.Error>, UniqueList<String>> = either {
    UniqueList.factory<String>().from(listOf("urgent", "urgent", "urgent"))
} // Left containing Duplicate(0, 1) and Duplicate(0, 2).
```

`BoundedList.Bounds.from(Input(minimum, maximum))` validates
`0 <= minimum <= maximum`, failing with `NegativeMinimum` or
`MaximumBelowMinimum`. `BoundedList.factory<A>(bounds)` then validates input size,
raising `TooSmall(minimum, actual)` or `TooLarge(maximum, actual)`. Both endpoints
are inclusive; `0..0` permits exactly an empty list, and equal positive bounds
require an exact size. Elements retain their order and values, including
duplicates.

These factories inherit the same mapped `from` overload as individual values:

```kotlin
import arrow.core.raise.Raise
import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.contact.Email

sealed interface InvitationError {
    data class InvalidBounds(val cause: BoundedList.Bounds.Error) : InvitationError
    data class InvalidRecipients(val cause: BoundedList.Error) : InvitationError
}

context(raise: Raise<InvitationError>)
fun invitationRecipients(raw: List<Email>): BoundedList<Email> {
    val bounds = BoundedList.Bounds.from(
        BoundedList.Bounds.Input(minimum = 1, maximum = 10),
        onError = InvitationError::InvalidBounds,
    )
    return BoundedList.factory<Email>(bounds).from(
        raw,
        onError = InvitationError::InvalidRecipients,
    )
}
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

All four factories copy collection structure into unmodifiable storage;
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
    NonEmptyLists.factory<NonBlankString>().from(elements, onError = Error::InvalidRecipients)
}
```

Independent collection fields are declared alongside it; parent errors can wrap
an entire `UniqueList` duplicate batch without matching each branch.
[CollectionCompositionTest](src/test/kotlin/io/liquidsoftware/safedomain/CollectionCompositionTest.kt)
demonstrates indexed element validation, nonempty lists and sets, uniqueness,
size bounds, and accumulation across all four fields. Relationships that depend
on valid elements run after their prerequisites succeed.

## Current scope

This library supplies validated construction, mapped factories, value semantics,
date range operations, collection invariants, and decimal money arithmetic. It does not yet include
serialization adapters, Java-facing convenience APIs, or Kotlin Multiplatform
targets. Arithmetic on an extracted
`Int` has ordinary Kotlin semantics; these wrappers do not make that arithmetic
overflow-safe. Any future arithmetic API must preserve invariants and report
overflow explicitly.

External data, including model-produced structured output, should pass through
the factories. Future serialization adapters must enforce the same validation.

## Build

The current scaffold uses Kotlin/JVM **2.4.20**, a **Java 26** toolchain, and the
**Gradle 9.6.0** wrapper. It pins
[Arrow Core 2.2.3](https://github.com/arrow-kt/arrow/releases/tag/2.2.3), the latest
stable release verified when this first pass was implemented. Arrow Core is an
API dependency because `Raise` appears in the public signatures.

Named context parameters are stable in Kotlin 2.4; no experimental context
compiler flag is needed. See the
[Kotlin context-parameter documentation](https://kotlinlang.org/docs/context-parameters.html)
and [Arrow's typed-error guide](https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/).

```sh
./gradlew test
./gradlew build
```

The tests cover invalid values, boundaries, preservation, equality and hashing,
and composition through named `Raise` contexts. Factory tests cover mapping
through multiple domain layers, accumulation, short-circuiting, and exception
propagation. `ValidationTest` covers the `validate` DSL: declaration order,
flattened and indexed nested errors, dependent field blocks, cross-field checks,
and early reads. `BusinessValueCompositionTest` demonstrates a composite of all six
new business types, including a range relationship failure alongside contact
failures and a nested money error batch. `DecimalAndIdentityCompositionTest`
demonstrates accumulating construction with decimals, percentages, tagged IDs,
and HTTP URLs. Identifier tests exercise runtime tag identity, including generic
tags. Money tests cover exact arithmetic, rounding decisions, immutability,
and typed failures. Collection tests cover empty inputs, duplicate positions,
inclusive size bounds, snapshot immutability, invariant-preserving operations,
and accumulating composition. The library is not yet
configured for publication.

Contributor rules are in [AGENTS.md](AGENTS.md).
