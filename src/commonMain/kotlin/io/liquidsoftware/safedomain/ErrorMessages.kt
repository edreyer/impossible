package io.liquidsoftware.safedomain

import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.collections.DistinctError
import io.liquidsoftware.safedomain.collections.NonEmptyError
import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
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
import io.liquidsoftware.safedomain.numeric.NegativeDecimal
import io.liquidsoftware.safedomain.numeric.NegativeInt
import io.liquidsoftware.safedomain.numeric.NegativeLong
import io.liquidsoftware.safedomain.numeric.NonNegativeDecimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveDecimal
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.PositiveLong
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

/**
 * A failure described for people and machines. [code] is stable, such as
 * `email.invalid_format`, so clients can branch on it and translators can key on it;
 * [text] is a default English sentence; [arguments] holds the values the sentence uses,
 * so a translation can place them itself.
 *
 * Descriptions never quote rejected text: it may be personal data, a secret, or text an
 * attacker chose, and the caller already has it. They may mention values that passed
 * validation or are not free text, such as a currency code, a length, or a date.
 */
public data class ErrorMessage(val code: String, val text: String, val arguments: Map<String, String> = emptyMap())

/**
 * Default English descriptions of every error this library defines, one overload per
 * error type. Describe a composite's own errors by delegating to these:
 *
 * ```kotlin
 * fun describe(error: OrderError): ErrorMessage = when (error) {
 *     is OrderError.Email -> ErrorMessages.describe(error.cause)
 *     is OrderError.TooManyItems -> ErrorMessage("order.too_many_items", "An order holds at most 50 items.")
 * }
 * ```
 *
 * To localize, look the [ErrorMessage.code] up in your own catalog and format its
 * [ErrorMessage.arguments]; fall back to [ErrorMessage.text].
 */
public object ErrorMessages {
    public fun <Error> describe(error: RequiredError<Error>, describeCause: (Error) -> ErrorMessage): ErrorMessage =
        when (error) {
            RequiredError.Missing -> ErrorMessage("required.missing", "A value is required.")
            is RequiredError.Invalid -> describeCause(error.cause)
        }

    // text

    public fun describe(error: NonBlankString.Error): ErrorMessage = when (error) {
        NonBlankString.Error.Blank -> ErrorMessage("non_blank_string.blank", "Enter some text.")
        is NonBlankString.Error.TooLong -> ErrorMessage(
            "non_blank_string.too_long",
            "Use at most ${error.maximum} characters; this has ${error.actual}.",
            mapOf("maximum" to "${error.maximum}", "actual" to "${error.actual}"),
        )
    }

    public fun describe(error: Slug.Error): ErrorMessage = when (error) {
        is Slug.Error.InvalidFormat -> ErrorMessage(
            "slug.invalid_format",
            "Use lowercase letters and digits, joined by single hyphens.",
        )
    }

    public fun describe(error: Secret.Error): ErrorMessage = when (error) {
        Secret.Error.Blank -> ErrorMessage("secret.blank", "Enter a value.")
    }

    // contact

    public fun describe(error: Email.Error): ErrorMessage = when (error) {
        is Email.Error.InvalidFormat -> ErrorMessage("email.invalid_format", "Enter an email address such as name@example.com.")
    }

    public fun describe(error: Phone.Error): ErrorMessage = when (error) {
        is Phone.Error.InvalidFormat -> ErrorMessage(
            "phone.invalid_format",
            "Enter an international phone number: + and the country code, then up to 15 digits, without spaces.",
        )
    }

    // locale

    public fun describe(error: CountryCode.Error): ErrorMessage = when (error) {
        is CountryCode.Error.UnsupportedCode -> ErrorMessage(
            "country_code.unsupported",
            "Enter a two-letter ISO country code in capitals, such as DE or US.",
        )
    }

    public fun describe(error: LanguageTag.Error): ErrorMessage = when (error) {
        is LanguageTag.Error.InvalidFormat -> ErrorMessage(
            "language_tag.invalid_format",
            "Enter a language tag such as en, en-US or zh-Hant-TW.",
        )
    }

    // identity

    public fun describe(error: TypedId.Error): ErrorMessage = when (error) {
        is TypedId.Error.InvalidFormat -> ErrorMessage("typed_id.invalid_format", "Enter an identifier in UUID form.")
    }

    // numeric

    public fun describe(error: PositiveInt.Error): ErrorMessage = when (error) {
        is PositiveInt.Error.NonPositive -> greaterThanZero("positive_int.non_positive")
    }

    public fun describe(error: PositiveLong.Error): ErrorMessage = when (error) {
        is PositiveLong.Error.NonPositive -> greaterThanZero("positive_long.non_positive")
    }

    public fun describe(error: PositiveDecimal.Error): ErrorMessage = when (error) {
        is PositiveDecimal.Error.NonPositive -> greaterThanZero("positive_decimal.non_positive")
    }

