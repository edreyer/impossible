package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.map
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.RequiredError
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.collections.BoundedList
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every error the library defines has a well-formed, stable description. */
class ErrorLawsTest {
    private val code = Regex("[a-z]+(?:_[a-z]+)*\\.[a-z]+(?:_[a-z]+)*")
    private val argumentName = Regex("[a-z]+(?:_[a-z]+)*")

    /** Every error generator, paired with the description of its errors. */
    private val described: List<Arb<ErrorMessage>> = listOf(
        NonBlankString.errorArb().map(ErrorMessages::describe),
        Slug.errorArb().map(ErrorMessages::describe),
        Secret.errorArb().map(ErrorMessages::describe),
        Email.errorArb().map(ErrorMessages::describe),
        Phone.errorArb().map(ErrorMessages::describe),
        CountryCode.errorArb().map(ErrorMessages::describe),
        LanguageTag.errorArb().map(ErrorMessages::describe),
        TypedId.errorArb().map(ErrorMessages::describe),
        PositiveInt.errorArb().map(ErrorMessages::describe),
        NegativeInt.errorArb().map(ErrorMessages::describe),
        PositiveLong.errorArb().map(ErrorMessages::describe),
        NegativeLong.errorArb().map(ErrorMessages::describe),
        PositiveDecimal.errorArb().map(ErrorMessages::describe),
        NegativeDecimal.errorArb().map(ErrorMessages::describe),
        NonNegativeDecimal.errorArb().map(ErrorMessages::describe),
        BoundedInt.errorArb(1..10).map(ErrorMessages::describe),
        Percentage.errorArb().map(ErrorMessages::describe),
        Currency.errorArb().map(ErrorMessages::describe),
        Money.errorArb().map(ErrorMessages::describe),
        Money.arithmeticErrorArb().map(ErrorMessages::describe),
        PositiveMoney.errorArb().map(ErrorMessages::describe),
        NonNegativeMoney.errorArb().map(ErrorMessages::describe),
        NegativeMoney.errorArb().map(ErrorMessages::describe),
        DateRange.errorArb().map(ErrorMessages::describe),
        DateChecks.errorArb().map(ErrorMessages::describe),
        TimeWindow.errorArb().map(ErrorMessages::describe),
        PositiveDuration.errorArb().map(ErrorMessages::describe),
        TimeZoneId.errorArb().map(ErrorMessages::describe),
        DomainName.errorArb().map(ErrorMessages::describe),
        IpAddress.errorArb().map(ErrorMessages::describe),
        HttpUrl.errorArb().map(ErrorMessages::describe),
        GeoPoint.errorArb().map(ErrorMessages::describe),
        BoundedList.errorArb(1..10).map(ErrorMessages::describe),
        UniqueList.errorArb().map(ErrorMessages::describe),
        Arb.nonEmptyError().map(ErrorMessages::describe),
        Arb.distinctError().map(ErrorMessages::describe),
        PositiveInt.errorArb().map { ErrorMessages.describe(RequiredError.Invalid(it), ErrorMessages::describe) },
    )

    /** Errors no generator yields: the arithmetic overflows and a missing required value. */
    private val fixed: List<ErrorMessage> = listOf(
        ErrorMessages.describe(PositiveInt.Overflow),
        ErrorMessages.describe(PositiveLong.Overflow),
        ErrorMessages.describe(NegativeInt.Overflow),
        ErrorMessages.describe(NegativeLong.Overflow),
        ErrorMessages.describe(RequiredError.Missing) { error: Nothing -> error },
    )

    /**
     * Every code the library publishes. Codes are public API: clients branch on them and
     * translations key on them, so this list only grows. Renaming or removing one is a
     * breaking change.
     */
    private val publishedCodes = setOf(
        "required.missing",
        "non_blank_string.blank", "non_blank_string.too_long",
        "slug.invalid_format",
        "secret.blank",
        "email.invalid_format",
        "phone.invalid_format",
        "country_code.unsupported",
        "language_tag.invalid_format",
        "typed_id.invalid_format",
        "positive_int.non_positive", "positive_int.overflow",
        "positive_long.non_positive", "positive_long.overflow",
        "positive_decimal.non_positive",
        "negative_int.non_negative", "negative_int.overflow",
        "negative_long.non_negative", "negative_long.overflow",
        "negative_decimal.non_negative",
        "non_negative_decimal.negative",
        "bounded_int.out_of_range",
        "percentage.out_of_range",
        "currency.unsupported",
        "money.invalid_amount", "money.no_minor_units", "money.not_in_minor_units", "money.out_of_range",
        "money.currency_mismatch", "money.division_by_zero", "money.overflow", "money.rounding_required",
        "positive_money.non_positive",
        "non_negative_money.negative",
        "negative_money.non_negative",
        "date_range.end_before_start",
        "date.not_past", "date.not_future",
        "time_window.not_after_start",
        "positive_duration.non_positive", "positive_duration.infinite",
        "time_zone.unknown",
        "domain_name.invalid_format",
        "ip_address.invalid_format",
        "http_url.invalid_syntax", "http_url.not_absolute", "http_url.unsupported_scheme",
        "http_url.missing_host", "http_url.invalid_port",
        "geo_point.latitude_out_of_range", "geo_point.longitude_out_of_range",
        "bounded_list.too_small", "bounded_list.too_large",
        "unique_list.duplicate",
        "collection.empty", "collection.duplicate",
    )

