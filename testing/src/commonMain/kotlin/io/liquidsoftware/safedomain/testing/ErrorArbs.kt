package io.liquidsoftware.safedomain.testing

import arrow.core.NonEmptySet
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.kotlinInstant
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.collections.DistinctError
import io.liquidsoftware.safedomain.collections.NonEmptyError
import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.collections.distinctFactory
import io.liquidsoftware.safedomain.collections.factory
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.geo.GeoPoint
import io.liquidsoftware.safedomain.identity.TypedId
import io.liquidsoftware.safedomain.locale.CountryCode
import io.liquidsoftware.safedomain.locale.LanguageTag
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.NegativeMoney
import io.liquidsoftware.safedomain.money.NonNegativeMoney
import io.liquidsoftware.safedomain.money.PositiveMoney
import io.liquidsoftware.safedomain.numeric.BoundedInt
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.NegativeDecimal
import io.liquidsoftware.safedomain.numeric.NegativeInt
import io.liquidsoftware.safedomain.numeric.NegativeLong
import io.liquidsoftware.safedomain.numeric.NonNegativeDecimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveDecimal
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.PositiveLong
import io.liquidsoftware.safedomain.numeric.RoundingMode
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Secret
import io.liquidsoftware.safedomain.text.Slug
import io.liquidsoftware.safedomain.time.DateChecks
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.PositiveDuration
import io.liquidsoftware.safedomain.time.TimeWindow
import io.liquidsoftware.safedomain.time.TimeZoneId
import io.liquidsoftware.safedomain.web.DomainName
import io.liquidsoftware.safedomain.web.HttpUrl
import io.liquidsoftware.safedomain.web.IpAddress
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

// Error generators. Each one runs the library's real factory or operation on input
// built to fail, so it yields exactly the errors, with exactly the details, that callers
// receive. Use them to test how an application describes, translates, or reports
// failures. Text parsers fail on arbitrary text and on near misses of valid values.

/** Text that is rarely valid for any parser, and text one edit away from [valid]. */
private fun invalidText(valid: Arb<String>): Arb<String> = Arb.choice(Arb.untrustedText(0..40), Arb.nearMiss(valid))

// text

/** Failures of [NonBlankString.bounded] with [maxLength]: blank text, and text longer than [maxLength]. */
public fun NonBlankString.Companion.errorArb(maxLength: Int = 60): Arb<NonBlankString.Error> {
    require(maxLength >= 1) { "maxLength must be positive: $maxLength" }
    val factory = NonBlankString.bounded(PositiveInt.fromTrusted(maxLength))
    val blank = textOf(" \t\n\r ", 0..8)
    val tooLong = textOf(ALPHANUMERIC, (maxLength + 1)..(maxLength + 20))
    return Arb.choice(blank, tooLong).map { rejected { factory.from(it) } }
}

public fun Slug.Companion.errorArb(): Arb<Slug.Error> =
    rejections(invalidText(Slug.arb().map { it.value })) { Slug.from(it) }

/** [Secret.Error.Blank], from empty and whitespace-only text. */
public fun Secret.Companion.errorArb(): Arb<Secret.Error> =
    textOf(" \t\n\r ", 0..8).map { rejected { Secret.from(it) } }

// contact

public fun Email.Companion.errorArb(): Arb<Email.Error> =
    rejections(invalidText(Email.arb().map { it.value })) { Email.from(it) }

public fun Phone.Companion.errorArb(): Arb<Phone.Error> =
    rejections(invalidText(Phone.arb().map { it.value })) { Phone.from(it) }

// locale

public fun CountryCode.Companion.errorArb(): Arb<CountryCode.Error> =
    rejections(invalidText(CountryCode.arb().map { it.value })) { CountryCode.from(it) }

public fun LanguageTag.Companion.errorArb(): Arb<LanguageTag.Error> =
    rejections(invalidText(LanguageTag.arb().map { it.value })) { LanguageTag.from(it) }

// identity

public fun TypedId.Companion.errorArb(): Arb<TypedId.Error> =
    rejections(invalidText(TypedId.arb<Unit>().map { it.value.toString() })) { TypedId.factory<Unit>().from(it) }

// numeric

/** [PositiveInt.Error.NonPositive] for zero and negative integers, including [Int.MIN_VALUE]. */
public fun PositiveInt.Companion.errorArb(): Arb<PositiveInt.Error> =
    Arb.int(Int.MIN_VALUE..0).map { rejected { PositiveInt.from(it) } }

/** [NegativeInt.Error.NonNegative] for zero and positive integers, including [Int.MAX_VALUE]. */
public fun NegativeInt.Companion.errorArb(): Arb<NegativeInt.Error> =
    Arb.int(0..Int.MAX_VALUE).map { rejected { NegativeInt.from(it) } }