    public fun describe(error: NegativeInt.Error): ErrorMessage = when (error) {
        is NegativeInt.Error.NonNegative -> lessThanZero("negative_int.non_negative")
    }

    public fun describe(error: NegativeLong.Error): ErrorMessage = when (error) {
        is NegativeLong.Error.NonNegative -> lessThanZero("negative_long.non_negative")
    }

    public fun describe(error: NegativeDecimal.Error): ErrorMessage = when (error) {
        is NegativeDecimal.Error.NonNegative -> lessThanZero("negative_decimal.non_negative")
    }

    public fun describe(error: NonNegativeDecimal.Error): ErrorMessage = when (error) {
        is NonNegativeDecimal.Error.Negative ->
            ErrorMessage("non_negative_decimal.negative", "Enter zero or a number greater than zero.")
    }

    public fun describe(error: BoundedInt.Error): ErrorMessage = when (error) {
        is BoundedInt.Error.OutOfRange -> ErrorMessage(
            "bounded_int.out_of_range",
            "Enter a whole number from ${error.bounds.first} to ${error.bounds.last}.",
            mapOf("minimum" to "${error.bounds.first}", "maximum" to "${error.bounds.last}"),
        )
    }

    public fun describe(error: Percentage.Error): ErrorMessage = when (error) {
        is Percentage.Error.OutOfRange -> ErrorMessage("percentage.out_of_range", "Enter a percentage from 0 to 100.")
    }

    // money

    public fun describe(error: Currency.Error): ErrorMessage = when (error) {
        is Currency.Error.UnsupportedCode -> ErrorMessage(
            "currency.unsupported",
            "Enter a three-letter ISO currency code in capitals, such as EUR or USD.",
        )
    }

    public fun describe(error: Money.Error): ErrorMessage = when (error) {
        is Money.Error.InvalidAmount -> ErrorMessage("money.invalid_amount", "Enter an amount such as 12.34.")
        is Money.Error.InvalidCurrency -> describe(error.cause)
        is Money.Error.NoMinorUnits -> ErrorMessage(
            "money.no_minor_units",
            "${error.currency.code} cannot be used for amounts of money.",
            mapOf("currency" to error.currency.code),
        )
        is Money.Error.NotInMinorUnits -> ErrorMessage(
            "money.not_in_minor_units",
            if (error.minorUnits == 0) {
                "Enter a whole amount, without decimal places."
            } else {
                "Enter an amount with at most ${error.minorUnits} decimal places."
            },
            mapOf("decimal_places" to "${error.minorUnits}"),
        )
        is Money.Error.OutOfRange -> ErrorMessage("money.out_of_range", "Enter a smaller amount.")
    }

    public fun describe(error: Money.ArithmeticError): ErrorMessage = when (error) {
        is Money.ArithmeticError.CurrencyMismatch -> ErrorMessage(
            "money.currency_mismatch",
            "Amounts in ${error.expected.code} and ${error.actual.code} cannot be combined.",
            mapOf("expected" to error.expected.code, "actual" to error.actual.code),
        )
        Money.ArithmeticError.DivisionByZero -> ErrorMessage("money.division_by_zero", "An amount cannot be divided by zero.")
        Money.ArithmeticError.Overflow -> ErrorMessage("money.overflow", "The result is too large to represent.")
        Money.ArithmeticError.RoundingRequired ->
            ErrorMessage("money.rounding_required", "The result cannot be represented without rounding.")
    }

    public fun describe(error: PositiveMoney.Error): ErrorMessage = when (error) {
        is PositiveMoney.Error.NonPositive -> ErrorMessage("positive_money.non_positive", "Enter an amount greater than zero.")
    }

    public fun describe(error: NonNegativeMoney.Error): ErrorMessage = when (error) {
        is NonNegativeMoney.Error.Negative -> ErrorMessage("non_negative_money.negative", "Enter zero or a positive amount.")
    }

    public fun describe(error: NegativeMoney.Error): ErrorMessage = when (error) {
        is NegativeMoney.Error.NonNegative -> ErrorMessage("negative_money.non_negative", "Enter an amount less than zero.")
    }

    // time

    public fun describe(error: DateRange.Error): ErrorMessage = when (error) {
        is DateRange.Error.EndBeforeStart -> ErrorMessage(
            "date_range.end_before_start",
            "The end date must not be before the start date.",
            mapOf("start" to "${error.start}", "end" to "${error.endInclusive}"),
        )
    }

    public fun describe(error: DateChecks.Error): ErrorMessage = when (error) {
        is DateChecks.Error.NotPast ->
            ErrorMessage("date.not_past", "Enter a date before today.", mapOf("today" to "${error.today}"))
        is DateChecks.Error.NotFuture ->
            ErrorMessage("date.not_future", "Enter a date after today.", mapOf("today" to "${error.today}"))
    }