    private fun assertWellFormed(message: ErrorMessage) {
        assertTrue(code.matches(message.code), "bad code ${message.code}")
        assertTrue(message.text.isNotBlank(), "${message.code} has no text")
        for ((name, value) in message.arguments) {
            assertTrue(argumentName.matches(name), "${message.code} has a bad argument name $name")
            assertTrue(value.isNotBlank(), "${message.code} has a blank argument $name")
        }
    }

    @Test
    fun `every error has a well formed description and every published code occurs`() = runTest {
        val seen = mutableSetOf<String>()
        fixed.forEach { message ->
            assertWellFormed(message)
            seen += message.code
        }
        for (messages in described) {
            forAll(messages, iterations = 100) { message ->
                assertWellFormed(message)
                seen += message.code
            }
        }
        assertEquals(publishedCodes, seen)
    }

    /** Each generator's errors, described by their own overload and by [ValidationError.describe]. */
    private val dispatched: List<Arb<Pair<ErrorMessage, ErrorMessage>>> = listOf(
        agreement(NonBlankString.errorArb()) { ErrorMessages.describe(it) },
        agreement(Slug.errorArb()) { ErrorMessages.describe(it) },
        agreement(Secret.errorArb()) { ErrorMessages.describe(it) },
        agreement(Email.errorArb()) { ErrorMessages.describe(it) },
        agreement(Phone.errorArb()) { ErrorMessages.describe(it) },
        agreement(CountryCode.errorArb()) { ErrorMessages.describe(it) },
        agreement(LanguageTag.errorArb()) { ErrorMessages.describe(it) },
        agreement(TypedId.errorArb()) { ErrorMessages.describe(it) },
        agreement(PositiveInt.errorArb()) { ErrorMessages.describe(it) },
        agreement(NegativeInt.errorArb()) { ErrorMessages.describe(it) },
        agreement(PositiveLong.errorArb()) { ErrorMessages.describe(it) },
        agreement(NegativeLong.errorArb()) { ErrorMessages.describe(it) },
        agreement(PositiveDecimal.errorArb()) { ErrorMessages.describe(it) },
        agreement(NegativeDecimal.errorArb()) { ErrorMessages.describe(it) },
        agreement(NonNegativeDecimal.errorArb()) { ErrorMessages.describe(it) },
        agreement(BoundedInt.errorArb(1..10)) { ErrorMessages.describe(it) },
        agreement(Percentage.errorArb()) { ErrorMessages.describe(it) },
        agreement(Currency.errorArb()) { ErrorMessages.describe(it) },
        agreement(Money.errorArb()) { ErrorMessages.describe(it) },
        agreement(Money.arithmeticErrorArb()) { ErrorMessages.describe(it) },
        agreement(PositiveMoney.errorArb()) { ErrorMessages.describe(it) },
        agreement(NonNegativeMoney.errorArb()) { ErrorMessages.describe(it) },
        agreement(NegativeMoney.errorArb()) { ErrorMessages.describe(it) },
        agreement(DateRange.errorArb()) { ErrorMessages.describe(it) },
        agreement(DateChecks.errorArb()) { ErrorMessages.describe(it) },
        agreement(TimeWindow.errorArb()) { ErrorMessages.describe(it) },
        agreement(PositiveDuration.errorArb()) { ErrorMessages.describe(it) },
        agreement(TimeZoneId.errorArb()) { ErrorMessages.describe(it) },
        agreement(DomainName.errorArb()) { ErrorMessages.describe(it) },
        agreement(IpAddress.errorArb()) { ErrorMessages.describe(it) },
        agreement(HttpUrl.errorArb()) { ErrorMessages.describe(it) },
        agreement(GeoPoint.errorArb()) { ErrorMessages.describe(it) },
        agreement(BoundedList.errorArb(1..10)) { ErrorMessages.describe(it) },
        agreement(UniqueList.errorArb()) { ErrorMessages.describe(it) },
        agreement(Arb.nonEmptyError()) { ErrorMessages.describe(it) },
        agreement(Arb.distinctError()) { ErrorMessages.describe(it) },
    )

    private fun <E : ValidationError> agreement(errors: Arb<E>, describe: (E) -> ErrorMessage): Arb<Pair<ErrorMessage, ErrorMessage>> =
        errors.map { describe(it) to it.describe() }

    @Test
    fun `every error describes itself as its own overload does`() = runTest {
        for (pairs in dispatched) {
            forAll(pairs, iterations = 50) { (specific, dispatched) -> assertEquals(specific, dispatched) }
        }
        val overflows: List<ValidationError> =
            listOf(PositiveInt.Overflow, PositiveLong.Overflow, NegativeInt.Overflow, NegativeLong.Overflow)
        assertEquals(
            listOf("positive_int.overflow", "positive_long.overflow", "negative_int.overflow", "negative_long.overflow"),
            overflows.map { it.describe().code },
        )
        assertEquals("required.missing", RequiredError.Missing.describe().code)
    }
}
