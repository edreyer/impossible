package io.liquidsoftware.safedomain.time

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A half-open interval of instants, [start] inclusive and [endExclusive] exclusive, with
 * [start] strictly before [endExclusive], so a window is never empty. Half-open windows
 * tile without overlap: a booking ending at 10:00 does not overlap one starting at 10:00.
 */
public class TimeWindow private constructor(public val start: Instant, public val endExclusive: Instant) {
    public data class Input(val start: Instant, val endExclusive: Instant)

    public sealed interface Error {
        public data class NotAfterStart(val start: Instant, val endExclusive: Instant) : Error
    }

    /** Always positive. */
    public val duration: Duration get() = endExclusive - start

    public operator fun contains(instant: Instant): Boolean = instant >= start && instant < endExclusive

    public fun overlaps(other: TimeWindow): Boolean = start < other.endExclusive && other.start < endExclusive

    override fun equals(other: Any?): Boolean =
        other is TimeWindow && start == other.start && endExclusive == other.endExclusive

    override fun hashCode(): Int = 31 * start.hashCode() + endExclusive.hashCode()

    override fun toString(): String = "TimeWindow(start=$start, endExclusive=$endExclusive)"

    public companion object : ValueFactory<Input, Error, TimeWindow> {
        context(raise: Raise<Error>)
        override fun from(value: Input): TimeWindow {
            raise.ensure(value.start < value.endExclusive) { Error.NotAfterStart(value.start, value.endExclusive) }
            return TimeWindow(value.start, value.endExclusive)
        }

        /** A window of [duration] starting at [start]. */
        public fun starting(start: Instant, duration: PositiveDuration): TimeWindow =
            TimeWindow(start, start + duration.value)
    }
}
