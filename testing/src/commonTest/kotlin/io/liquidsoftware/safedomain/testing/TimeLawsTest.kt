package io.liquidsoftware.safedomain.testing

import io.kotest.property.arbitrary.arbitrary
import io.liquidsoftware.safedomain.time.DateChecks
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.PositiveDuration
import io.liquidsoftware.safedomain.time.TimeWindow
import io.liquidsoftware.safedomain.fromTrusted
import io.kotest.property.Arb
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Ranges and windows keep their ordering invariant and overlap consistently. */
class TimeLawsTest {
    @Test
    fun `a date range contains exactly its days`() = runTest {
        forAll(DateRange.arb(maxDays = 400)) { range ->
            val dates = range.dates().toList()
            assertEquals(range.days, dates.size.toLong())
            assertEquals(range.start, dates.first())
            assertEquals(range.endInclusive, dates.last())
            assertTrue(range.start in range && range.endInclusive in range)
            assertFalse(range.start.plus(DatePeriod(days = -1)) in range)
        }
    }

    @Test
    fun `date range overlap is symmetric and closed at both ends`() = runTest {
        forAll(arbitrary { DateRange.arb(maxDays = 60).bind() to DateRange.arb(maxDays = 60).bind() }) { (a, b) ->
            assertEquals(a.overlaps(b), b.overlaps(a))
            assertTrue(a.overlaps(a))
            val touching = DateRange.fromTrusted(DateRange.Input(a.endInclusive, a.endInclusive.plus(DatePeriod(days = 1))))
            val adjacent = DateRange.fromTrusted(DateRange.Input(a.endInclusive.plus(DatePeriod(days = 1)), b.endInclusive.plus(DatePeriod(days = 400_000))))
            assertTrue(a.overlaps(touching))
            assertFalse(a.overlaps(adjacent))
        }
    }

    @Test
    fun `time windows are half open so consecutive windows tile`() = runTest {
        forAll(arbitrary { TimeWindow.arb().bind() to PositiveDuration.arb().bind() }) { (window, length) ->
            assertTrue(window.duration.isPositive())
            assertTrue(window.start in window)
            assertFalse(window.endExclusive in window)
            val next = TimeWindow.starting(window.endExclusive, length)
            assertFalse(window.overlaps(next))
            assertFalse(next.overlaps(window))
            assertTrue(window.overlaps(window))
        }
    }

    @Test
    fun `past and future checks split dates around today`() = runTest {
        forAll(arbitrary { Arb.kotlinxLocalDate().bind() to Arb.kotlinxLocalDate().bind() }) { (today, date) ->
            val clock = object : Clock {
                override fun now(): Instant = today.atStartOfDayIn(TimeZone.UTC)
            }
            with(clock) {
                with(TimeZone.UTC) {
                    assertEquals(date < today, attempt { DateChecks.past().from(date) }.isRight())
                    assertEquals(date > today, attempt { DateChecks.future().from(date) }.isRight())
                }
            }
        }
    }
}
