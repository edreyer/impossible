package io.liquidsoftware.safedomain.testing

import arrow.core.Either
import arrow.core.NonEmptyList
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.collections.NonEmptyError
import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.collections.factory
import io.liquidsoftware.safedomain.geo.GeoPoint
import io.kotest.property.arbitrary.double
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Collection invariants survive their operations, and validation reports every failure. */
class CollectionLawsTest {
    private val bounds = 2..6

    @Test
    fun `bounded lists stay within bounds or fail precisely`() = runTest {
        forAll(arbitrary { BoundedList.arb(Arb.int(0..9), bounds).bind() to Arb.int(0..9).bind() }) { (list, element) ->
            assertTrue(list.size in bounds)
            assertEquals(list.size, list.map { it * 2 }.size)
            val appended = attempt { list.append(element) }
            if (list.size == bounds.last) {
                assertEquals(Either.Left(BoundedList.Error.TooLarge(bounds.last, list.size + 1)), appended)
            } else {
                assertEquals(list.value + element, appended.getOrNull()?.value)
            }
            val kept = list.value.count { it >= element }
            assertEquals(kept >= bounds.first, attempt { list.filter { it >= element } }.isRight())
        }
    }

    @Test
    fun `unique lists stay unique and report every duplicate`() = runTest {
        forAll(UniqueList.arb(Arb.int(0..99), 0..10)) { list ->
            assertEquals(list.value.distinct(), list.value)
            assertEquals(list.value.filter { it % 2 == 0 }, list.filter { it % 2 == 0 }.value)
            list.value.forEach { existing ->
                assertEquals(
                    Either.Left(NonEmptyList.of(UniqueList.Error.Duplicate(list.value.indexOf(existing), list.size))),
                    attempt<NonEmptyList<UniqueList.Error>, UniqueList<Int>> { list.append(existing) },
                )
            }
        }
        forAll(Arb.list(Arb.int(0..5), 0..12)) { raw ->
            val result = attempt { UniqueList.factory<Int>().from(raw) }
            val duplicates = raw.size - raw.distinct().size
            assertEquals(duplicates, result.leftOrNull()?.size ?: 0)
        }
    }

    @Test
    fun `non empty lists accept exactly the non empty inputs`() = runTest {
        forAll(Arb.list(Arb.int(), 0..5)) { raw ->
            val result = attempt<NonEmptyError, NonEmptyList<Int>> { NonEmptyList.factory<Int>().from(raw) }
            if (raw.isEmpty()) assertEquals(Either.Left(NonEmptyError.Empty), result) else assertEquals<List<Int>?>(raw, result.getOrNull())
        }
    }

    @Test
    fun `validation reports one error per invalid coordinate`() = runTest {
        val coordinates = Arb.double(-400.0..400.0)
        forAll(arbitrary { coordinates.bind() to coordinates.bind() }) { (latitude, longitude) ->
            val invalid = listOf(latitude !in -90.0..90.0, longitude !in -180.0..180.0).count { it }
            val result = attempt { GeoPoint.from(GeoPoint.Input(latitude, longitude)) }
            assertEquals(invalid, result.leftOrNull()?.size ?: 0)
        }
    }
}
