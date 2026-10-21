package io.liquidsoftware.safedomain.tutorial.step02

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// Code for docs/tutorial/02-your-own-errors.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

sealed interface StockError {
    data class InvalidCopies(val cause: PositiveInt.Error) : StockError
}

context(raise: Raise<StockError>)
fun stockLevel(raw: Int): PositiveInt = PositiveInt.from(raw, onError = StockError::InvalidCopies)

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

sealed interface ImportError {
    data class InvalidBook(val line: Int, val cause: Book.Error) : ImportError
}

context(raise: Raise<ImportError>)
fun importLine(line: Int, input: BookInput): Book = Book.from(input) { ImportError.InvalidBook(line, it) }

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

class Step02OwnErrorsTest {
    @Test
    fun `onError wraps a library error in your own`() {
        val result: Either<StockError, PositiveInt> = either { stockLevel(0) }
        assertEquals(Either.Left(StockError.InvalidCopies(PositiveInt.Error.NonPositive(0))), result)
    }

    @Test
    fun `a composite factory stops at its first failure`() {
        val result: Either<Book.Error, Book> = either { Book.from(BookInput(title = " ", copies = 0)) }
        assertEquals(Either.Left(Book.Error.InvalidTitle(NonBlankString.Error.Blank)), result)
    }

    @Test
    fun `each layer wraps the layer below without enumerating it`() {
        val result: Either<ImportError, Book> = either { importLine(7, BookInput(title = "Dune", copies = -2)) }
        assertEquals(
            Either.Left(ImportError.InvalidBook(7, Book.Error.InvalidCopies(PositiveInt.Error.NonPositive(-2)))),
            result,
        )
    }

    @Test
    fun `a boundary turns errors into its own closed outcomes`() {
        val added = assertIs<AddBookOutcome.Added>(addBook(BookInput("Dune", 3)))
        assertEquals("Dune", added.book.title.value)
        assertEquals(AddBookOutcome.Rejected("Stock at least one copy."), addBook(BookInput("Dune", 0)))
    }
}
