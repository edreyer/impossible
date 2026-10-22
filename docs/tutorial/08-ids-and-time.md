# Step 8: Identifiers and time

**You will learn** how to give each kind of id its own type so they can't be mixed up,
how to check a date against "today" without hiding a clock inside your types, and how
to work with date ranges and time windows.

Imports for this step: `identity.TypedId`, `time.DateChecks`, `time.DateRange`,
`time.TimeWindow`, `time.PositiveDuration`, `kotlinx.datetime.LocalDate`,
`kotlinx.datetime.TimeZone`, `kotlin.time.Clock`, `kotlin.time.Instant`,
`kotlin.time.Duration.Companion.hours`, `arrow.core.raise.ensure`, and the `validate`
tools from step 4.

## Ids that can't be swapped

A customer id and a promotion id are both UUIDs, and a function that takes two of
them is one transposition away from a bug. `TypedId<Tag>` is a UUID with a
compile-time tag. Define an empty marker per kind of id and a factory for each:

```kotlin
interface CustomerTag
interface PromotionTag

typealias CustomerId = TypedId<CustomerTag>
typealias PromotionId = TypedId<PromotionTag>

val customerIds = TypedId.factory<CustomerTag>()
val promotionIds = TypedId.factory<PromotionTag>()

fun greet(customer: CustomerId): String = "Hello, customer ${customer.value}"
```

Passing a `PromotionId` to `greet` doesn't compile. The tag exists only at compile
time: at run time a `TypedId` is just its `kotlin.uuid.Uuid`. The factory accepts the
full `8-4-4-4-12` form in either case and stores it canonically, so
`customerIds.from("550E8400-E29B-41D4-A716-446655440000")` renders in lowercase, and
`"42"` raises `TypedId.Error.InvalidFormat`. `TypedId.random<Tag>()` makes a new one.

## "In the future" is not a property of a date

A promotion is announced on some date, which must still be ahead of us when the
promotion is created. But "future" is a relationship between the date and *now*; a
date that is in the future today won't be next month. So there is no `FutureDate`
type. Instead, `DateChecks.future()` is a factory that reads today from a `Clock` and
a `TimeZone` passed as context parameters, and returns the plain `LocalDate`. The
check holds at construction, and the stored value stays a date.

Because the factory needs a clock, `Promotion.from` takes one as a context parameter
too, so its companion is a plain function instead of a `ValueFactory`:

```kotlin
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
```

`DateRange` includes both ends, so a single day is a valid range, and an end before
the start raises `EndBeforeStart`. It offers `date in range`, `overlaps`, `days` (30
for November) and `dates()`. The block then relates the two dates, as in step 5.

In production, pass the system clock; in tests, a fixed one, so the tests never
depend on the day they run:

```kotlin
private val clock = object : Clock {
    override fun now(): Instant = Instant.parse("2026-10-05T12:00:00Z")
}
```

```kotlin
private fun promotion(input: PromotionInput): Either<NonEmptyList<Promotion.Error>, Promotion> =
    with(clock) { with(TimeZone.UTC) { either { Promotion.from(input) } } }
```

An announcement dated today fails with `NotFuture(today, today)`: both checks are
strict. `DateChecks.past()` is the mirror image, for birth dates and order dates.

## Time windows

Delivery slots are spans of time, not dates. `TimeWindow` is a half-open interval of
`Instant`s, start included and end excluded, and is never empty.
`PositiveDuration` is a finite duration greater than zero:

```kotlin
val slotLength: PositiveDuration = PositiveDuration.fromTrusted(2.hours)

fun deliverySlot(start: Instant): TimeWindow = TimeWindow.starting(start, slotLength)
```

Because the end is excluded, the 08:00 to 10:00 slot and the 10:00 to 12:00 slot don't
overlap: 09:59:59 is in the first and 10:00 is not.

## What you have now

Ids the compiler keeps apart, promotions whose dates were checked against a clock you
control, and delivery slots that tile without overlapping.

The code and its tests are in
[Step08IdsAndTimeTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/step08/Step08IdsAndTimeTest.kt).

Next: [Step 9, field paths](09-field-paths.md).