    public fun describe(error: TimeWindow.Error): ErrorMessage = when (error) {
        is TimeWindow.Error.NotAfterStart -> ErrorMessage(
            "time_window.not_after_start",
            "The end time must be after the start time.",
            mapOf("start" to "${error.start}", "end" to "${error.endExclusive}"),
        )
    }

    public fun describe(error: PositiveDuration.Error): ErrorMessage = when (error) {
        is PositiveDuration.Error.NonPositive ->
            ErrorMessage("positive_duration.non_positive", "Enter a duration longer than zero.")
        PositiveDuration.Error.Infinite -> ErrorMessage("positive_duration.infinite", "Enter a finite duration.")
    }

    public fun describe(error: TimeZoneId.Error): ErrorMessage = when (error) {
        is TimeZoneId.Error.UnknownZone -> ErrorMessage(
            "time_zone.unknown",
            "Enter a time zone such as Europe/Berlin or UTC, spelled exactly as in the IANA database.",
        )
    }

    // web

    public fun describe(error: DomainName.Error): ErrorMessage = when (error) {
        is DomainName.Error.InvalidFormat -> ErrorMessage("domain_name.invalid_format", "Enter a domain name such as example.com.")
    }

    public fun describe(error: IpAddress.Error): ErrorMessage = when (error) {
        is IpAddress.Error.InvalidFormat ->
            ErrorMessage("ip_address.invalid_format", "Enter an IPv4 address such as 192.0.2.1 or an IPv6 address.")
    }

    public fun describe(error: HttpUrl.Error): ErrorMessage = when (error) {
        is HttpUrl.Error.InvalidSyntax -> ErrorMessage("http_url.invalid_syntax", "Enter a web address such as https://example.com.")
        is HttpUrl.Error.NotAbsolute -> ErrorMessage("http_url.not_absolute", "Enter a full web address, starting with https://.")
        is HttpUrl.Error.UnsupportedScheme -> ErrorMessage(
            "http_url.unsupported_scheme",
            "Enter a web address starting with http:// or https://.",
        )
        is HttpUrl.Error.MissingHost -> ErrorMessage("http_url.missing_host", "Enter a web address that includes a host name.")
        is HttpUrl.Error.InvalidPort -> ErrorMessage(
            "http_url.invalid_port",
            "Use a port from 1 to 65535.",
            mapOf("port" to "${error.port}"),
        )
    }

    // geo

    public fun describe(error: GeoPoint.Error): ErrorMessage = when (error) {
        is GeoPoint.Error.LatitudeOutOfRange ->
            ErrorMessage("geo_point.latitude_out_of_range", "Enter a latitude from -90 to 90 degrees.")
        is GeoPoint.Error.LongitudeOutOfRange ->
            ErrorMessage("geo_point.longitude_out_of_range", "Enter a longitude from -180 to 180 degrees.")
    }

    // collections

    public fun describe(error: BoundedList.Error): ErrorMessage = when (error) {
        is BoundedList.Error.TooSmall -> ErrorMessage(
            "bounded_list.too_small",
            "Add at least ${error.minimum} ${items(error.minimum)}; there ${are(error.actual)}.",
            mapOf("minimum" to "${error.minimum}", "actual" to "${error.actual}"),
        )
        is BoundedList.Error.TooLarge -> ErrorMessage(
            "bounded_list.too_large",
            "Use at most ${error.maximum} ${items(error.maximum)}; there ${are(error.actual)}.",
            mapOf("maximum" to "${error.maximum}", "actual" to "${error.actual}"),
        )
    }

    public fun describe(error: UniqueList.Error): ErrorMessage = when (error) {
        is UniqueList.Error.Duplicate -> duplicate("unique_list.duplicate", error.firstIndex, error.duplicateIndex)
    }

    public fun describe(error: NonEmptyError): ErrorMessage = when (error) {
        NonEmptyError.Empty -> ErrorMessage("collection.empty", "Add at least one item.")
    }

    public fun describe(error: DistinctError): ErrorMessage = when (error) {
        DistinctError.Empty -> ErrorMessage("collection.empty", "Add at least one item.")
        is DistinctError.Duplicate -> duplicate("collection.duplicate", error.firstIndex, error.duplicateIndex)
    }

    private fun greaterThanZero(code: String) = ErrorMessage(code, "Enter a number greater than zero.")

    private fun lessThanZero(code: String) = ErrorMessage(code, "Enter a number less than zero.")

    private fun duplicate(code: String, firstIndex: Int, duplicateIndex: Int) = ErrorMessage(
        code,
        "Item ${duplicateIndex + 1} repeats item ${firstIndex + 1}.",
        mapOf("first_index" to "$firstIndex", "duplicate_index" to "$duplicateIndex"),
    )

    private fun items(count: Int) = if (count == 1) "item" else "items"

    private fun are(count: Int) = if (count == 1) "is 1" else "are $count"
}
