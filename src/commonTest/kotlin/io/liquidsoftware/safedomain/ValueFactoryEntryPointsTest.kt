package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValueFactoryEntryPointsTest {
    @Test
    fun `fromEither agrees with either around from`() {
        for (raw in listOf("a@example.com", "nope", "")) {
            assertEquals(either { Email.from(raw) }, Email.fromEither(raw))
        }
        assertEquals(Either.Left(PositiveInt.Error.NonPositive(0)), PositiveInt.fromEither(0))
    }

    @Test
    fun `fromOrNull gives the value or null`() {
        assertEquals(3, PositiveInt.fromOrNull(3)?.value)
        assertNull(PositiveInt.fromOrNull(-1))
        assertEquals(either { NonBlankString.from("Ann") }.getOrNull(), NonBlankString.fromOrNull("Ann"))
    }

    @Test
    fun `fromOrElse runs recover once and only on failure`() {
        var calls = 0
        val fallback = PositiveInt.fromOrElse(0) { error ->
            calls++
            assertEquals(PositiveInt.Error.NonPositive(0), error)
            PositiveInt.ONE
        }
        assertEquals(PositiveInt.ONE, fallback)
        assertEquals(1, calls)

        val valid = PositiveInt.fromOrElse(7) { error("recover must not run for valid input") }
        assertEquals(7, valid.value)
    }

    @Test
    fun `fromOrElse lets recover return from the enclosing function`() {
        fun quantityOrMessage(raw: Int): String {
            val quantity = PositiveInt.fromOrElse(raw) { return "rejected: $it" }
            return "accepted: ${quantity.value}"
        }
        assertEquals("accepted: 2", quantityOrMessage(2))
        assertEquals("rejected: ${PositiveInt.Error.NonPositive(0)}", quantityOrMessage(0))
    }

    @Test
    fun `a required factory reports a missing value through fromEither`() {
        assertEquals(Either.Left(RequiredError.Missing), Email.required().fromEither(null))
        assertEquals(
            Either.Left(RequiredError.Invalid(Email.Error.InvalidFormat("nope"))),
            Email.required().fromEither("nope"),
        )
    }
}
