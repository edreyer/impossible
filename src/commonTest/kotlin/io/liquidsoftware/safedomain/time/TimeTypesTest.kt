package io.liquidsoftware.safedomain.time

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class TimeTypesTest {
    private val nine = Instant.parse("2026-10-05T09:00:00Z")
    private val ten = Instant.parse("2026-10-05T10:00:00Z")
    private val eleven = Instant.parse("2026-10-05T11:00:00Z")

    @Test
    fun `time windows are half open and never empty`() {
        assertEquals(Either.Left(TimeWindow.Error.NotAfterStart(ten, ten)),
            either<TimeWindow.Error, TimeWindow> { TimeWindow.from(TimeWindow.Input(ten, ten)) })
        assertEquals(Either.Left(TimeWindow.Error.NotAfterStart(ten, nine)),
            either<TimeWindow.Error, TimeWindow> { TimeWindow.from(TimeWindow.Input(ten, nine)) })
        val window = assertIs<Either.Right<TimeWindow>>(either { TimeWindow.from(TimeWindow.Input(nine, ten)) }).value
        assertTrue(nine in window)
        assertFalse(ten in window)
        assertEquals(1.hours, window.duration)
    }

    @Test
    fun `adjacent windows do not overlap and equality uses both instants`() {
        val result = either {
            val first = TimeWindow.from(TimeWindow.Input(nine, ten))
            val second = TimeWindow.from(TimeWindow.Input(ten, eleven))
            val spanning = TimeWindow.from(TimeWindow.Input(nine, eleven))
            assertFalse(first.overlaps(second))
            assertTrue(spanning.overlaps(second))
            assertEquals(first, TimeWindow.from(TimeWindow.Input(nine, ten)))
            assertEquals(spanning, TimeWindow.starting(nine, PositiveDuration.from(2.hours)))
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `positive durations reject zero negative and infinite durations`() {
        for (value in listOf(Duration.ZERO, (-1).nanoseconds, -Duration.INFINITE)) {
            assertEquals(Either.Left(PositiveDuration.Error.NonPositive(value)),
                either<PositiveDuration.Error, PositiveDuration> { PositiveDuration.from(value) })
        }
        assertEquals(Either.Left(PositiveDuration.Error.Infinite),
            either<PositiveDuration.Error, PositiveDuration> { PositiveDuration.from(Duration.INFINITE) })
        assertEquals(1.nanoseconds, assertIs<Either.Right<PositiveDuration>>(either { PositiveDuration.from(1.nanoseconds) }).value.value)
        val result = either { assertTrue(PositiveDuration.from(1.minutes) < PositiveDuration.from(1.hours)) }
        assertEquals(Either.Right(Unit), result)
    }
}
