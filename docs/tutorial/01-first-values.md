# Step 1: First values

**You will learn** how to build a value that cannot be invalid, how a failure arrives
as a typed value instead of an exception, and the two ways to work with it: as an
`Either`, or inside a function with a `Raise` context.

Imports for this step: `arrow.core.Either`, `arrow.core.getOrElse`,
`arrow.core.raise.Raise`, `arrow.core.raise.either`,
`io.liquidsoftware.safedomain.fromEither`, `io.liquidsoftware.safedomain.fromOrElse`,
`io.liquidsoftware.safedomain.fromOrNull`, `io.liquidsoftware.safedomain.fromTrusted`,
`io.liquidsoftware.safedomain.numeric.PositiveInt` and
`io.liquidsoftware.safedomain.text.NonBlankString`.

## A title that is never blank

A bookshop's catalogue is full of strings that must not be empty: titles, author
names, descriptions. Instead of checking `isNotBlank()` wherever a title is used,
check it once and keep the result in a type that says so: `NonBlankString`.

Its constructor is private, so the only way to get one is `NonBlankString.from(raw)`.
`from` doesn't return `null` or throw; it *raises* a `NonBlankString.Error`. To turn
that into an ordinary value, run it inside Arrow's `either { }` block:

```kotlin
fun titleOf(raw: String): Either<NonBlankString.Error, NonBlankString> =
    either { NonBlankString.from(raw) }
```

An `Either<E, A>` holds exactly one of two things: a `Right` with the value, or a
`Left` with the error. `titleOf("Dune")` is `Right(NonBlankString("Dune"))`;
`titleOf(" ")` is `Left(NonBlankString.Error.Blank)`. The text is kept exactly as given: `"  Dune  "`
keeps its spaces. The library never trims, case-folds or repairs input unless you
ask it to (step 3 shows how).

Every factory also has three shortcuts for code outside a `Raise` scope:
`fromEither(raw)` is the same `Either` as `either { from(raw) }`, `fromOrNull(raw)`
gives the value or `null` when you don't need the reason, and `fromOrElse(raw) { ... }`
hands the error to a block that supplies a value or returns early:

```kotlin
fun titleOrNull(raw: String): NonBlankString? = NonBlankString.fromOrNull(raw)

fun titleOrUntitled(raw: String): NonBlankString =
    NonBlankString.fromOrElse(raw) { NonBlankString.fromTrusted("Untitled") }
```

## Errors are values

Each type's errors form a small sealed hierarchy, so a `when` over them is checked by
the compiler. If the library ever added a case, your code would stop compiling instead
of failing at run time:

```kotlin
fun describeTitle(raw: String): String = when (val title = titleOf(raw)) {
    is Either.Right -> "Title: ${title.value.value}"
    is Either.Left -> when (title.value) {
        NonBlankString.Error.Blank -> "A book needs a title."
        is NonBlankString.Error.TooLong -> "That title is too long."
    }
}
```

(`TooLong` only happens when you ask for a maximum length, which step 3 does. The
compiler still makes you say what to do about it.)

## Functions that raise

`Either` is handy at the edge of your program, but threading it through every
function gets noisy. The other way is to *declare* that a function may fail. A
function with a `context(raise: Raise<E>)` parameter can call anything that raises
`E`, and returns its success type directly:

```kotlin
context(raise: Raise<PositiveInt.Error>)
fun copies(raw: Int): PositiveInt = PositiveInt.from(raw)

context(raise: Raise<PositiveInt.Error>)
fun restock(onHand: Int, delivered: Int): Pair<PositiveInt, PositiveInt> =
    copies(onHand) to copies(delivered)
```

There's no unwrapping and no `if (result.isLeft) return`: the first failure stops the
function, the way an exception would, but the failure is part of the signature and
nothing escapes as an exception. Run it inside `either { }` when you need a value
back: `either { restock(0, -1) }` is `Left(PositiveInt.Error.NonPositive(0))`. The
second call never ran.

At the edge of your program you can also fall back to a default with `getOrElse`:

```kotlin
fun copiesOrOne(raw: Int): PositiveInt =
    either<PositiveInt.Error, PositiveInt> { copies(raw) }.getOrElse { PositiveInt.ONE }
```

## Literals you know are valid

Some values come from you, not from users: limits, defaults, test data. For those,
`fromTrusted` skips the ceremony. It throws `IllegalArgumentException` if you got it
wrong, because that's a bug in your code, not bad input:

```kotlin
val maximumBasketSize: PositiveInt = PositiveInt.fromTrusted(50)
```

Never use `fromTrusted` for anything a user, a file, or another system gave you.
Common constants such as `PositiveInt.ONE` already exist.

## What you have now

- A value of type `NonBlankString` or `PositiveInt` is valid by construction: any code
  that receives one can rely on it without checking again.
- Failures are typed values you match on exhaustively, not exceptions.
- `either { }` turns a raising call into an `Either`; a `Raise` context lets functions
  compose failures without unwrapping.

The code and its tests are in
[Step01FirstValuesTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/step01/Step01FirstValuesTest.kt).

Next: [Step 2, your own errors](02-your-own-errors.md).
