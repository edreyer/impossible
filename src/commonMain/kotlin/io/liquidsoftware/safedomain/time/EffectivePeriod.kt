package io.liquidsoftware.safedomain.time

import kotlinx.datetime.LocalDate

/**
 * When something applies: from a date onward, or within a closed [DateRange]. A closed
 * set of states instead of a range with a nullable end, so "no end date" is explicit.
 */
public sealed interface EffectivePeriod {
    public val start: LocalDate

    public operator fun contains(date: LocalDate): Boolean

    public fun overlaps(other: EffectivePeriod): Boolean

    /** Applies on [start] and every later date. */
    public data class OpenEnded(override val start: LocalDate) : EffectivePeriod {
        override fun contains(date: LocalDate): Boolean = date >= start

        override fun overlaps(other: EffectivePeriod): Boolean = when (other) {
            is OpenEnded -> true
            is Bounded -> other.range.endInclusive >= start
        }
    }

    /** Applies within [range], both endpoints included. */
    public data class Bounded(val range: DateRange) : EffectivePeriod {
        override val start: LocalDate get() = range.start

        override fun contains(date: LocalDate): Boolean = date in range

        override fun overlaps(other: EffectivePeriod): Boolean = when (other) {
            is OpenEnded -> other.overlaps(this)
            is Bounded -> range.overlaps(other.range)
        }
    }
}