/** [PositiveLong.Error.NonPositive] for zero and negative longs, including [Long.MIN_VALUE]. */
public fun PositiveLong.Companion.errorArb(): Arb<PositiveLong.Error> =
    Arb.long(Long.MIN_VALUE..0).map { rejected { PositiveLong.from(it) } }

/** [NegativeLong.Error.NonNegative] for zero and positive longs, including [Long.MAX_VALUE]. */
public fun NegativeLong.Companion.errorArb(): Arb<NegativeLong.Error> =
    Arb.long(0..Long.MAX_VALUE).map { rejected { NegativeLong.from(it) } }

/** [PositiveDecimal.Error.NonPositive] for zeros of several scales and negative decimals. */
public fun PositiveDecimal.Companion.errorArb(): Arb<PositiveDecimal.Error> =
    Decimal.arb().map { if (it.sign > 0) -it else it }.map { rejected { PositiveDecimal.from(it) } }

/** [NegativeDecimal.Error.NonNegative] for zeros of several scales and positive decimals. */
public fun NegativeDecimal.Companion.errorArb(): Arb<NegativeDecimal.Error> =
    Decimal.arb().map { it.abs() }.map { rejected { NegativeDecimal.from(it) } }

/** [NonNegativeDecimal.Error.Negative] for negative decimals. */
public fun NonNegativeDecimal.Companion.errorArb(): Arb<NonNegativeDecimal.Error> =
    nonZeroMagnitudes(maxDigits = 30, scales = -5..12).map { rejected { NonNegativeDecimal.from(-it) } }

/** [BoundedInt.Error.OutOfRange] for integers outside [bounds], which must leave some integers out. */
public fun BoundedInt.Companion.errorArb(bounds: IntRange): Arb<BoundedInt.Error> {
    val factory = BoundedInt.factory(bounds)
    val outside = listOfNotNull(
        if (bounds.first > Int.MIN_VALUE) Arb.int(Int.MIN_VALUE until bounds.first) else null,
        if (bounds.last < Int.MAX_VALUE) Arb.int((bounds.last + 1)..Int.MAX_VALUE) else null,
    )
    require(outside.isNotEmpty()) { "Bounds must leave some integers out: $bounds" }
    return Arb.choice(outside).map { rejected { factory.from(it) } }
}

/** [Percentage.Error.OutOfRange] for decimals below 0 and above 100. */
public fun Percentage.Companion.errorArb(): Arb<Percentage.Error> {
    val magnitudes = nonZeroMagnitudes(maxDigits = 20, scales = -2..8)
    return Arb.choice(magnitudes.map { -it }, magnitudes.map { Decimal.of(100) + it })
        .map { rejected { Percentage.from(it) } }
}

// money

public fun Currency.Companion.errorArb(): Arb<Currency.Error> =
    rejections(invalidText(Currency.arb().map { it.code })) { Currency.from(it) }

/**
 * Every [Money.Error]: amounts that are not decimals, unknown currencies, currencies
 * without a minor unit (such as XAU), amounts finer than the currency's minor unit, and
 * amounts too large for a Long count of minor units.
 */
public fun Money.Companion.errorArb(): Arb<Money.Error> {
    val monetary = Currency.arb(monetaryOnly = true)
    val invalidAmount = rejections(Arb.untrustedText(0..20)) { Money.parser.from(Money.Text(it, "EUR")) }
        .map { it.head }
    val invalidCurrency = rejections(invalidText(Currency.arb().map { it.code })) {
        Money.from(Money.Input(Decimal.ONE, it))
    }
    val noMinorUnits = Currency.arb().filter { it.minorUnits == null }
        .map { rejected { Money.of(Decimal.ONE, it) } }
    val notInMinorUnits = arbitrary { rs ->
        val currency = monetary.bind()
        val scale = (currency.minorUnits ?: 0) + rs.random.nextInt(1, 5)
        // A last digit of 1..9 makes the amount finer than the currency's minor unit.
        val unscaled = rs.random.nextLong(0, 1_000_000_000) * 10 + rs.random.nextLong(1, 10)
        rejected { Money.of(Decimal.of(unscaled, scale), currency) }
    }
    val outOfRange = arbitrary { rs ->
        val digits = (1..rs.random.nextInt(20, 31)).joinToString("") { DIGITS.drop(1).random(rs.random).toString() }
        val sign = if (rs.random.nextBoolean()) "-" else ""
        rejected { Money.of(Decimal.parse(sign + digits), monetary.bind()) }
    }
    return Arb.choice(invalidAmount, invalidCurrency, noMinorUnits, notInMinorUnits, outOfRange)
}

