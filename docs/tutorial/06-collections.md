# Step 6: Collections

**You will learn** how to validate every element of a list and report each failure
with its position, and how to turn the valid elements into a collection with
invariants of its own: non-empty, free of duplicates, or within a size range.

Imports for this step: everything from step 4, plus `io.liquidsoftware.safedomain.eachField`,
`collections.factory` (the extension on Arrow's `NonEmptyList` companion),
`collections.NonEmptyError`, `collections.UniqueList`, `collections.BoundedList`,
`arrow.core.raise.withError`, `text.Slug` and the `Isbn` type from step 3.

## Two kinds of rule

A wishlist has rules about its elements (every ISBN valid, every tag a slug) and
rules about the collection (at least one book, no tag twice). The second kind only
makes sense once the first holds, so each collection is a dependent `field { }`:
validate the elements with `eachField`, then build the collection from them.

```kotlin
data class WishlistInput(val owner: String, val isbns: List<String>, val tags: List<String>)

class Wishlist private constructor(val owner: NonBlankString, val books: NonEmptyList<Isbn>, val tags: UniqueList<Slug>) {
    sealed interface Error {
        data class InvalidOwner(val cause: NonBlankString.Error) : Error
        data class InvalidIsbn(val index: Int, val cause: Isbn.Error) : Error
        data class NoBooks(val cause: NonEmptyError) : Error
        data class InvalidTag(val index: Int, val cause: Slug.Error) : Error
        data class DuplicateTags(val causes: NonEmptyList<UniqueList.Error>) : Error
    }

    companion object : ValueFactory<WishlistInput, NonEmptyList<Error>, Wishlist> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: WishlistInput): Wishlist = validate {
            val owner by NonBlankString.field(value.owner, Error::InvalidOwner)
            val books by field {
                val isbns by Isbn.eachField(value.isbns, Error::InvalidIsbn)
                NonEmptyList.factory<Isbn>().from(isbns, onError = Error::NoBooks)
            }
            val tags by field {
                val slugs by Slug.eachField(value.tags, Error::InvalidTag)
                UniqueList.factory<Slug>().from(slugs, onError = Error::DuplicateTags)
            }
            Wishlist(owner, books, tags)
        }
    }
}
```

- `eachField(values) { index, error -> ... }` validates every element and reports each
  failure with its zero-based index. With ISBNs `["9780441013593", "978",
  "9780441013594"]` you get `InvalidIsbn(1, NotThirteenDigits)` and
  `InvalidIsbn(2, WrongCheckDigit)`.
- `NonEmptyList.factory<A>()` returns Arrow's own `NonEmptyList`, so everything Arrow
  offers for it still works. An empty list raises `NonEmptyError.Empty`, even though
  every one of its (zero) elements was valid.
- `UniqueList.factory<A>()` reports *every* repeat as `Duplicate(firstIndex,
  duplicateIndex)`. Tags `["classics", "classics", "classics"]` give
  `Duplicate(0, 1)` and `Duplicate(0, 2)`, wrapped in one `DuplicateTags`.

Each collection field accumulates alongside the others, so an empty owner, no books
and a bad tag are reported together.

## A basket with a size limit

`BoundedList.factory<A>(range)` checks the size against an inclusive range, which is
configuration like `BoundedInt`'s. The resulting list keeps its bounds, so operations
that could break them need a `Raise` context:

```kotlin
sealed interface BasketError {
    data class WrongSize(val cause: BoundedList.Error) : BasketError
}

private val basketSize = BoundedList.factory<Isbn>(1..3)

context(raise: Raise<BasketError>)
fun basketOf(books: List<Isbn>): BoundedList<Isbn> = basketSize.from(books, onError = BasketError::WrongSize)

context(raise: Raise<BasketError>)
fun BoundedList<Isbn>.add(book: Isbn): BoundedList<Isbn> = raise.withError(BasketError::WrongSize) { append(book) }
```

`append` raises `BoundedList.Error`, and Arrow's `withError` maps it into
`BasketError`, the same job `onError` does for factories. Adding a fourth book gives
`WrongSize(TooLarge(maximum = 3, actual = 4))`; an empty basket gives
`WrongSize(TooSmall(minimum = 1, actual = 0))`.

## Operations keep the guarantee

`UniqueList` and `BoundedList` copy their input, are immutable, and implement
`Iterable`. Their own operations keep the invariant:

| Operation | `UniqueList` | `BoundedList` |
| --- | --- | --- |
| `map` | may create duplicates, so it raises | keeps the size, so it can't fail |
| `filter` | can't create duplicates | may shrink below the minimum, so it raises |
| `append` | raises on a duplicate | raises above the maximum |

Kotlin's other collection functions, such as `first` or `sortedBy`, work through
`Iterable`, and the `value` property gives a plain `List`. Both return ordinary
collections that no longer carry the guarantee.

## What you have now

A `Wishlist` that always has at least one valid book and never repeats a tag, and a
basket that can't grow past its limit, with every element failure reported by index.

The code and its tests are in
[Step06CollectionsTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/step06/Step06CollectionsTest.kt).

Next: [Step 7, decimals and money](07-decimals-and-money.md).
