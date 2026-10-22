# Step 2: Your own errors

**You will learn** how to wrap the library's errors in your domain's own error types,
how to build a composite type with the same factory contract the library uses, and how
to turn failures into a closed outcome at the edge of your program.

Imports for this step: `arrow.core.raise.Raise`, `arrow.core.raise.either`,
`io.liquidsoftware.safedomain.ValueFactory`, `io.liquidsoftware.safedomain.numeric.PositiveInt`
and `io.liquidsoftware.safedomain.text.NonBlankString`.

## Wrapping a library error

The library's types deliberately share no common error supertype. A
`PositiveInt.Error` means "not a positive number" and nothing more; whether that number
was a stock level, a page count or a quantity is your domain's business. Say so with
your own error type, and map into it with the `onError` overload every factory has:

```kotlin
sealed interface StockError {
    data class InvalidCopies(val cause: PositiveInt.Error) : StockError
}

context(raise: Raise<StockError>)
fun stockLevel(raw: Int): PositiveInt = PositiveInt.from(raw, onError = StockError::InvalidCopies)
```

The mapper runs only on failure. It wraps the whole lower-level error without
matching its cases, so if `PositiveInt.Error` gained a case, `StockError` wouldn't need
to change.

## A composite with the same contract

Every library type's companion implements `ValueFactory<Input, Error, Value>`: one
`from(value)` that raises `Error`. Your composite types can implement it too, and then
they get everything the library's types have, including the `onError` overload above:

```kotlin
data class BookInput(val title: String, val copies: Int)

class Book private constructor(val title: NonBlankString, val copies: PositiveInt) {
    sealed interface Error {
        data class InvalidTitle(val cause: NonBlankString.Error) : Error
        data class InvalidCopies(val cause: PositiveInt.Error) : Error
    }

    companion object : ValueFactory<BookInput, Error, Book> {
        context(raise: Raise<Error>)
        override fun from(value: BookInput): Book = Book(
            title = NonBlankString.from(value.title, onError = Error::InvalidTitle),
            copies = PositiveInt.from(value.copies, onError = Error::InvalidCopies),
        )
    }
}
```

Three things make `Book` impossible to get wrong:

1. The constructor is private, so `Book.from` is the only way in.
2. Its fields are already-validated types, so a `Book` can't hold a blank title.
3. `Book.Error` names exactly what can go wrong with a book.

`Book.from` stops at the first failure: with a blank title *and* zero copies you get
only `InvalidTitle`. That's often fine for internal code. Step 4 shows how to report
every failure at once, which is what a form needs.

## Layers wrap layers

A CSV import is one level up: it knows which line it was reading. It wraps `Book.Error`
the same way `Book` wrapped the library's errors, with a trailing lambda this time
because it adds the line number:

```kotlin
sealed interface ImportError {
    data class InvalidBook(val line: Int, val cause: Book.Error) : ImportError
}

context(raise: Raise<ImportError>)
fun importLine(line: Int, input: BookInput): Book = Book.from(input) { ImportError.InvalidBook(line, it) }
```

`either { importLine(7, BookInput("Dune", -2)) }` is
`Left(InvalidBook(7, InvalidCopies(NonPositive(-2))))`: the complete story, with each
layer adding only what it knows.

## Closing the loop at the boundary

At the edge (an HTTP handler, a CLI command, a message consumer) turn the result into
whatever that boundary speaks. A `when` over the sealed error keeps it exhaustive:

```kotlin
sealed interface AddBookOutcome {
    data class Added(val book: Book) : AddBookOutcome
    data class Rejected(val reason: String) : AddBookOutcome
}

fun addBook(input: BookInput): AddBookOutcome = either<Book.Error, Book> { Book.from(input) }.fold(
    ifLeft = { error ->
        when (error) {
            is Book.Error.InvalidTitle -> AddBookOutcome.Rejected("A book needs a title.")
            is Book.Error.InvalidCopies -> AddBookOutcome.Rejected("Stock at least one copy.")
        }
    },
    ifRight = { book -> AddBookOutcome.Added(book) },
)
```

Step 10 replaces these hand-written sentences with stable error codes and messages.

## What you have now

- Your own error types, each wrapping the layer below with `onError`.
- A composite, `Book`, built only through a `ValueFactory` companion.
- A boundary that turns every failure into a closed outcome.

The code and its tests are in
[Step02OwnErrorsTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/step02/Step02OwnErrorsTest.kt).

Next: [Step 3, your own types](03-your-own-types.md).
