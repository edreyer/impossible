package io.liquidsoftware.safedomain.time

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class DateChecksTest {
    // 2026-10-05T23:30Z is still Oct 5 in UTC but already Oct 6 in Berlin (UTC+2).
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-05T23:30:00Z")
    }
    private val today = LocalDate(2026, 10, 5)

    @Test
    fun `past and future are strict and compare with today from the context clock`() {
        with(clock) {
            with(TimeZone.UTC) {
                assertEquals(Either.Right(LocalDate(2026, 10, 4)), either<DateChecks.Error.NotPast, LocalDate> { DateChecks.past().from(LocalDate(2026, 10, 4)) })
                assertEquals(Either.Left(DateChecks.Error.NotPast(today, today)), either<DateChecks.Error.NotPast, LocalDate> { DateChecks.past().from(today) })
                assertEquals(Either.Right(LocalDate(2026, 10, 6)), either<DateChecks.Error.NotFuture, LocalDate> { DateChecks.future().from(LocalDate(2026, 10, 6)) })
                assertEquals(Either.Left(DateChecks.Error.NotFuture(today, today)), either<DateChecks.Error.NotFuture, LocalDate> { DateChecks.future().from(today) })
            }
        }
    }

    @Test
    fun `today depends on the context time zone`() {
        with(clock) {
            with(TimeZone.of("+02:00")) {
                val berlinToday = LocalDate(2026, 10, 6)
                assertEquals(Either.Right(today), either<DateChecks.Error.NotPast, LocalDate> { DateChecks.past().from(today) })
                assertEquals(Either.Left(DateChecks.Error.NotFuture(berlinToday, berlinToday)),
                    either<DateChecks.Error.NotFuture, LocalDate> { DateChecks.future().from(berlinToday) })
            }
        }
    }

    @Test
    fun `a composite stores a plain date and accumulates check failures`() {
        with(clock) {
            with(TimeZone.UTC) {
                val result = either { Trip.from(TripInput(born = today, departs = today)) }
                assertEquals(Either.Left(nonEmptyListOf(
                    Trip.Error.InvalidBirthDate(DateChecks.Error.NotPast(today, today)),
                    Trip.Error.InvalidDeparture(DateChecks.Error.NotFuture(today, today)),
                )), result)
                val trip = either { Trip.from(TripInput(born = LocalDate(1990, 1, 1), departs = LocalDate(2027, 1, 1))) }
                assertEquals(Either.Right(Trip(LocalDate(1990, 1, 1), LocalDate(2027, 1, 1))), trip)
            }
        }
    }

    private data class TripInput(val born: LocalDate, val departs: LocalDate)

    private data class Trip(val born: LocalDate, val departs: LocalDate) {
        sealed interface Error {
            data class InvalidBirthDate(val cause: DateChecks.Error.NotPast) : Error
            data class InvalidDeparture(val cause: DateChecks.Error.NotFuture) : Error
        }

        companion object {
            context(clock: Clock, timeZone: TimeZone, raise: Raise<NonEmptyList<Error>>)
            fun from(input: TripInput): Trip = validate {
                val born by DateChecks.past().field(input.born, Error::InvalidBirthDate)
                val departs by DateChecks.future().field(input.departs, Error::InvalidDeparture)
                Trip(born, departs)
            }
        }
    }
}
