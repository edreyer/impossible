package io.liquidsoftware.safedomain.time

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** A closed range with [start] <= [endInclusive]. Both endpoints are included. */
class DateRange private constructor(val start: LocalDate, val endInclusive: LocalDate) {
    data class Input(val start: LocalDate, val endInclusive: LocalDate)

    sealed interface Error {
        data class EndBeforeStart(val start: LocalDate, val endInclusive: LocalDate) : Error
    }

    operator fun contains(date: LocalDate): Boolean = date >= start && date <= endInclusive

    fun overlaps(other: DateRange): Boolean = start <= other.endInclusive && other.start <= endInclusive

    /** The number of dates in the range, counting both endpoints; at least 1. */
    val days: Long get() = start.daysUntil(endInclusive).toLong() + 1

    /** Every date from [start] to [endInclusive], in order. */
    fun dates(): Sequence<LocalDate> =
        generateSequence(start) { date -> if (date < endInclusive) date.plus(1, DateTimeUnit.DAY) else null }

    override fun equals(other: Any?): Boolean =
        other is DateRange && start == other.start && endInclusive == other.endInclusive

    override fun hashCode(): Int = 31 * start.hashCode() + endInclusive.hashCode()

    override fun toString(): String = "DateRange(start=$start, endInclusive=$endInclusive)"

    companion object : ValueFactory<Input, Error, DateRange> {
        context(raise: Raise<Error>)
        override fun from(value: Input): DateRange {
            raise.ensure(value.start <= value.endInclusive) {
                Error.EndBeforeStart(value.start, value.endInclusive)
            }
            return DateRange(value.start, value.endInclusive)
        }
    }
}
