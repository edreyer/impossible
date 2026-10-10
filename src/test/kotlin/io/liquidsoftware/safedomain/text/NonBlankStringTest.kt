package io.liquidsoftware.safedomain.text

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class NonBlankStringTest {
    @Test
    fun `rejects empty strings with a typed error`() {
        val result = either { NonBlankString.from("") }

        assertEquals(Either.Left(NonBlankString.Error.Blank), result)
    }

    @Test
    fun `rejects whitespace including Unicode spaces`() {
        for (input in listOf(" ", "\t\r\n", "\u00A0", "\u2003", "\u202F")) {
            val result = either { NonBlankString.from(input) }

            assertEquals(Either.Left(NonBlankString.Error.Blank), result, "Input: $input")
        }
    }

    @Test
    fun `preserves nonblank text without trimming or normalization`() {
        for (input in listOf("a", "  Alice\t", "cafe\u0301", "東京", "\u200B")) {
            val result = either { NonBlankString.from(input) }

            assertEquals(input, assertIs<Either.Right<NonBlankString>>(result).value.value)
        }
    }

    @Test
    fun `uses exact text for equality and collection keys`() {
        val result = either {
            val first = NonBlankString.from("Alice")
            val second = NonBlankString.from("Alice")

            assertEquals(first, second)
            assertEquals("customer", mapOf(first to "customer")[second])
            assertNotEquals(first, NonBlankString.from("alice"))
            assertNotEquals(first, NonBlankString.from(" Alice "))
        }

        assertEquals(Either.Right(Unit), result)
    }
}
