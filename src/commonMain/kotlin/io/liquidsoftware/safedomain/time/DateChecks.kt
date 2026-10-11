package io.liquidsoftware.safedomain.time

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Checks of a [LocalDate] against today, read from a [Clock] in a [TimeZone] supplied
 * as context parameters. The factories return the date unchanged: "past" and "future"
 * are relationships to a moment, not properties of the value, so a composite stores a
 * plain [LocalDate] and the check holds as of construction only. Today is read once per
 * validation; supply a fixed clock for deterministic tests.
 */
public object DateChecks {
    public sealed interface Error {
        public data class NotPast(val value: LocalDate, val today: LocalDate) : Error
        public data class NotFuture(val value: LocalDate, val today: LocalDate) : Error
    }

    /** Accepts dates strictly before today. */
    context(clock: Clock, timeZone: TimeZone)
    public fun past(): ValueFactory<LocalDate, Error.NotPast, LocalDate> =
        object : ValueFactory<LocalDate, Error.NotPast, LocalDate> {
            context(raise: Raise<Error.NotPast>)
            override fun from(value: LocalDate): LocalDate {
                val today = clock.todayIn(timeZone)
                raise.ensure(value < today) { Error.NotPast(value, today) }
                return value
            }
        }

    /** Accepts dates strictly after today. */
    context(clock: Clock, timeZone: TimeZone)
    public fun future(): ValueFactory<LocalDate, Error.NotFuture, LocalDate> =
        object : ValueFactory<LocalDate, Error.NotFuture, LocalDate> {
            context(raise: Raise<Error.NotFuture>)
            override fun from(value: LocalDate): LocalDate {
                val today = clock.todayIn(timeZone)
                raise.ensure(value > today) { Error.NotFuture(value, today) }
                return value
            }
        }
}
