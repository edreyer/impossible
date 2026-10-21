package io.liquidsoftware.safedomain.tutorial.step06

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.withError
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.collections.NonEmptyError
import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.collections.factory
import io.liquidsoftware.safedomain.eachField
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Slug
import io.liquidsoftware.safedomain.tutorial.step03.Isbn
import io.liquidsoftware.safedomain.validate
import kotlin.test.Test
import kotlin.test.assertEquals

// Code for docs/tutorial/06-collections.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

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

sealed interface BasketError {
    data class WrongSize(val cause: BoundedList.Error) : BasketError
}

private val basketSize = BoundedList.factory<Isbn>(1..3)

context(raise: Raise<BasketError>)
fun basketOf(books: List<Isbn>): BoundedList<Isbn> = basketSize.from(books, onError = BasketError::WrongSize)

context(raise: Raise<BasketError>)
fun BoundedList<Isbn>.add(book: Isbn): BoundedList<Isbn> = raise.withError(BasketError::WrongSize) { append(book) }

class Step06CollectionsTest {
    private val dune = Isbn.fromTrusted("9780441013593")
    private val valid = WishlistInput(owner = "Ada", isbns = listOf("9780441013593"), tags = listOf("classics", "space-opera"))

    private fun wishlist(input: WishlistInput): Either<NonEmptyList<Wishlist.Error>, Wishlist> =
        either { Wishlist.from(input) }

    @Test
    fun `eachField reports every invalid element with its index`() {
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    Wishlist.Error.InvalidIsbn(1, Isbn.Error.NotThirteenDigits),
                    Wishlist.Error.InvalidIsbn(2, Isbn.Error.WrongCheckDigit),
                ),
            ),
            wishlist(valid.copy(isbns = listOf("9780441013593", "978", "9780441013594"))),
        )
    }

    @Test
    fun `valid elements can still make an invalid collection`() {
        assertEquals(Either.Left(nonEmptyListOf(Wishlist.Error.NoBooks(NonEmptyError.Empty))), wishlist(valid.copy(isbns = emptyList())))
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    Wishlist.Error.DuplicateTags(
                        nonEmptyListOf(UniqueList.Error.Duplicate(0, 1), UniqueList.Error.Duplicate(0, 2)),
                    ),
                ),
            ),
            wishlist(valid.copy(tags = listOf("classics", "classics", "classics"))),
        )
    }

    @Test
    fun `collection fields accumulate alongside the others`() {
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    Wishlist.Error.InvalidOwner(NonBlankString.Error.Blank),
                    Wishlist.Error.NoBooks(NonEmptyError.Empty),
                    Wishlist.Error.InvalidTag(0, Slug.Error.InvalidFormat("Space Opera")),
                ),
            ),
            wishlist(WishlistInput(owner = "", isbns = emptyList(), tags = listOf("Space Opera"))),
        )
        assertEquals(listOf("classics", "space-opera"), wishlist(valid).getOrNull()?.tags?.value?.map { it.value })
    }

    @Test
    fun `bounded lists keep their size through every operation`() {
        val full: Either<BasketError, BoundedList<Isbn>> = either { basketOf(listOf(dune, dune, dune)).add(dune) }
        assertEquals(Either.Left(BasketError.WrongSize(BoundedList.Error.TooLarge(maximum = 3, actual = 4))), full)

        val empty: Either<BasketError, BoundedList<Isbn>> = either { basketOf(emptyList()) }
        assertEquals(Either.Left(BasketError.WrongSize(BoundedList.Error.TooSmall(minimum = 1, actual = 0))), empty)

        val two: Either<BasketError, BoundedList<Isbn>> = either { basketOf(listOf(dune)).add(dune) }
        assertEquals(2, two.getOrNull()?.size)
    }
}
