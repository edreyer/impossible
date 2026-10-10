package io.liquidsoftware.safedomain.collections

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class BoundedListTest {
    @Test
    fun `bounds are programmer configuration so invalid ranges throw`() {
        for (bounds in listOf(-1..3, 2..1, 0..-1)) {
            assertFailsWith<IllegalArgumentException> { BoundedList.factory<Int>(bounds) }
        }
        val full = assertIs<Either.Right<BoundedList<Int>>>(either { BoundedList.factory<Int>(0..Int.MAX_VALUE).from(emptyList()) }).value
        assertEquals(0..Int.MAX_VALUE, full.bounds)
    }

    @Test
    fun `size boundaries are inclusive and errors retain the actual size`() {
        val factory = BoundedList.factory<Int>(1..2)
        assertEquals(Either.Left(BoundedList.Error.TooSmall(1, 0)), either<BoundedList.Error, BoundedList<Int>> { factory.from(emptyList()) })
        assertEquals(Either.Left(BoundedList.Error.TooLarge(2, 3)), either<BoundedList.Error, BoundedList<Int>> { factory.from(listOf(1, 2, 3)) })
        for (input in listOf(listOf(1), listOf(1, 1))) {
            val value = assertIs<Either.Right<BoundedList<Int>>>(either { factory.from(input) }).value
            assertEquals(input, value.value)
            assertEquals(input.size, value.size)
        }
    }

    @Test
    fun `zero bounds accept exactly an empty list and exact bounds require that size`() {
        val zero = BoundedList.factory<Int>(0..0)
        assertEquals(emptyList(), assertIs<Either.Right<BoundedList<Int>>>(either { zero.from(emptyList()) }).value.value)
        assertEquals(Either.Left(BoundedList.Error.TooLarge(0, 1)), either<BoundedList.Error, BoundedList<Int>> { zero.from(listOf(1)) })
        val exact = BoundedList.factory<Int>(2..2)
        assertIs<Either.Right<BoundedList<Int>>>(either { exact.from(listOf(1, 2)) })
        assertEquals(Either.Left(BoundedList.Error.TooSmall(2, 1)), either<BoundedList.Error, BoundedList<Int>> { exact.from(listOf(1)) })
    }

    @Test
    fun `snapshot preserves order nulls and duplicates and cannot be mutated`() {
        val source = mutableListOf<String?>("b", null, "b")
        val value = assertIs<Either.Right<BoundedList<String?>>>(either {
            BoundedList.factory<String?>(1..3).from(source)
        }).value
        source.clear()
        assertEquals(listOf("b", null, "b"), value.value)
        assertFails { (value.value as MutableList<String?>).clear() }
    }

    @Test
    fun `value semantics include bounds because bounds determine future operations`() {
        val first = assertIs<Either.Right<BoundedList<Int>>>(either { BoundedList.factory<Int>(1..2).from(listOf(1)) }).value
        val equal = assertIs<Either.Right<BoundedList<Int>>>(either { BoundedList.factory<Int>(1..2).from(listOf(1)) }).value
        val differentBounds = assertIs<Either.Right<BoundedList<Int>>>(either { BoundedList.factory<Int>(0..3).from(listOf(1)) }).value
        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, differentBounds)
        assertNotEquals<Any>(first, first.value)
    }

    @Test
    fun `map preserves bounds without validation and returns an unmodifiable result`() {
        val source = assertIs<Either.Right<BoundedList<String>>>(either { BoundedList.factory<String>(2..2).from(listOf("a", "b")) }).value
        val mapped = source.map { it.length }
        assertEquals(listOf(1, 1), mapped.value)
        assertEquals(source.bounds, mapped.bounds)
        assertFails { (mapped.value as MutableList<Int>).clear() }
    }

    @Test
    fun `filter enforces minimum and append enforces maximum without mutating the original`() {
        val source = assertIs<Either.Right<BoundedList<Int>>>(either { BoundedList.factory<Int>(1..2).from(listOf(1, 2)) }).value
        assertEquals(Either.Left(BoundedList.Error.TooSmall(1, 0)), either<BoundedList.Error, BoundedList<Int>> { source.filter { false } })
        assertEquals(Either.Left(BoundedList.Error.TooLarge(2, 3)), either<BoundedList.Error, BoundedList<Int>> { source.append(3) })
        val filtered = assertIs<Either.Right<BoundedList<Int>>>(either { source.filter { it == 2 } }).value
        val appended = assertIs<Either.Right<BoundedList<Int>>>(either { filtered.append(3) }).value
        assertEquals(listOf(2, 3), appended.value)
        assertEquals(source.bounds, appended.bounds)
        assertEquals(listOf(1, 2), source.value)
    }

    @Test
    fun `mapped factory retains precise size errors`() {
        val result: Either<BoundedFieldError, BoundedList<Int>> = either {
            BoundedList.factory<Int>(1..2).from(emptyList(), onError = ::BoundedFieldError)
        }
        assertEquals(Either.Left(BoundedFieldError(BoundedList.Error.TooSmall(1, 0))), result)
    }
}

private data class BoundedFieldError(val cause: BoundedList.Error)
