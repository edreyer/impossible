package io.liquidsoftware.safedomain.numeric

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PositiveIntTest {
    @Test
    fun `rejects zero and negative integers with their rejected value`() {
        for (input in listOf(0, -1, Int.MIN_VALUE)) {
            val result = either { PositiveInt.from(input) }

            assertEquals(Either.Left(PositiveInt.Error.NonPositive(input)), result)
        }
    }

    @Test
    fun `accepts the full positive Int range`() {
        for (input in listOf(1, 42, Int.MAX_VALUE)) {
            val result = either { PositiveInt.from(input) }

            assertEquals(input, assertIs<Either.Right<PositiveInt>>(result).value.value)
        }
    }

    @Test
    fun `uses the integer value for collection keys`() {
        val result = either {
            val first = PositiveInt.from(42)
            val second = PositiveInt.from(42)

            assertEquals(2, setOf(first, second, PositiveInt.from(43)).size)
            assertEquals("quantity", mapOf(first to "quantity")[second])
        }

        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `composes in a named Raise context and stops at the first failure`() {
        val visited = mutableListOf<Int>()

        context(raise: Raise<PositiveInt.Error>)
        fun quantity(input: Int): PositiveInt {
            visited.add(input)
            return PositiveInt.from(input)
        }

        val result: Either<PositiveInt.Error, List<PositiveInt>> = either {
            listOf(quantity(1), quantity(0), quantity(2))
        }

        assertEquals(Either.Left(PositiveInt.Error.NonPositive(0)), result)
        assertEquals(listOf(1, 0), visited)
    }
}
