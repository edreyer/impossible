package io.liquidsoftware.safedomain.time

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime

class TimeZoneIdTest {
    private fun zone(value: String) = either<TimeZoneId.Error, TimeZoneId> { TimeZoneId.from(value) }

    @Test
    fun `region ids resolve on every platform`() {
        for (value in listOf("UTC", "Europe/Berlin", "America/New_York", "America/Argentina/Buenos_Aires", "Asia/Kolkata", "Etc/GMT+5")) {
            assertTrue(zone(value).isRight(), value)
        }
        val berlin = TimeZoneId.fromTrusted("Europe/Berlin")
        assertEquals(
            LocalDateTime(2026, 7, 1, 14, 0),
            Instant.parse("2026-07-01T12:00:00Z").toLocalDateTime(berlin.timeZone),
        )
    }

    @Test
    fun `unknown ids wrong case and fixed offsets are rejected`() {
        for (value in listOf("", "Europe/Atlantis", "europe/berlin", "Berlin", "+02:00", "UTC+2", "Z", "GMT", " UTC")) {
            assertEquals(Either.Left(TimeZoneId.Error.UnknownZone(value)), zone(value), value)
        }
    }
}
