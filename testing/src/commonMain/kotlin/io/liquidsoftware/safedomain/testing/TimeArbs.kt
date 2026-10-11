package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.kotlinInstant
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.PositiveDuration
import io.liquidsoftware.safedomain.time.TimeWindow
import io.liquidsoftware.safedomain.time.TimeZoneId
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/** The default span for generated dates: 1900-01-01 through 2100-12-31. */
val DEFAULT_DATES: ClosedRange<LocalDate> = LocalDate(1900, 1, 1)..LocalDate(2100, 12, 31)

/**
 * kotlinx-datetime dates in [range], including both edges. Named apart from kotest's
 * JVM-only `Arb.localDate()`, which produces `java.time.LocalDate`.
 */
fun Arb.Companion.kotlinxLocalDate(range: ClosedRange<LocalDate> = DEFAULT_DATES): Arb<LocalDate> =
    Arb.long(range.start.toEpochDays()..range.endInclusive.toEpochDays()).map { LocalDate.fromEpochDays(it) }

/** The default span for generated instants: 1970 through 2099, at nanosecond precision. */
val DEFAULT_INSTANTS: ClosedRange<Instant> =
    Instant.parse("1970-01-01T00:00:00Z")..Instant.parse("2099-12-31T23:59:59.999999999Z")

/** Ranges starting within [dates] and spanning one to [maxDays] + 1 days. */
fun DateRange.Companion.arb(dates: ClosedRange<LocalDate> = DEFAULT_DATES, maxDays: Int = 3_650): Arb<DateRange> {
    require(maxDays >= 0) { "maxDays must not be negative: $maxDays" }
    val starts = Arb.kotlinxLocalDate(dates)
    val lengths = Arb.int(0..maxDays)
    return arbitrary {
        val start = starts.bind()
        DateRange.fromTrusted(DateRange.Input(start, start.plus(DatePeriod(days = lengths.bind()))))
    }
}

/** Finite durations from one nanosecond to [max]. */
fun PositiveDuration.Companion.arb(max: Duration = 365.days): Arb<PositiveDuration> {
    require(max.isPositive() && max.isFinite()) { "max must be positive and finite: $max" }
    return Arb.long(1..max.inWholeNanoseconds).map { PositiveDuration.fromTrusted(it.nanoseconds) }
}

/** Windows starting within [starts] and lasting up to [maxDuration]. */
fun TimeWindow.Companion.arb(
    starts: Arb<Instant> = Arb.kotlinInstant(DEFAULT_INSTANTS),
    maxDuration: Duration = 365.days,
): Arb<TimeWindow> {
    val durations = PositiveDuration.arb(maxDuration)
    return arbitrary { TimeWindow.starting(starts.bind(), durations.bind()) }
}

private val zoneIds: List<String> by lazy {
    // Validating UTC first loads the time zone database on platforms that bundle it.
    TimeZoneId.fromTrusted("UTC")
    (TimeZone.availableZoneIds + "UTC").filter { accepts { TimeZoneId.from(it) } }.sorted()
}

/** Every zone id the platform's database resolves, plus `UTC`, chosen uniformly. */
fun TimeZoneId.Companion.arb(): Arb<TimeZoneId> = Arb.element(zoneIds).map { TimeZoneId.fromTrusted(it) }
