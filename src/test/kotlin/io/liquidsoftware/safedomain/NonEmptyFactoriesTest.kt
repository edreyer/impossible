package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NonEmptyFactoriesTest {
    @Test
    fun `empty inputs raise each adapter's precise error`() {
        assertEquals(Either.Left(NonEmptyLists.Error.Empty), either<NonEmptyLists.Error, arrow.core.NonEmptyList<String>> {
            NonEmptyLists.factory<String>().from(emptyList())
        })
        assertEquals(Either.Left(NonEmptySets.Error.Empty), either<NonEmptySets.Error, arrow.core.NonEmptySet<String>> {
            NonEmptySets.factory<String>().from(emptyList())
        })
    }

    @Test
    fun `list adapter preserves order duplicates and nullable elements`() {
        val result = either { NonEmptyLists.factory<String?>().from(listOf("b", null, "b", "a")) }
        val list = assertIs<Either.Right<arrow.core.NonEmptyList<String?>>>(result).value
        assertEquals(listOf("b", null, "b", "a"), list.all)
        assertEquals("b", list.head)
    }

    @Test
    fun `set adapter applies set semantics and preserves first occurrence order`() {
        val result = either { NonEmptySets.factory<String?>().from(listOf("b", null, "b", "a")) }
        val set = assertIs<Either.Right<arrow.core.NonEmptySet<String?>>>(result).value
        assertEquals(listOf("b", null, "a"), set.toList())
    }

    @Test
    fun `list adapter snapshot survives source changes and rejects exposed storage mutation`() {
        val source = mutableListOf("a", "b")
        val result = either { NonEmptyLists.factory<String>().from(source) }
        val list = assertIs<Either.Right<arrow.core.NonEmptyList<String>>>(result).value
        source.clear()
        assertEquals(listOf("a", "b"), list.all)
        assertFailsWith<UnsupportedOperationException> { (list.all as MutableList<String>).clear() }
        assertFailsWith<UnsupportedOperationException> { (list.tail as MutableList<String>).clear() }
        assertEquals("a", list.head)
    }

    @Test
    fun `set adapter snapshot survives source changes and rejects exposed storage mutation`() {
        val source = mutableSetOf("a", "b")
        val result = either { NonEmptySets.factory<String>().from(source) }
        val set = assertIs<Either.Right<arrow.core.NonEmptySet<String>>>(result).value
        source.clear()
        assertEquals(setOf("a", "b"), set.toSet())
        assertFailsWith<UnsupportedOperationException> { (set.toSet() as MutableSet<String>).clear() }
        assertEquals("a", set.head)
    }

    @Test
    fun `mapped adapters translate errors without enumerating branches`() {
        val result: Either<RecipientError, arrow.core.NonEmptyList<String>> = either {
            NonEmptyLists.factory<String>().from(emptyList(), onError = RecipientError::InvalidRecipients)
        }
        assertEquals(Either.Left(RecipientError.InvalidRecipients(NonEmptyLists.Error.Empty)), result)
    }
}

private sealed interface RecipientError {
    data class InvalidRecipients(val cause: NonEmptyLists.Error) : RecipientError
}
