package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.geo.GeoPoint
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Slug
import kotlin.jvm.JvmInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ValueFactoryCombinatorsTest {
    @Test
    fun `a type defined from combinators validates every step in order`() {
        assertEquals(Either.Right("AB-12"), Sku.result("AB-12").map { it.value })
        assertEquals(Either.Left(Sku.Error.InvalidText(NonBlankString.Error.Blank)), Sku.result(" "))
        assertEquals(
            Either.Left(Sku.Error.InvalidText(NonBlankString.Error.TooLong(maximum = 12, actual = 13))),
            Sku.result("ABCDEFGHIJKLM"),
        )
        assertEquals(Either.Left(Sku.Error.UnsupportedCharacter), Sku.result("ab-12"))
    }

    @Test
    fun `a type defined from combinators keeps value semantics`() {
        assertEquals(Sku.result("AB-12"), Sku.result("AB-12"))
        assertEquals(Sku.fromTrusted("AB-12").hashCode(), Sku.fromTrusted("AB-12").hashCode())
        assertFailsWith<IllegalArgumentException> { Sku.fromTrusted("ab") }
    }

    @Test
    fun `map transforms only a constructed value`() {
        val length = NonBlankString.map { it.value.length }

        assertEquals(Either.Right(5), length.result("hello"))
        assertEquals(Either.Left(NonBlankString.Error.Blank), length.result(""))
        assertEquals(
            Either.Left(NonBlankString.Error.Blank),
            NonBlankString.map<String, NonBlankString.Error, NonBlankString, Int> {
                error("Mapped a failure")
            }.result(""),
        )
    }

    @Test
    fun `mapError maps a failure without touching success`() {
        val name = NonBlankString.mapError { error: NonBlankString.Error -> NameError(error) }

        assertEquals(Either.Left(NameError(NonBlankString.Error.Blank)), name.result(""))
        assertEquals(
            Either.Right("Ada"),
            NonBlankString.mapError<String, NonBlankString.Error, NonBlankString, NameError> {
                error("Mapped a success")
            }.result("Ada")
                .map { it.value },
        )
    }

    @Test
    fun `mapError receives an accumulating factory batch in one call`() {
        val calls = mutableListOf<NonEmptyList<GeoPoint.Error>>()
        val point = GeoPoint.mapError { errors ->
            calls += errors
            PlaceError(errors)
        }

        val result = point.result(GeoPoint.Input(latitude = 91.0, longitude = 181.0))

        val expected = nonEmptyListOf(GeoPoint.Error.LatitudeOutOfRange(91.0), GeoPoint.Error.LongitudeOutOfRange(181.0))
        assertEquals(Either.Left(PlaceError(expected)), result)
        assertEquals(listOf(expected), calls)
    }

    @Test
    fun `andThen with a shared error model refines a value further`() {
        val shortSlug = Slug.andThen(
            object : ValueFactory<Slug, Slug.Error, Slug> {
                context(raise: Raise<Slug.Error>)
                override fun from(value: Slug): Slug {
                    raise.ensure(value.value.length <= 8) { Slug.Error.InvalidFormat(value.value) }
                    return value
                }
            },
        )

        assertEquals(Either.Right("sale"), shortSlug.result("sale").map { it.value })
        assertEquals(Either.Left(Slug.Error.InvalidFormat("summer-sale")), shortSlug.result("summer-sale"))
        assertEquals(Either.Left(Slug.Error.InvalidFormat("Sale")), shortSlug.result("Sale"))
    }

    @Test
    fun `andThen maps the next factory's failures into the first error model`() {
        val handle = NonBlankString.bounded(PositiveInt.fromTrusted(10))
            .mapError(HandleError::InvalidText)
            .map { it.value }
            .andThen(Slug, HandleError::InvalidSlug)

        assertEquals(Either.Right("ada-l"), handle.result("ada-l").map { it.value })
        assertEquals(Either.Left(HandleError.InvalidText(NonBlankString.Error.Blank)), handle.result(""))
        assertEquals(Either.Left(HandleError.InvalidSlug(Slug.Error.InvalidFormat("Ada L"))), handle.result("Ada L"))
    }

    @Test
    fun `andThen never runs the next step after a failure`() {
        val next = object : ValueFactory<NonBlankString, NonBlankString.Error, NonBlankString> {
            context(raise: Raise<NonBlankString.Error>)
            override fun from(value: NonBlankString): NonBlankString = error("Next step ran after a failure")
        }

        assertEquals(Either.Left(NonBlankString.Error.Blank), NonBlankString.andThen(next).result(""))
        assertEquals(
            Either.Left(NonBlankString.Error.Blank),
            NonBlankString.andThen(next) { error: NonBlankString.Error -> error }.result(""),
        )
    }

    @Test
    fun `ensure raises its error only for a constructed value failing the predicate`() {
        val even = PositiveInt.mapError(NumberError::Invalid).ensure({
            it.value % 2 == 0
        }) { NumberError.Odd(it.value) }

        assertEquals(Either.Right(4), even.result(4).map { it.value })
        assertEquals(Either.Left(NumberError.Odd(3)), even.result(3))
        assertEquals(Either.Left(NumberError.Invalid(PositiveInt.Error.NonPositive(0))), even.result(0))
    }

    @Test
    fun `ensure checks nothing when the factory already failed`() {
        val checked = NonBlankString.ensure({
            error("Predicate ran after a failure")
        }) { error("Error built after a failure") }

        assertEquals(Either.Left(NonBlankString.Error.Blank), checked.result(""))
    }

    @Test
    fun `combined factories work with the mapped overload and optional`() {
        val result: Either<Order, Sku?> = either { Sku.optional().from(null, onError = ::Order) }
        val failure: Either<Order, Sku?> = either { Sku.optional().from("x", onError = ::Order) }

        assertEquals(Either.Right(null), result)
        assertEquals(Either.Left(Order(Sku.Error.UnsupportedCharacter)), failure)
    }

    @Test
    fun `unexpected exceptions from a combinator function propagate`() {
        val failure = IllegalStateException("Unexpected transform failure")

        val caught = assertFailsWith<IllegalStateException> {
            NonBlankString.map<String, NonBlankString.Error, NonBlankString, Int> { throw failure }.result("Ada")
        }

        assertSame(failure, caught)
    }

    @Test
    fun `a combined factory composes inside validate`() {
        val result = either<NonEmptyList<Sku.Error>, List<Sku>> {
            validate {
                val first by Sku.field("AB") { it }
                val second by Sku.field("cd") { it }
                val third by Sku.field("") { it }
                listOf(first, second, third)
            }
        }

        assertIs<Either.Left<NonEmptyList<Sku.Error>>>(result)
        assertEquals(
            listOf(Sku.Error.UnsupportedCharacter, Sku.Error.InvalidText(NonBlankString.Error.Blank)),
            result.value,
        )
    }

    private data class NameError(val cause: NonBlankString.Error)

    private data class PlaceError(val causes: NonEmptyList<GeoPoint.Error>)

    private sealed interface NumberError {
        data class Invalid(val cause: PositiveInt.Error) : NumberError

        data class Odd(val value: Int) : NumberError
    }

    private data class Order(val cause: Sku.Error)

    private sealed interface HandleError {
        data class InvalidText(val cause: NonBlankString.Error) : HandleError

        data class InvalidSlug(val cause: Slug.Error) : HandleError
    }

    private fun <Input, Error, Value> ValueFactory<Input, Error, Value>.result(input: Input): Either<Error, Value> =
        either { from(input) }

    /** The recipe the README shows: a refined type defined entirely by combinators. */
    @JvmInline
    private value class Sku private constructor(val value: String) {
        sealed interface Error {
            data class InvalidText(val cause: NonBlankString.Error) : Error

            data object UnsupportedCharacter : Error
        }

        // Parentheses let the chain use trailing lambdas inside a class header.
        companion object : ValueFactory<String, Error, Sku> by (
            NonBlankString.bounded(PositiveInt.fromTrusted(12))
                .mapError(Error::InvalidText)
                .ensure({ text ->
                    text.value.all { it in 'A'..'Z' || it in '0'..'9' || it == '-' }
                }) { Error.UnsupportedCharacter }
                .map { Sku(it.value) }
            )
    }
}