/**
 * Every [Money.ArithmeticError]: combining two currencies, dividing by zero, overflowing
 * the minor-unit count, and multiplying without rounding where the result needs it.
 */
public fun Money.Companion.arithmeticErrorArb(): Arb<Money.ArithmeticError> {
    val currencies = Currency.arb(monetaryOnly = true)
    val amounts = Money.arb()
    val mismatch = arbitrary {
        val first = currencies.bind()
        val second = currencies.filter { it != first }.bind()
        val augend = valid { Money.zero(first) }
        val addend = valid { Money.zero(second) }
        rejected { augend.add(addend) }
    }
    val divisionByZero = amounts.map { money -> rejected { money.divide(Decimal.ZERO, RoundingMode.HALF_EVEN) } }
    val overflow = currencies.map { currency ->
        val largest = valid { Money.ofMinor(Long.MAX_VALUE, currency) }
        val one = valid { Money.ofMinor(1, currency) }
        rejected { largest.add(one) }
    }
    // One minor unit times one half is half a minor unit, which needs rounding.
    val roundingRequired = currencies.map { currency ->
        val one = valid { Money.ofMinor(1, currency) }
        rejected { one.multiply(Decimal.of(5, 1), RoundingMode.UNNECESSARY) }
    }
    return Arb.choice(mismatch, divisionByZero, overflow, roundingRequired)
}

/** [PositiveMoney.Error.NonPositive] for zero and negative amounts. */
public fun PositiveMoney.Companion.errorArb(): Arb<PositiveMoney.Error> =
    Money.arb(minor = -DEFAULT_MAX_MINOR..0).map { rejected { PositiveMoney.from(it) } }

/** [NonNegativeMoney.Error.Negative] for negative amounts. */
public fun NonNegativeMoney.Companion.errorArb(): Arb<NonNegativeMoney.Error> =
    Money.arb(minor = -DEFAULT_MAX_MINOR..-1).map { rejected { NonNegativeMoney.from(it) } }

/** [NegativeMoney.Error.NonNegative] for zero and positive amounts. */
public fun NegativeMoney.Companion.errorArb(): Arb<NegativeMoney.Error> =
    Money.arb(minor = 0..DEFAULT_MAX_MINOR).map { rejected { NegativeMoney.from(it) } }

// time

/** [DateRange.Error.EndBeforeStart] for ends one to [maxDays] days before their start. */
public fun DateRange.Companion.errorArb(dates: ClosedRange<LocalDate> = DEFAULT_DATES, maxDays: Int = 3_650): Arb<DateRange.Error> {
    require(maxDays >= 1) { "maxDays must be positive: $maxDays" }
    val starts = Arb.kotlinxLocalDate(dates)
    val gaps = Arb.int(1..maxDays)
    return arbitrary {
        val start = starts.bind()
        rejected { DateRange.from(DateRange.Input(start, start.minus(DatePeriod(days = gaps.bind())))) }
    }
}

/**
 * [DateChecks.Error.NotPast] for today and later dates, and [DateChecks.Error.NotFuture]
 * for today and earlier dates, each against a fixed clock set to a day within [dates].
 */
