package io.liquidsoftware.safedomain.time

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DateTypesTest {
    private val today = LocalDate(2026, 10, 5)

    // The extreme dates every platform supports; kotlinx-datetime keeps its own bounds internal.
    private val earliest = LocalDate(-999_999, 1, 1)
    private val latest = LocalDate(999_999, 12, 31)

    @Test
    fun `range rejects reversed endpoints with a precise relationship failure`() {
        assertEquals(
            Either.Left(DateRange.Error.EndBeforeStart(today, today.minus(DatePeriod(days = 1)))),
            either<DateRange.Error, DateRange> {
                DateRange.from(DateRange.Input(today, today.minus(DatePeriod(days = 1))))
            },
        )
    }

    @Test
    fun `range includes both endpoints and allows a single day`() {
        val result = either {
            val range = DateRange.from(DateRange.Input(today, today.plus(DatePeriod(days = 2))))
            assertTrue(today in range)
            assertTrue(today.plus(DatePeriod(days = 1)) in range)
            assertTrue(today.plus(DatePeriod(days = 2)) in range)
            assertFalse(today.minus(DatePeriod(days = 1)) in range)
            assertFalse(today.plus(DatePeriod(days = 3)) in range)
            val oneDay = DateRange.from(DateRange.Input(today, today))
            assertTrue(today in oneDay)
            assertEquals(oneDay, DateRange.from(DateRange.Input(today, today)))
            assertEquals(1, setOf(oneDay, DateRange.from(DateRange.Input(today, today))).size)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `ranges overlap on a shared endpoint and not on adjacent days`() {
        val result = either {
            val range = DateRange.from(DateRange.Input(today, today.plus(DatePeriod(days = 2))))
            val touching = DateRange.from(DateRange.Input(today.plus(DatePeriod(days = 2)), today.plus(DatePeriod(days = 4))))
            val separate = DateRange.from(DateRange.Input(today.plus(DatePeriod(days = 3)), today.plus(DatePeriod(days = 4))))
            assertTrue(range.overlaps(touching))
            assertTrue(touching.overlaps(range))
            assertFalse(range.overlaps(separate))
            val allDates = DateRange.from(DateRange.Input(earliest, latest))
            assertTrue(earliest in allDates)
            assertTrue(latest in allDates)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `date relationship failures can be mapped without matching error branches`() {
        data class InvalidPeriod(val cause: DateRange.Error)
        assertEquals(
            Either.Left(InvalidPeriod(DateRange.Error.EndBeforeStart(today, today.minus(DatePeriod(days = 1))))),
            either<InvalidPeriod, DateRange> {
                DateRange.from(
                    DateRange.Input(today, today.minus(DatePeriod(days = 1))),
                    onError = ::InvalidPeriod,
                )
            },
        )
    }

    @Test
    fun `range counts and lists its dates including both endpoints`() {
        val result = either {
            val range = DateRange.from(DateRange.Input(LocalDate(2026, 2, 27), LocalDate(2026, 3, 2)))
            assertEquals(4L, range.days)
            assertEquals(
                listOf(LocalDate(2026, 2, 27), LocalDate(2026, 2, 28), LocalDate(2026, 3, 1), LocalDate(2026, 3, 2)),
                range.dates().toList(),
            )
            val oneDay = DateRange.from(DateRange.Input(today, today))
            assertEquals(1L, oneDay.days)
            assertEquals(listOf(today), oneDay.dates().toList())
            assertEquals(latest, DateRange.from(DateRange.Input(latest, latest)).dates().single())
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `effective periods make the missing end date an explicit state`() {
        val result = either {
            val from = EffectivePeriod.OpenEnded(today)
            val bounded = EffectivePeriod.Bounded(DateRange.from(DateRange.Input(today, today.plus(DatePeriod(days = 2)))))
            val before = EffectivePeriod.Bounded(DateRange.from(DateRange.Input(earliest, today.minus(DatePeriod(days = 1)))))
            assertTrue(latest in from)
            assertFalse(today.minus(DatePeriod(days = 1)) in from)
            assertFalse(today.plus(DatePeriod(days = 3)) in bounded)
            assertTrue(from.overlaps(bounded))
            assertTrue(bounded.overlaps(from))
            assertFalse(from.overlaps(before))
            assertFalse(before.overlaps(from))
            assertTrue(from.overlaps(EffectivePeriod.OpenEnded(latest)))
            assertEquals(today, bounded.start)
        }
        assertEquals(Either.Right(Unit), result)
    }
}
