package io.liquidsoftware.safedomain.collections

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.NonEmptySet
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

class NonEmptyFactoriesTest {
    @Test
    fun `empty inputs raise each adapter's precise error`() {
        assertEquals(
            Either.Left(NonEmptyError.Empty),
            either<NonEmptyError, NonEmptyList<String>> {
                NonEmptyList.factory<String>().from(emptyList())
            },
        )
        assertEquals(
            Either.Left(NonEmptyError.Empty),
            either<NonEmptyError, NonEmptySet<String>> {
                NonEmptySet.factory<String>().from(emptyList())
            },
        )
    }

    @Test
    fun `list adapter preserves order duplicates and nullable elements`() {
        val result = either { NonEmptyList.factory<String?>().from(listOf("b", null, "b", "a")) }
        val list = assertIs<Either.Right<NonEmptyList<String?>>>(result).value
        assertEquals(listOf("b", null, "b", "a"), list.all)
        assertEquals("b", list.head)
    }

    @Test
    fun `set adapter applies set semantics and preserves first occurrence order`() {
        val result = either { NonEmptySet.factory<String?>().from(listOf("b", null, "b", "a")) }
        val set = assertIs<Either.Right<NonEmptySet<String?>>>(result).value
        assertEquals(listOf("b", null, "a"), set.toList())
    }

    @Test
    fun `list adapter snapshot survives source changes and rejects exposed storage mutation`() {
        val source = mutableListOf("a", "b")
        val result = either { NonEmptyList.factory<String>().from(source) }
        val list = assertIs<Either.Right<NonEmptyList<String>>>(result).value
        source.clear()
        assertEquals(listOf("a", "b"), list.all)
        assertFails { (list.all as MutableList<String>).clear() }
        assertFails { (list.tail as MutableList<String>).clear() }
        assertEquals("a", list.head)
    }

    @Test
    fun `set adapter snapshot survives source changes and rejects exposed storage mutation`() {
        val source = mutableSetOf("a", "b")
        val result = either { NonEmptySet.factory<String>().from(source) }
        val set = assertIs<Either.Right<NonEmptySet<String>>>(result).value
        source.clear()
        assertEquals(setOf("a", "b"), set.toSet())
        assertFails { (set.toSet() as MutableSet<String>).clear() }
        assertEquals("a", set.head)
    }

    @Test
    fun `distinct set factory reports every repeat instead of collapsing it`() {
        val result = either { NonEmptySet.distinctFactory<String>().from(listOf("a", "b", "a", "c", "b", "a")) }
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    DistinctError.Duplicate(0, 2),
                    DistinctError.Duplicate(1, 4),
                    DistinctError.Duplicate(0, 5),
                ),
            ),
            result,
        )
        assertEquals(
            Either.Left(nonEmptyListOf(DistinctError.Empty)),
            either<NonEmptyList<DistinctError>, NonEmptySet<String>> {
                NonEmptySet.distinctFactory<String>().from(emptyList())
            },
        )
        val set = assertIs<Either.Right<NonEmptySet<String>>>(
            either {
                NonEmptySet.distinctFactory<String>().from(listOf("b", "a"))
            },
        ).value
        assertEquals(listOf("b", "a"), set.toList())
    }

    @Test
    fun `mapped adapters translate errors without enumerating branches`() {
        val result: Either<RecipientError, NonEmptyList<String>> = either {
            NonEmptyList.factory<String>().from(emptyList(), onError = RecipientError::InvalidRecipients)
        }
        assertEquals(Either.Left(RecipientError.InvalidRecipients(NonEmptyError.Empty)), result)
    }
}

private sealed interface RecipientError {
    data class InvalidRecipients(val cause: NonEmptyError) : RecipientError
}