public fun DateChecks.errorArb(dates: ClosedRange<LocalDate> = DEFAULT_DATES): Arb<DateChecks.Error> {
    val todays = Arb.kotlinxLocalDate(dates)
    val offsets = Arb.int(0..3_650)
    return arbitrary { rs ->
        val today = todays.bind()
        val timeZone = TimeZone.UTC
        val clock = FixedClock(today.atStartOfDayIn(timeZone))
        val offset = DatePeriod(days = offsets.bind())
        context(clock, timeZone) {
            if (rs.random.nextBoolean()) {
                rejected { DateChecks.past().from(today.plus(offset)) }
            } else {
                rejected { DateChecks.future().from(today.minus(offset)) }
            }
        }
    }
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** [TimeWindow.Error.NotAfterStart] for windows ending at or up to [maxDuration] before their start. */
public fun TimeWindow.Companion.errorArb(
    starts: Arb<Instant> = Arb.kotlinInstant(DEFAULT_INSTANTS),
    maxDuration: Duration = 365.days,
): Arb<TimeWindow.Error> {
    require(maxDuration.isPositive() && maxDuration.isFinite()) {
        "maxDuration must be positive and finite: $maxDuration"
    }
    val gaps = Arb.long(0..maxDuration.inWholeNanoseconds)
    return arbitrary {
        val start = starts.bind()
        rejected { TimeWindow.from(TimeWindow.Input(start, start - gaps.bind().nanoseconds)) }
    }
}

/** [PositiveDuration.Error.NonPositive] for zero and negative durations, and [PositiveDuration.Error.Infinite]. */
public fun PositiveDuration.Companion.errorArb(): Arb<PositiveDuration.Error> =
    Arb.choice(
        Arb.long(Long.MIN_VALUE / 2..0).map { it.nanoseconds },
        Arb.element(Duration.INFINITE, -Duration.INFINITE),
    ).map { rejected { PositiveDuration.from(it) } }

public fun TimeZoneId.Companion.errorArb(): Arb<TimeZoneId.Error> =
    rejections(invalidText(TimeZoneId.arb().map { it.value })) { TimeZoneId.from(it) }

// web

public fun DomainName.Companion.errorArb(): Arb<DomainName.Error> =
    rejections(invalidText(DomainName.arb().map { it.value })) { DomainName.from(it) }

public fun IpAddress.Companion.errorArb(): Arb<IpAddress.Error> =
    rejections(invalidText(IpAddress.arb().map { it.toString() })) { IpAddress.from(it) }

/**
 * Every [HttpUrl.Error]: malformed text, relative references, other schemes such as
 * `ftp`, URLs without a host, and ports outside 1..65535.
 */
public fun HttpUrl.Companion.errorArb(): Arb<HttpUrl.Error> {
    val hosts = DomainName.arb().map { it.value }
    val paths = Slug.arb().map { it.value }
    val crafted = arbitrary { rs ->
        when (rs.random.nextInt(4)) {
            0 -> "/" + paths.bind()
            1 -> listOf("ftp", "mailto", "file", "ws").random(rs.random) + "://" + hosts.bind()
            2 -> "https:///" + paths.bind()
            else -> "http://" + hosts.bind() + ":" + listOf(0, rs.random.nextInt(65_536, 100_000)).random(rs.random)
        }
    }
    return rejections(Arb.choice(invalidText(HttpUrl.arb().map { it.value }), crafted)) { HttpUrl.from(it) }
}

// geo

/** Latitudes outside -90..90 and longitudes outside -180..180, including NaN. */
public fun GeoPoint.Companion.errorArb(): Arb<GeoPoint.Error> {
    val inLatitude = Arb.double(-90.0..90.0, includeNaNs = false)
    val inLongitude = Arb.double(-180.0..180.0, includeNaNs = false)
    val latitude = arbitrary { GeoPoint.Input(outside(90.0).bind(), inLongitude.bind()) }
    val longitude = arbitrary { GeoPoint.Input(inLatitude.bind(), outside(180.0).bind()) }
    return Arb.choice(latitude, longitude).map { rejected { GeoPoint.from(it) }.head }
}

/** Doubles beyond -[limit]..[limit], and NaN. */
private fun outside(limit: Double): Arb<Double> = Arb.choice(
    Arb.double(limit..Double.MAX_VALUE, includeNaNs = false).filter { it > limit },
    Arb.double(-Double.MAX_VALUE..-limit, includeNaNs = false).filter { it < -limit },
    Arb.constant(Double.NaN),
)

// collections

/** [BoundedList.Error] for lists whose size is outside [bounds]; sizes above it go up to 5 past its end. */
public fun BoundedList.Companion.errorArb(bounds: IntRange): Arb<BoundedList.Error> {
    require(bounds.first >= 0 && !bounds.isEmpty()) { "Bounds must be a non-empty range of sizes: $bounds" }
    val factory = BoundedList.factory<Int>(bounds)
    val sizes = listOfNotNull(
        if (bounds.first > 0) Arb.int(0 until bounds.first) else null,
        Arb.int((bounds.last + 1)..(bounds.last + 5)),
    )
    return Arb.choice(sizes).map { size -> rejected { factory.from(List(size) { it }) } }
}

/** [UniqueList.Error.Duplicate] for lists of up to 10 elements that repeat an element. */
public fun UniqueList.Companion.errorArb(): Arb<UniqueList.Error> = arbitrary { rs ->
    val distinct = (0 until rs.random.nextInt(1, 10)).toList()
    val repeated = distinct + distinct.random(rs.random)
    rejected { UniqueList.factory<Int>().from(repeated.shuffled(rs.random)) }.random(rs.random)
}

/** [NonEmptyError.Empty], from the non-empty collection factories. */
public fun Arb.Companion.nonEmptyError(): Arb<NonEmptyError> =
    Arb.constant(Unit).map { rejected { NonEmptySet.factory<Int>().from(emptyList()) } }

/** Every [DistinctError]: an empty input, and inputs of up to 10 elements that repeat one. */
public fun Arb.Companion.distinctError(): Arb<DistinctError> = arbitrary { rs ->
    val distinct = (0 until rs.random.nextInt(0, 10)).toList()
    val input = if (distinct.isEmpty()) distinct else (distinct + distinct.random(rs.random)).shuffled(rs.random)
    rejected { NonEmptySet.distinctFactory<Int>().from(input) }.random(rs.random)
}
