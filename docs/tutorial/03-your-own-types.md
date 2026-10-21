# Step 3: Your own types

**You will learn** how to define new validated types, such as a title, an ISBN, a
shelf and a star rating, by chaining small combinators onto existing factories, so you
rarely write a companion by hand.

Imports for this step: `io.liquidsoftware.safedomain.ValueFactory` and the combinators
`map`, `mapError`, `ensure` and `andThen` from `io.liquidsoftware.safedomain`, plus
`fromTrusted`, `numeric.BoundedInt`, `numeric.PositiveInt`, `text.NonBlankString`,
`text.Slug` and `kotlin.jvm.JvmInline`.

## The combinators

Each combinator takes a factory and returns a new one. The steps run in order and stop
at the first failure, so a later step never sees input an earlier step rejected.

| Combinator | What it adds |
| --- | --- |
| `map { ... }` | Transforms a valid value, usually by wrapping it in your type's private constructor. Must not fail. |
| `mapError { ... }` | Maps every failure into another error type, usually your type's own. |
| `ensure({ check }) { error }` | Raises `error` for a value that fails `check`. |
| `andThen(next)` | Feeds a valid value into another factory. With a second argument, maps that factory's errors too. |

The result is assigned to the companion with `by`, so your type is a `ValueFactory`
like any other. The parentheses around the chain are needed because a class header
can't end in a trailing lambda.

## A title: refine and wrap

Titles should be non-blank and at most 200 characters, with stray spaces from a form
removed. `NonBlankString.bounded` does both checks; trimming happens only because
we ask for it:

```kotlin
@JvmInline
value class Title private constructor(val value: String) {
    companion object : ValueFactory<String, NonBlankString.Error, Title> by (
        NonBlankString.bounded(PositiveInt.fromTrusted(200), trim = true).map { Title(it.value) }
    )
}
```

`Title.from("  Dune ")` is `Title("Dune")`. Its errors are still
`NonBlankString.Error` (`Blank` or `TooLong`), because a title adds no new way to fail.
`Title` is a value class, so at run time it's just the `String`.

## An ISBN: your own rules and errors

An ISBN-13 adds rules of its own, so it gets its own error type. `mapError` comes
first, so that each `ensure` can raise an `Isbn.Error`:

```kotlin
/** An ISBN-13: thirteen ASCII digits whose check digit is correct. */
@JvmInline
value class Isbn private constructor(val value: String) {
    sealed interface Error {
        data class InvalidText(val cause: NonBlankString.Error) : Error
        data object NotThirteenDigits : Error
        data object WrongCheckDigit : Error
    }

    companion object : ValueFactory<String, Error, Isbn> by (
        NonBlankString
            .mapError(Error::InvalidText)
            .ensure({ text -> text.value.length == 13 && text.value.all { it in '0'..'9' } }) { Error.NotThirteenDigits }
            .ensure({ text -> hasValidCheckDigit(text.value) }) { Error.WrongCheckDigit }
            .map { Isbn(it.value) }
    )
}

private fun hasValidCheckDigit(digits: String): Boolean =
    digits.mapIndexed { index, digit -> (digit - '0') * if (index % 2 == 0) 1 else 3 }.sum() % 10 == 0
```

The check digit is only computed for thirteen digits, because the second `ensure`
never sees text the first one rejected. `"978-0306406157"` fails with
`NotThirteenDigits` and `"9780306406158"` with `WrongCheckDigit`.

> **Why `mapError` first?** `ValueFactory` is covariant in its error type. Calling
> `ensure` on a factory whose error is `NonBlankString.Error` with an
> `Isbn.Error` would make Kotlin infer their common supertype, which is `Any`. Mapping
> first gives every later step the same error type. The library never widens an error
> silently, and neither should your types.

## A shelf: build on another type

A shelf code such as `science-fiction` is a `Slug` (lowercase words joined by hyphens)
of at most 24 characters. `andThen` hands the bounded text to `Slug`'s factory and maps
its errors with the second argument:

```kotlin
/** Where a book lives in the shop, such as `science-fiction`: a slug of at most 24 characters. */
@JvmInline
value class Shelf private constructor(val slug: Slug) {
    sealed interface Error {
        data class InvalidText(val cause: NonBlankString.Error) : Error
        data class NotASlug(val cause: Slug.Error) : Error
    }

    companion object : ValueFactory<String, Error, Shelf> by (
        NonBlankString.bounded(PositiveInt.fromTrusted(24))
            .mapError(Error::InvalidText)
            .map { it.value }
            .andThen(Slug) { Error.NotASlug(it) }
            .map { Shelf(it) }
    )
}
```

`Shelf` keeps the `Slug` itself, so code that needs a slug (to build a URL, say) gets
one without parsing again.

## A rating: configured factories

Some factories need configuration. `BoundedInt.factory(1..5)` accepts exactly the
integers in a range. The range is programmer configuration, not input, so it's a
plain `IntRange`; an empty range throws when the factory is created. Wrap it once and
the bounds become part of your type:

```kotlin
/** A review score from one to five stars. */
class Rating private constructor(val stars: Int) {
    companion object : ValueFactory<Int, BoundedInt.Error, Rating> by (
        BoundedInt.factory(1..5).map { Rating(it.value) }
    )
}
```

## Everything else comes free

`Isbn`, `Shelf`, `Title` and `Rating` are ordinary factories now. They have
`from(raw)`, `from(raw, onError = ...)` from step 2, `fromTrusted` for literals, and
everything the coming steps add (`optional()`, `required()`, `field`, and the rest).
Their constructors are private, so the chain is the only way in.

## What you have now

Four domain types, each a few lines, each impossible to construct from invalid input.
The next steps use them to build bigger things.

The code and its tests are in
[Step03OwnTypesTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/Step03OwnTypesTest.kt).

Next: [Step 4, collect every error](04-collect-every-error.md).
