package io.liquidsoftware.safedomain.testing

import arrow.core.Either
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.geo.GeoPoint
import io.liquidsoftware.safedomain.identity.TypedId
import io.liquidsoftware.safedomain.locale.CountryCode
import io.liquidsoftware.safedomain.locale.LanguageTag
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveDecimal
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.PositiveLong
import io.liquidsoftware.safedomain.numeric.NegativeInt
import io.liquidsoftware.safedomain.numeric.NegativeLong
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Secret
import io.liquidsoftware.safedomain.text.Slug
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.TimeWindow
import io.liquidsoftware.safedomain.time.TimeZoneId
import io.liquidsoftware.safedomain.web.DomainName
import io.liquidsoftware.safedomain.web.HttpUrl
import io.liquidsoftware.safedomain.web.IpAddress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rendering a valid value and parsing it again gives back an equal value, with the same
 * hash and, where the type preserves input, the same text.
 */
class RoundTripLawsTest {
    private fun <A> assertRoundTrip(original: A, reparsed: Either<*, A>) {
        assertEquals(Either.Right(original), reparsed)
        assertEquals(original.hashCode(), reparsed.getOrNull().hashCode())
    }

    @Test
    fun `text types preserve their input exactly`() = runTest {
        forAll(NonBlankString.arb()) { assertRoundTrip(it, attempt { NonBlankString.from(it.value) }) }
        forAll(Slug.arb()) { assertRoundTrip(it, attempt { Slug.from(it.value) }) }
        forAll(Secret.arb()) { assertRoundTrip(it, attempt { Secret.from(it.reveal()) }) }
    }

    @Test
    fun `contact and locale types preserve their input exactly`() = runTest {
        forAll(Email.arb()) { assertRoundTrip(it, attempt { Email.from(it.value) }) }
        forAll(Phone.arb()) { assertRoundTrip(it, attempt { Phone.from(it.value) }) }
        forAll(CountryCode.arb()) { assertRoundTrip(it, attempt { CountryCode.from(it.value) }) }
        forAll(LanguageTag.arb()) { assertRoundTrip(it, attempt { LanguageTag.from(it.value) }) }
        forAll(Currency.arb()) { assertRoundTrip(it, attempt { Currency.from(it.code) }) }
    }

    @Test
    fun `network types survive rendering and reparsing`() = runTest {
        forAll(DomainName.arb()) { assertRoundTrip(it, attempt { DomainName.from(it.value) }) }
        forAll(HttpUrl.arb()) { assertRoundTrip(it, attempt { HttpUrl.from(it.value) }) }
        forAll(IpAddress.arb()) { assertRoundTrip(it, attempt { IpAddress.from(it.toString()) }) }
    }

    @Test
    fun `identifiers and time zones survive rendering and reparsing`() = runTest {
        forAll(TypedId.arb<Unit>()) { assertRoundTrip(it, attempt { TypedId.factory<Unit>().from(it.value.toString()) }) }
        forAll(TimeZoneId.arb()) { assertRoundTrip(it, attempt { TimeZoneId.from(it.value) }) }
    }

    @Test
    fun `decimal text round trips exactly including scale`() = runTest {
        forAll(Decimal.arb()) { decimal ->
            assertEquals(decimal, Decimal.parse(decimal.toString()))
            assertEquals(0, Decimal.parse(decimal.toPlainString()).compareTo(decimal))
        }
    }

    @Test
    fun `numeric refinements accept their own values`() = runTest {
        forAll(PositiveInt.arb()) { assertRoundTrip(it, attempt { PositiveInt.from(it.value) }) }
        forAll(NegativeInt.arb()) { assertRoundTrip(it, attempt { NegativeInt.from(it.value) }) }
        forAll(PositiveLong.arb()) { assertRoundTrip(it, attempt { PositiveLong.from(it.value) }) }
        forAll(NegativeLong.arb()) { assertRoundTrip(it, attempt { NegativeLong.from(it.value) }) }
        forAll(PositiveDecimal.arb()) { assertRoundTrip(it, attempt { PositiveDecimal.from(it.value) }) }
        forAll(Percentage.arb()) { assertRoundTrip(it, attempt { Percentage.from(it.value) }) }
    }

    @Test
    fun `money round trips through both its factory and its text parser`() = runTest {
        forAll(Money.arb()) { money ->
            assertRoundTrip(money, attempt { Money.from(Money.Input(money.amount, money.currency.code)) })
            assertRoundTrip(
                money,
                attempt { Money.parser.from(Money.Text(money.amount.toPlainString(), money.currency.code)) },
            )
        }
    }

    @Test
    fun `composite inputs round trip`() = runTest {
        forAll(GeoPoint.arb()) { assertRoundTrip(it, attempt { GeoPoint.from(GeoPoint.Input(it.latitude, it.longitude)) }) }
        forAll(DateRange.arb()) { assertRoundTrip(it, attempt { DateRange.from(DateRange.Input(it.start, it.endInclusive)) }) }
        forAll(TimeWindow.arb()) { assertRoundTrip(it, attempt { TimeWindow.from(TimeWindow.Input(it.start, it.endExclusive)) }) }
    }
}
