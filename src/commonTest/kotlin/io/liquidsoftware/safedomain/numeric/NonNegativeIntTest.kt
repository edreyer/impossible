package io.liquidsoftware.safedomain.numeric

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NonNegativeIntTest {
    @Test
    fun `rejects negative integers with their rejected value`() {
        for (input in listOf(-1, -42, Int.MIN_VALUE)) {
            val result = either { NonNegativeInt.from(input) }

            assertEquals(Either.Left(NonNegativeInt.Error.Negative(input)), result)
        }
    }

    @Test
    fun `accepts zero and the full positive Int range`() {
        for (input in listOf(0, 1, 42, Int.MAX_VALUE)) {
            val result = either { NonNegativeInt.from(input) }

            assertEquals(input, assertIs<Either.Right<NonNegativeInt>>(result).value.value)
        }
    }

    @Test
    fun `uses the integer value for collection keys`() {
        val result = either {
            val first = NonNegativeInt.from(0)
            val second = NonNegativeInt.from(0)

            assertEquals(2, setOf(first, second, NonNegativeInt.from(1)).size)
            assertEquals("count", mapOf(first to "count")[second])
        }

        assertEquals(Either.Right(Unit), result)
    }
}
