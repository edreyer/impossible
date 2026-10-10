package io.liquidsoftware.safedomain.time

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class DateTypesTest {
    private val today = LocalDate(2026, 10, 5)

    // The extreme dates every platform supports; kotlinx-datetime keeps its own bounds internal.
    private val earliest = LocalDate(-999_999, 1, 1)
    private val latest = LocalDate(999_999, 12, 31)

    @Test
    fun `future dates are strictly after an explicit reference date`() {
        for (date in listOf(today.minus(DatePeriod(days = 1)), today)) {
            assertEquals(Either.Left(FutureDate.Error.NotFuture(date, today)),
                either<FutureDate.Error, FutureDate> { FutureDate.from(FutureDate.Input(date, today)) })
        }
        val future = assertIs<Either.Right<FutureDate>>(either {
            FutureDate.from(FutureDate.Input(today.plus(DatePeriod(days = 1)), today))
        }).value
        assertEquals(today.plus(DatePeriod(days = 1)), future.value)
        assertEquals(today, future.asOf)
    }

    @Test
    fun `past dates are strictly before an explicit reference date`() {
        for (date in listOf(today, today.plus(DatePeriod(days = 1)))) {
            assertEquals(Either.Left(PastDate.Error.NotPast(date, today)),
                either<PastDate.Error, PastDate> { PastDate.from(PastDate.Input(date, today)) })
        }
        val past = assertIs<Either.Right<PastDate>>(either {
            PastDate.from(PastDate.Input(today.minus(DatePeriod(days = 1)), today))
        }).value
        assertEquals(today.minus(DatePeriod(days = 1)), past.value)
        assertEquals(today, past.asOf)
    }

    @Test
    fun `relative dates support extreme dates without date arithmetic`() {
        val result = either {
            assertEquals(latest, FutureDate.from(FutureDate.Input(latest, earliest)).value)
            assertEquals(earliest, PastDate.from(PastDate.Input(earliest, latest)).value)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `relative date equality includes the validation reference date`() {
        val result = either {
            val future = FutureDate.from(FutureDate.Input(today.plus(DatePeriod(days = 1)), today))
            val same = FutureDate.from(FutureDate.Input(today.plus(DatePeriod(days = 1)), today))
            val earlierReference = FutureDate.from(FutureDate.Input(today.plus(DatePeriod(days = 1)), today.minus(DatePeriod(days = 1))))
            assertEquals(2, setOf(future, same, earlierReference).size)
            val past = PastDate.from(PastDate.Input(today.minus(DatePeriod(days = 1)), today))
            assertEquals(past, PastDate.from(PastDate.Input(today.minus(DatePeriod(days = 1)), today)))
            assertEquals(past.hashCode(), PastDate.from(PastDate.Input(today.minus(DatePeriod(days = 1)), today)).hashCode())
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `range rejects reversed endpoints with a precise relationship failure`() {
        assertEquals(Either.Left(DateRange.Error.EndBeforeStart(today, today.minus(DatePeriod(days = 1)))),
            either<DateRange.Error, DateRange> { DateRange.from(DateRange.Input(today, today.minus(DatePeriod(days = 1)))) })
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
        assertEquals(Either.Left(InvalidPeriod(DateRange.Error.EndBeforeStart(today, today.minus(DatePeriod(days = 1))))),
            either<InvalidPeriod, DateRange> { DateRange.from(DateRange.Input(today, today.minus(DatePeriod(days = 1))), onError = ::InvalidPeriod) })
    }
}
