package io.liquidsoftware.safedomain.time

import arrow.core.Either
import arrow.core.raise.either
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DateTypesTest {
    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun `future dates are strictly after an explicit reference date`() {
        for (date in listOf(today.minusDays(1), today)) {
            assertEquals(Either.Left(FutureDate.Error.NotFuture(date, today)),
                either<FutureDate.Error, FutureDate> { FutureDate.from(FutureDate.Input(date, today)) })
        }
        val future = assertIs<Either.Right<FutureDate>>(either {
            FutureDate.from(FutureDate.Input(today.plusDays(1), today))
        }).value
        assertEquals(today.plusDays(1), future.value)
        assertEquals(today, future.asOf)
    }

    @Test
    fun `past dates are strictly before an explicit reference date`() {
        for (date in listOf(today, today.plusDays(1))) {
            assertEquals(Either.Left(PastDate.Error.NotPast(date, today)),
                either<PastDate.Error, PastDate> { PastDate.from(PastDate.Input(date, today)) })
        }
        val past = assertIs<Either.Right<PastDate>>(either {
            PastDate.from(PastDate.Input(today.minusDays(1), today))
        }).value
        assertEquals(today.minusDays(1), past.value)
        assertEquals(today, past.asOf)
    }

    @Test
    fun `relative dates support extreme dates without date arithmetic`() {
        val result = either {
            assertEquals(LocalDate.MAX, FutureDate.from(FutureDate.Input(LocalDate.MAX, LocalDate.MIN)).value)
            assertEquals(LocalDate.MIN, PastDate.from(PastDate.Input(LocalDate.MIN, LocalDate.MAX)).value)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `relative date equality includes the validation reference date`() {
        val result = either {
            val future = FutureDate.from(FutureDate.Input(today.plusDays(1), today))
            val same = FutureDate.from(FutureDate.Input(today.plusDays(1), today))
            val earlierReference = FutureDate.from(FutureDate.Input(today.plusDays(1), today.minusDays(1)))
            assertEquals(2, setOf(future, same, earlierReference).size)
            val past = PastDate.from(PastDate.Input(today.minusDays(1), today))
            assertEquals(past, PastDate.from(PastDate.Input(today.minusDays(1), today)))
            assertEquals(past.hashCode(), PastDate.from(PastDate.Input(today.minusDays(1), today)).hashCode())
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `range rejects reversed endpoints with a precise relationship failure`() {
        assertEquals(Either.Left(DateRange.Error.EndBeforeStart(today, today.minusDays(1))),
            either<DateRange.Error, DateRange> { DateRange.from(DateRange.Input(today, today.minusDays(1))) })
    }

    @Test
    fun `range includes both endpoints and allows a single day`() {
        val result = either {
            val range = DateRange.from(DateRange.Input(today, today.plusDays(2)))
            assertTrue(today in range)
            assertTrue(today.plusDays(1) in range)
            assertTrue(today.plusDays(2) in range)
            assertFalse(today.minusDays(1) in range)
            assertFalse(today.plusDays(3) in range)
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
            val range = DateRange.from(DateRange.Input(today, today.plusDays(2)))
            val touching = DateRange.from(DateRange.Input(today.plusDays(2), today.plusDays(4)))
            val separate = DateRange.from(DateRange.Input(today.plusDays(3), today.plusDays(4)))
            assertTrue(range.overlaps(touching))
            assertTrue(touching.overlaps(range))
            assertFalse(range.overlaps(separate))
            val allDates = DateRange.from(DateRange.Input(LocalDate.MIN, LocalDate.MAX))
            assertTrue(LocalDate.MIN in allDates)
            assertTrue(LocalDate.MAX in allDates)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `date relationship failures can be mapped without matching error branches`() {
        data class InvalidPeriod(val cause: DateRange.Error)
        assertEquals(Either.Left(InvalidPeriod(DateRange.Error.EndBeforeStart(today, today.minusDays(1)))),
            either<InvalidPeriod, DateRange> { DateRange.from(DateRange.Input(today, today.minusDays(1)), onError = ::InvalidPeriod) })
    }
}
