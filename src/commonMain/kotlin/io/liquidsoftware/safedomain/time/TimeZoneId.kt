package io.liquidsoftware.safedomain.time

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone

/**
 * An IANA time zone region id such as `Europe/Berlin`, or `UTC`. The id must be written
 * exactly as the time zone database spells it (it is case-sensitive) and must resolve in
 * the platform's database: the JDK's on JVM, the system's on native targets, and the
 * bundled `@js-joda/timezone` data on JS. Databases of different ages can disagree on
 * recently added zones. Fixed offsets such as `+02:00` are rejected; use `UtcOffset`.
 * Equality and hashing use [value].
 */
class TimeZoneId private constructor(val value: String, val timeZone: TimeZone) {
    sealed interface Error {
        data class UnknownZone(val value: String) : Error
    }

    override fun equals(other: Any?): Boolean = other is TimeZoneId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "TimeZoneId(value=$value)"

    companion object : ValueFactory<String, Error, TimeZoneId> {
        private val regionId = Regex("[A-Za-z][A-Za-z0-9_+-]*(?:/[A-Za-z0-9_+-]+)+")

        context(raise: Raise<Error>)
        override fun from(value: String): TimeZoneId {
            raise.ensure(value == "UTC" || regionId.matches(value)) { Error.UnknownZone(value) }
            loadTimeZoneDatabase()
            val zone = try {
                TimeZone.of(value)
            } catch (_: IllegalTimeZoneException) {
                raise.raise(Error.UnknownZone(value))
            }
            raise.ensure(zone.id == value) { Error.UnknownZone(value) }
            return TimeZoneId(value, zone)
        }
    }
}

/** Makes the full time zone database available; only JS needs to load one. */
internal expect fun loadTimeZoneDatabase()
