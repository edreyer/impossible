# Step 12: Property testing

**You will learn** how to generate valid library values to property-test your own
types, how to make sure your parsers survive hostile input, and how to check that
your application handles every error the library can raise.

This step uses the `impossible-testing` module, which publishes
[kotest](https://kotest.io/docs/proptest/property-based-testing.html) generators for
every type. Imports: `io.liquidsoftware.safedomain.testing.arb`, `errorArb`,
`untrustedText` and `nearMiss`, plus `io.kotest.property.Arb`,
`io.kotest.property.PropTestConfig`, `io.kotest.common.ExperimentalKotest`,
`io.kotest.property.checkAll`,
`io.kotest.property.arbitrary.arbitrary`, `io.kotest.property.arbitrary.constant`
and `kotlinx.coroutines.test.runTest`.

## A fixed seed

Property tests generate hundreds of inputs per run. A fixed seed makes every run
generate the same ones, so a failure on CI is a failure on your machine too:

```kotlin
// A fixed seed keeps every run reproducible. PropTestConfig's constructor exposes
// experimental parameters, hence the opt-in; none of them are used.
@OptIn(ExperimentalKotest::class)
val seeded: PropTestConfig = PropTestConfig(seed = 20_261_021L, iterations = 300)
```

## Generate values of your own types

Each generator is an extension on the type's companion (`PositiveInt.arb(1..20)`,
`NonBlankString.arb(maxLength = 80)`, `NonNegativeMoney.arb(currency)`) and builds
its values through the type's real factory, so a generated value has passed the same
validation as production input. Combine them into generators for your types:

```kotlin
data class BasketLine(val title: NonBlankString, val copies: PositiveInt, val unitPrice: NonNegativeMoney)

val usd: Currency = Currency.fromTrusted("USD")

val basketLines: Arb<BasketLine> = arbitrary {
    BasketLine(
        title = NonBlankString.arb(maxLength = 80).bind(),
        copies = PositiveInt.arb(1..20).bind(),
        unitPrice = NonNegativeMoney.arb(Arb.constant(usd)).bind(),
    )
}

context(raise: Raise<Money.ArithmeticError>)
fun BasketLine.total(): NonNegativeMoney = unitPrice.multiply(copies)
```

Then state what must always hold:

```kotlin
@Test
fun `generated values compose into your own types`() = runTest {
    checkAll(seeded, basketLines) { line ->
        val total: Either<Money.ArithmeticError, NonNegativeMoney> = either { line.total() }
        assertEquals(line.unitPrice.money.minor * line.copies.value, total.getOrNull()?.money?.minor)
        assertEquals(usd, total.getOrNull()?.money?.currency)
    }
}
```

The generators include each type's edge cases: zero money, both ends of a range,
the nil UUID, and so on. `Money.arb()` keeps amounts to about a billion major units,
so sums of a few never overflow; pass a wider range when you want to test overflow.

## Hostile input

Your own parsers, such as step 3's `Shelf` (repeated in this module's test file),
must return a value or a typed error for *any* text, never throw.
`Arb.untrustedText()` mixes printable ASCII with odd whitespace, Cyrillic, Japanese
and characters outside the Basic Multilingual Plane:

```kotlin
@Test
fun `your parser returns a value or a typed error for any text`() = runTest {
    checkAll(seeded, Arb.untrustedText()) { text ->
        val shelf: Either<Shelf.Error, Shelf> = either { Shelf.from(text) }
        shelf.onRight { assertTrue(it.slug.value.length <= 24) }
    }
}
```

Random text almost never comes close to valid input, so it rarely reaches your
interesting rules. `Arb.nearMiss(valid)` takes valid text and makes one small edit:
a character inserted, deleted or replaced, or its case flipped. That probes the edges
of what you accept:

```kotlin
@Test
fun `near misses probe the edge of what your parser accepts`() = runTest {
    checkAll(seeded, Arb.nearMiss(Slug.arb().map { it.value })) { text ->
        val shelf: Either<Shelf.Error, Shelf> = either { Shelf.from(text) }
        val slug: Either<Slug.Error, Slug> = either { Slug.from(text) }
        if (text.length <= 24) assertEquals(slug.isRight(), shelf.isRight())
    }
}
```

## Every error, handled

Every error type has a generator too, such as `Email.errorArb()`,
`Money.arithmeticErrorArb()` or `BoundedList.errorArb(1..10)`. Each runs the real
factory or operation on input built to fail, so it yields exactly the errors callers
receive, every variant included. Use them to check that your application has a
translation, an HTTP status or a log line for every one:

```kotlin
@Test
fun `every error the library can raise has a message in your catalog`() = runTest {
    val catalog = setOf("email.invalid_format")
    checkAll(seeded, Email.errorArb()) { error ->
        assertTrue(ErrorMessages.describe(error).code in catalog)
    }
}
```

If a future version of the library added an error variant, a test like this would
tell you before your users did.

## The library's own laws

The module's own tests are laws the library promises on every platform: rendering and
reparsing gives an equal value, parsers never throw, money never loses a minor unit,
error descriptions never quote rejected text, and more. The
[README](../../README.md#property-based-testing) lists them all. Your types inherit
those guarantees, so your properties can focus on your own rules.

## Where to go next

You've now used every part of the library. The [README](../../README.md) is the
reference for each type's exact rules, normalization and equality, and
[AGENTS.md](../../AGENTS.md) records the design rules the library follows, which are
a good guide for your own types too.

The code and its tests are in
[Step12PropertyTestingTest.kt](../../testing/src/commonTest/kotlin/io/liquidsoftware/safedomain/testing/tutorial/Step12PropertyTestingTest.kt).

Back to the [tutorial overview](README.md).
