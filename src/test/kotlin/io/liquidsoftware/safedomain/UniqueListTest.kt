package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class UniqueListTest {
    @Test
    fun `reports every repeated position in encounter order against its first occurrence`() {
        val result = either { UniqueList.factory<String>().from(listOf("a", "b", "a", "a", "b")) }
        assertEquals(Either.Left(nonEmptyListOf(
            UniqueList.Error.Duplicate(firstIndex = 0, duplicateIndex = 2),
            UniqueList.Error.Duplicate(firstIndex = 0, duplicateIndex = 3),
            UniqueList.Error.Duplicate(firstIndex = 1, duplicateIndex = 4),
        )), result)
    }

    @Test
    fun `allows empty and singleton inputs and preserves exact element order`() {
        for (input in listOf(emptyList(), listOf("a"), listOf("b", "a", " A "))) {
            val value = assertIs<Either.Right<UniqueList<String>>>(either { UniqueList.factory<String>().from(input) }).value
            assertEquals(input, value.value)
            assertEquals(input.size, value.size)
            assertEquals(input, value.toList())
        }
    }

    @Test
    fun `uses element equality including nullable values`() {
        val result = either { UniqueList.factory<String?>().from(listOf(null, "a", null)) }
        assertEquals(Either.Left(nonEmptyListOf(UniqueList.Error.Duplicate(0, 2))), result)
        val first = assertIs<Either.Right<PositiveDecimal>>(either {
            PositiveDecimal.from(java.math.BigDecimal("1.0"))
        }).value
        val second = assertIs<Either.Right<PositiveDecimal>>(either {
            PositiveDecimal.from(java.math.BigDecimal("1.00"))
        }).value
        assertEquals(Either.Left(nonEmptyListOf(UniqueList.Error.Duplicate(0, 1))), either<NonEmptyList<UniqueList.Error>, UniqueList<PositiveDecimal>> {
            UniqueList.factory<PositiveDecimal>().from(listOf(first, second))
        })
    }

    @Test
    fun `takes an unmodifiable snapshot and has order-sensitive value semantics`() {
        val source = mutableListOf("a", "b")
        val list = assertIs<Either.Right<UniqueList<String>>>(either { UniqueList.factory<String>().from(source) }).value
        val equal = assertIs<Either.Right<UniqueList<String>>>(either { UniqueList.factory<String>().from(source) }).value
        val reversed = assertIs<Either.Right<UniqueList<String>>>(either { UniqueList.factory<String>().from(source.reversed()) }).value
        source.clear()
        assertEquals(listOf("a", "b"), list.value)
        assertFailsWith<UnsupportedOperationException> { (list.value as MutableList<String>).clear() }
        assertEquals(list, equal)
        assertEquals(list.hashCode(), equal.hashCode())
        assertNotEquals(list, reversed)
        assertNotEquals<Any>(list, list.value)
    }

    @Test
    fun `map rejects collisions while filter safely permits an empty result`() {
        val source = assertIs<Either.Right<UniqueList<String>>>(either { UniqueList.factory<String>().from(listOf("a", "b")) }).value
        assertEquals(Either.Left(nonEmptyListOf(UniqueList.Error.Duplicate(0, 1))), either<NonEmptyList<UniqueList.Error>, UniqueList<Int>> { source.map { 1 } })
        val mapped = assertIs<Either.Right<UniqueList<Int>>>(either { source.map { it.single().code } }).value
        assertEquals(listOf(97, 98), mapped.value)
        assertEquals(emptyList(), source.filter { false }.value)
        assertEquals(listOf("a", "b"), source.value)
        assertFailsWith<UnsupportedOperationException> { (mapped.value as MutableList<Int>).clear() }
    }

    @Test
    fun `append validates uniqueness and leaves the original unchanged`() {
        val source = assertIs<Either.Right<UniqueList<String>>>(either { UniqueList.factory<String>().from(listOf("a")) }).value
        assertEquals(Either.Left(nonEmptyListOf(UniqueList.Error.Duplicate(0, 1))), either<NonEmptyList<UniqueList.Error>, UniqueList<String>> { source.append("a") })
        val appended = assertIs<Either.Right<UniqueList<String>>>(either { source.append("b") }).value
        assertEquals(listOf("a", "b"), appended.value)
        assertEquals(listOf("a"), source.value)
    }

    @Test
    fun `maps the complete duplicate batch into one parent error`() {
        val result: Either<UniqueFieldError, UniqueList<Int>> = either {
            UniqueList.factory<Int>().from(listOf(1, 1, 1), onError = ::UniqueFieldError)
        }
        assertEquals(Either.Left(UniqueFieldError(nonEmptyListOf(
            UniqueList.Error.Duplicate(0, 1), UniqueList.Error.Duplicate(0, 2),
        ))), result)
    }
}

private data class UniqueFieldError(val causes: NonEmptyList<UniqueList.Error>)
