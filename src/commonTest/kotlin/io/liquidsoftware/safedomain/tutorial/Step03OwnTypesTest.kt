package io.liquidsoftware.safedomain.tutorial.step03

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.andThen
import io.liquidsoftware.safedomain.ensure
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.map
import io.liquidsoftware.safedomain.mapError
import io.liquidsoftware.safedomain.numeric.BoundedInt
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Slug
import kotlin.jvm.JvmInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Code for docs/tutorial/03-your-own-types.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

@JvmInline
value class Title private constructor(val value: String) {
    companion object : ValueFactory<String, NonBlankString.Error, Title> by (
        NonBlankString.bounded(PositiveInt.fromTrusted(200), trim = true).map { Title(it.value) }
        )
}

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

/** A review score from one to five stars. */
class Rating private constructor(val stars: Int) {
    companion object : ValueFactory<Int, BoundedInt.Error, Rating> by (
        BoundedInt.factory(1..5).map { Rating(it.value) }
        )
}

class Step03OwnTypesTest {
    private fun <Input, Error, Value> ValueFactory<Input, Error, Value>.result(input: Input): Either<Error, Value> =
        either { from(input) }

    @Test
    fun `map wraps a library type in your own`() {
        assertEquals("Dune", Title.result("  Dune ").getOrNull()?.value)
        assertEquals(Either.Left(NonBlankString.Error.Blank), Title.result("   "))
        assertEquals(Either.Left(NonBlankString.Error.TooLong(maximum = 200, actual = 201)), Title.result("x".repeat(201)))
    }

    @Test
    fun `ensure adds checks that run in order`() {
        assertEquals("9780306406157", Isbn.result("9780306406157").getOrNull()?.value)
        assertEquals(Either.Left(Isbn.Error.InvalidText(NonBlankString.Error.Blank)), Isbn.result(""))
        assertEquals(Either.Left(Isbn.Error.NotThirteenDigits), Isbn.result("978-0306406157"))
        assertEquals(Either.Left(Isbn.Error.WrongCheckDigit), Isbn.result("9780306406158"))
    }

    @Test
    fun `andThen refines through another factory`() {
        assertEquals("science-fiction", Shelf.result("science-fiction").getOrNull()?.slug?.value)
        assertEquals(Either.Left(Shelf.Error.NotASlug(Slug.Error.InvalidFormat("Science Fiction"))), Shelf.result("Science Fiction"))
        assertEquals(
            Either.Left(Shelf.Error.InvalidText(NonBlankString.Error.TooLong(maximum = 24, actual = 25))),
            Shelf.result("a".repeat(25)),
        )
    }

    @Test
    fun `a configured factory becomes a type of its own`() {
        assertEquals(4, Rating.result(4).getOrNull()?.stars)
        assertEquals(Either.Left(BoundedInt.Error.OutOfRange(6, 1..5)), Rating.result(6))
    }

    @Test
    fun `your types get the whole factory toolkit for free`() {
        assertEquals(Isbn.fromTrusted("9780306406157"), Isbn.fromTrusted("9780306406157"))
        assertFailsWith<IllegalArgumentException> { Isbn.fromTrusted("not an isbn") }
    }
}
