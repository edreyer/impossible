package io.liquidsoftware.safedomain.tutorial.step08

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.identity.TypedId
import io.liquidsoftware.safedomain.time.DateChecks
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.PositiveDuration
import io.liquidsoftware.safedomain.time.TimeWindow
import io.liquidsoftware.safedomain.validate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Code for docs/tutorial/08-ids-and-time.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

interface CustomerTag
interface PromotionTag

typealias CustomerId = TypedId<CustomerTag>
typealias PromotionId = TypedId<PromotionTag>

val customerIds = TypedId.factory<CustomerTag>()
val promotionIds = TypedId.factory<PromotionTag>()

fun greet(customer: CustomerId): String = "Hello, customer ${customer.value}"

data class PromotionInput(val id: String, val announce: LocalDate, val runs: DateRange.Input)

class Promotion private constructor(val id: PromotionId, val announce: LocalDate, val runs: DateRange) {
    sealed interface Error {
        data class InvalidId(val cause: TypedId.Error) : Error
        data class AnnouncementNotFuture(val cause: DateChecks.Error.NotFuture) : Error
        data class InvalidRun(val cause: DateRange.Error) : Error
        data class AnnouncedAfterStart(val announce: LocalDate, val start: LocalDate) : Error
    }

    companion object {
        context(clock: Clock, timeZone: TimeZone, raise: Raise<NonEmptyList<Error>>)
        fun from(value: PromotionInput): Promotion = validate {
            val id by promotionIds.field(value.id, Error::InvalidId)
            val schedule by field {
                val announce by DateChecks.future().field(value.announce, Error::AnnouncementNotFuture)
                val runs by DateRange.field(value.runs, Error::InvalidRun)
                ensure(announce <= runs.start) { Error.AnnouncedAfterStart(announce, runs.start) }
                announce to runs
            }
            Promotion(id, schedule.first, schedule.second)
        }
    }
}

val slotLength: PositiveDuration = PositiveDuration.fromTrusted(2.hours)

fun deliverySlot(start: Instant): TimeWindow = TimeWindow.starting(start, slotLength)

class Step08IdsAndTimeTest {
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-05T12:00:00Z")
    }
    private val today = LocalDate(2026, 10, 5)
    private val valid = PromotionInput(
        id = "550e8400-e29b-41d4-a716-446655440000",
        announce = LocalDate(2026, 10, 20),
        runs = DateRange.Input(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)),
    )

    private fun promotion(input: PromotionInput): Either<NonEmptyList<Promotion.Error>, Promotion> =
        with(clock) { with(TimeZone.UTC) { either { Promotion.from(input) } } }

    @Test
    fun `typed ids parse once and keep their tag`() {
        val id: Either<TypedId.Error, CustomerId> = either { customerIds.from("550E8400-E29B-41D4-A716-446655440000") }
        assertEquals("Hello, customer 550e8400-e29b-41d4-a716-446655440000", id.map(::greet).getOrNull())
        val notAnId: Either<TypedId.Error, CustomerId> = either { customerIds.from("42") }
        assertEquals(Either.Left(TypedId.Error.InvalidFormat("42")), notAnId)
    }

    @Test
    fun `date checks read today from the clock in context`() {
        assertEquals(30L, promotion(valid).getOrNull()?.runs?.days)
        assertEquals(
            Either.Left(nonEmptyListOf(Promotion.Error.AnnouncementNotFuture(DateChecks.Error.NotFuture(today, today)))),
            promotion(valid.copy(announce = today)),
        )
    }

    @Test
    fun `relationships between dates are checked once both are valid`() {
        val late = LocalDate(2026, 11, 2)
        assertEquals(
            Either.Left(nonEmptyListOf(Promotion.Error.AnnouncedAfterStart(late, LocalDate(2026, 11, 1)))),
            promotion(valid.copy(announce = late)),
        )
        val backwards = DateRange.Input(LocalDate(2026, 11, 30), LocalDate(2026, 11, 1))
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    Promotion.Error.InvalidId(TypedId.Error.InvalidFormat("nope")),
                    Promotion.Error.InvalidRun(DateRange.Error.EndBeforeStart(backwards.start, backwards.endInclusive)),
                ),
            ),
            promotion(valid.copy(id = "nope", runs = backwards)),
        )
    }

    @Test
    fun `adjacent delivery slots never overlap`() {
        val morning = deliverySlot(Instant.parse("2026-10-06T08:00:00Z"))
        val midday = deliverySlot(Instant.parse("2026-10-06T10:00:00Z"))
        assertFalse(morning.overlaps(midday))
        assertTrue(Instant.parse("2026-10-06T09:59:59Z") in morning)
        assertFalse(Instant.parse("2026-10-06T10:00:00Z") in morning)
    }
}
