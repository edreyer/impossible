package io.liquidsoftware.safedomain.tutorial.step04

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Slug
import io.liquidsoftware.safedomain.tutorial.step03.Isbn
import io.liquidsoftware.safedomain.tutorial.step03.Shelf
import io.liquidsoftware.safedomain.tutorial.step03.Title
import io.liquidsoftware.safedomain.validate
import kotlin.test.Test
import kotlin.test.assertEquals

// Code for docs/tutorial/04-collect-every-error.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

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

sealed interface CatalogError {
    data class InvalidListing(val causes: NonEmptyList<Listing.Error>) : CatalogError
}

context(raise: Raise<CatalogError>)
fun publish(input: ListingInput): Listing = Listing.from(input, onError = CatalogError::InvalidListing)

class Step04ValidateTest {
    private val valid = ListingInput(title = "Dune", isbn = "9780441013593", shelf = "science-fiction", copies = 3)

    @Test
    fun `validate reports every failing field in declaration order`() {
        val result: Either<NonEmptyList<Listing.Error>, Listing> =
            either { Listing.from(ListingInput(title = "", isbn = "123", shelf = "Sci Fi", copies = 0)) }
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    Listing.Error.InvalidTitle(NonBlankString.Error.Blank),
                    Listing.Error.InvalidIsbn(Isbn.Error.NotThirteenDigits),
                    Listing.Error.InvalidShelf(Shelf.Error.NotASlug(Slug.Error.InvalidFormat("Sci Fi"))),
                    Listing.Error.InvalidCopies(PositiveInt.Error.NonPositive(0)),
                ),
            ),
            result,
        )
    }

    @Test
    fun `valid input builds the composite`() {
        val listing = either<NonEmptyList<Listing.Error>, Listing> { Listing.from(valid) }.getOrNull()
        assertEquals("science-fiction", listing?.shelf?.slug?.value)
    }

    @Test
    fun `a parent can wrap the whole batch in one error`() {
        val result: Either<CatalogError, Listing> = either { publish(valid.copy(copies = -1)) }
        assertEquals(
            Either.Left(CatalogError.InvalidListing(nonEmptyListOf(Listing.Error.InvalidCopies(PositiveInt.Error.NonPositive(-1))))),
            result,
        )
    }
}
