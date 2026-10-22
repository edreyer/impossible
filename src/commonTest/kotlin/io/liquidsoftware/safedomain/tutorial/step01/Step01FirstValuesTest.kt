package io.liquidsoftware.safedomain.tutorial.step01

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Code for docs/tutorial/01-first-values.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

fun titleOf(raw: String): Either<NonBlankString.Error, NonBlankString> =
    either { NonBlankString.from(raw) }

fun describeTitle(raw: String): String = when (val title = titleOf(raw)) {
    is Either.Right -> "Title: ${title.value.value}"
    is Either.Left -> when (title.value) {
        NonBlankString.Error.Blank -> "A book needs a title."
        is NonBlankString.Error.TooLong -> "That title is too long."
    }
}

context(raise: Raise<PositiveInt.Error>)
fun copies(raw: Int): PositiveInt = PositiveInt.from(raw)

context(raise: Raise<PositiveInt.Error>)
fun restock(onHand: Int, delivered: Int): Pair<PositiveInt, PositiveInt> =
    copies(onHand) to copies(delivered)

fun copiesOrOne(raw: Int): PositiveInt =
    either<PositiveInt.Error, PositiveInt> { copies(raw) }.getOrElse { PositiveInt.ONE }

val maximumBasketSize: PositiveInt = PositiveInt.fromTrusted(50)

class Step01FirstValuesTest {
    @Test
    fun `either turns a validation into a value`() {
        assertEquals("  Dune  ", titleOf("  Dune  ").getOrNull()?.value)
        assertEquals(Either.Left(NonBlankString.Error.Blank), titleOf(" \t"))
    }

    @Test
    fun `errors are values you match on exhaustively`() {
        assertEquals("Title: Dune", describeTitle("Dune"))
        assertEquals("A book needs a title.", describeTitle(""))
    }

    @Test
    fun `functions with a Raise context compose and stop at the first failure`() {
        val ok: Either<PositiveInt.Error, Pair<PositiveInt, PositiveInt>> = either { restock(3, 12) }
        assertEquals(3 to 12, ok.getOrNull()?.let { (onHand, delivered) -> onHand.value to delivered.value })

        val failed: Either<PositiveInt.Error, Pair<PositiveInt, PositiveInt>> = either { restock(0, -1) }
        assertEquals(Either.Left(PositiveInt.Error.NonPositive(0)), failed)
    }

    @Test
    fun `getOrElse supplies a fallback at the edge`() {
        assertEquals(7, copiesOrOne(7).value)
        assertEquals(PositiveInt.ONE, copiesOrOne(-4))
    }

    @Test
    fun `fromTrusted is for literals and throws on a programmer error`() {
        assertEquals(50, maximumBasketSize.value)
        assertFailsWith<IllegalArgumentException> { PositiveInt.fromTrusted(0) }
    }
}
