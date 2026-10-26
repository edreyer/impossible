# Step 4: Collect every error

**You will learn** how to validate every field of a composite and report all the
failures together, which is what a form or an API client needs, using the
`validate { }` DSL.

Imports for this step: `arrow.core.NonEmptyList`, `arrow.core.raise.Raise`,
`io.liquidsoftware.safedomain.ValueFactory`, `io.liquidsoftware.safedomain.field`,
`io.liquidsoftware.safedomain.validate`, `numeric.PositiveInt`, `text.NonBlankString`,
and the `Title`, `Isbn` and `Shelf` types from step 3.

## Stopping early isn't always what you want

`Book` in step 2 stopped at its first failure. Someone listing a new book through the
shop's back office would fix the title, submit, and only then learn the ISBN was wrong
too. `validate { }` runs every field and raises all their failures together, in the
order the fields are declared.

## A listing with four fields

```kotlin
data class ListingInput(val title: String, val isbn: String, val shelf: String, val copies: Int)

class Listing private constructor(val title: Title, val isbn: Isbn, val shelf: Shelf, val copies: PositiveInt) {
    sealed interface Error {
        data class InvalidTitle(val cause: NonBlankString.Error) : Error
        data class InvalidIsbn(val cause: Isbn.Error) : Error
        data class InvalidShelf(val cause: Shelf.Error) : Error
        data class InvalidCopies(val cause: PositiveInt.Error) : Error
    }

    companion object : ValueFactory<ListingInput, NonEmptyList<Error>, Listing> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: ListingInput): Listing = validate {
            val title by Title.field(value.title, Error::InvalidTitle)
            val isbn by Isbn.field(value.isbn, Error::InvalidIsbn)
            val shelf by Shelf.field(value.shelf, Error::InvalidShelf)
            val copies by PositiveInt.field(value.copies, Error::InvalidCopies)
            Listing(title, isbn, shelf, copies)
        }
    }
}
```

Compare it with `Book` from step 2:

- The error type is `NonEmptyList<Error>`: a failed listing has at least one error, and
  maybe several.
- Each field is declared with `val name by Factory.field(raw, onError)`. The name is
  yours to choose; the delegate runs the factory and records any failure.
- The last line reads the fields to build the result. It runs only when every field
  succeeded.

With every field wrong:

```text
Listing.from(ListingInput(title = "", isbn = "123", shelf = "Sci Fi", copies = 0))
// Left([InvalidTitle(Blank), InvalidIsbn(NotThirteenDigits),
//       InvalidShelf(NotASlug(InvalidFormat("Sci Fi"))), InvalidCopies(NonPositive(0))])
```

## Read fields at the end

A declared field's value is only available once its validation succeeded. Reading a
failed field raises everything collected so far and ends the block, so the fields
declared after it would never run. So read fields only in the final construction.
Step 5 shows `field { }`, a block that declares fields of its own and may read them,
because a failure there stops only that block.

## The batch is still one value

`Listing` is a `ValueFactory`, so the `onError` overload from step 2 still works. It
receives the whole batch at once, so a caller can wrap it in a single error of its own:

```kotlin
sealed interface CatalogError {
    data class InvalidListing(val causes: NonEmptyList<Listing.Error>) : CatalogError
}

context(raise: Raise<CatalogError>)
fun publish(input: ListingInput): Listing = Listing.from(input, onError = CatalogError::InvalidListing)
```

## About the DSL

`validate` is built on Arrow's `accumulate` API, which Arrow marks experimental. The
library keeps that API inside: the block runs in the library's own `ValidationScope`,
which is a `Raise` of your error, and each field is a `Validated` value, so your code
needs no `@OptIn` and an Arrow upgrade cannot change it. There's no limit on the
number of fields.

## What you have now

A `Listing` whose factory reports every invalid field in one go, built from the types
of step 3 without repeating any of their rules.

The code and its tests are in
[Step04ValidateTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/step04/Step04ValidateTest.kt).

Next: [Step 5, optional and dependent fields](05-optional-and-dependent-fields.md).
