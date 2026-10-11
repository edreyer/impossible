package io.liquidsoftware.safedomain.serialization

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
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
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.NegativeDecimal
import io.liquidsoftware.safedomain.numeric.NegativeInt
import io.liquidsoftware.safedomain.numeric.NegativeLong
import io.liquidsoftware.safedomain.numeric.NonNegativeDecimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveDecimal
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.PositiveLong
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.testing.arb
import io.liquidsoftware.safedomain.testing.untrustedText
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Secret
import io.liquidsoftware.safedomain.text.Slug
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.PositiveDuration
import io.liquidsoftware.safedomain.time.TimeWindow
import io.liquidsoftware.safedomain.time.TimeZoneId
import io.liquidsoftware.safedomain.web.DomainName
import io.liquidsoftware.safedomain.web.HttpUrl
import io.liquidsoftware.safedomain.web.IpAddress
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Every serializer writes what it reads, and reads any input without an unexpected exception. */
class SerializationLawsTest {
    private val json = Json

    @OptIn(ExperimentalKotest::class)
    private suspend fun <A> roundTrips(serializer: KSerializer<A>, arb: Arb<A>, iterations: Int = 200) {
        checkAll(PropTestConfig(seed = 20_261_011L, iterations = iterations), arb) { value ->
            val text = json.encodeToString(serializer, value)
            assertEquals(value, json.decodeFromString(serializer, text), text)
        }
    }

    @OptIn(ExperimentalKotest::class)
    private suspend fun <A> neverThrowsUnexpectedly(serializer: KSerializer<A>) {
        val texts = Arb.choice(Arb.untrustedText(), Arb.string(0..20))
        checkAll(PropTestConfig(seed = 20_261_011L, iterations = 200), texts) { text ->
            val encoded = json.encodeToString(String.serializer(), text)
            try {
                json.decodeFromString(serializer, encoded)
            } catch (_: InvalidValueException) {
            } catch (e: Throwable) {
                fail("${serializer.descriptor.serialName} threw ${e::class.simpleName} instead of InvalidValueException")
            }
        }
    }

    @Test
    fun `text contact and locale values round trip`() = runTest {
        roundTrips(NonBlankStringSerializer, NonBlankString.arb())
        roundTrips(SlugSerializer, Slug.arb())
        roundTrips(EmailSerializer, Email.arb())
        roundTrips(PhoneSerializer, Phone.arb())
        roundTrips(CountryCodeSerializer, CountryCode.arb())
        roundTrips(LanguageTagSerializer, LanguageTag.arb())
        roundTrips(TypedIdSerializer<Unit>(), TypedId.arb())
    }

    @Test
    fun `numeric values round trip exactly`() = runTest {
        roundTrips(DecimalSerializer, Decimal.arb())
        roundTrips(PositiveIntSerializer, PositiveInt.arb())
        roundTrips(NegativeIntSerializer, NegativeInt.arb())
        roundTrips(PositiveLongSerializer, PositiveLong.arb())
        roundTrips(NegativeLongSerializer, NegativeLong.arb())
        roundTrips(PositiveDecimalSerializer, PositiveDecimal.arb())
        roundTrips(NonNegativeDecimalSerializer, NonNegativeDecimal.arb())
        roundTrips(NegativeDecimalSerializer, NegativeDecimal.arb())
        roundTrips(PercentageSerializer, Percentage.arb())
        roundTrips(BoundedIntSerializer(-3..7), BoundedInt.arb(-3..7))
    }

    @Test
    fun `money values round trip`() = runTest {
        roundTrips(CurrencySerializer, Currency.arb())
        roundTrips(MoneySerializer, Money.arb())
        roundTrips(PositiveMoneySerializer, PositiveMoney.arb())
        roundTrips(NonNegativeMoneySerializer, NonNegativeMoney.arb())
        roundTrips(NegativeMoneySerializer, NegativeMoney.arb())
    }

    @Test
    fun `time web geo and collection values round trip`() = runTest {
        roundTrips(DateRangeSerializer, DateRange.arb())
        roundTrips(TimeWindowSerializer, TimeWindow.arb())
        roundTrips(PositiveDurationSerializer, PositiveDuration.arb())
        roundTrips(TimeZoneIdSerializer, TimeZoneId.arb())
        roundTrips(DomainNameSerializer, DomainName.arb())
        roundTrips(HttpUrlSerializer, HttpUrl.arb())
        roundTrips(IpAddressSerializer, IpAddress.arb())
        roundTrips(Ipv4AddressSerializer, IpAddress.V4.arb())
        roundTrips(Ipv6AddressSerializer, IpAddress.V6.arb())
        roundTrips(GeoPointSerializer, GeoPoint.arb())
        roundTrips(UniqueListSerializer(PositiveIntSerializer), UniqueList.arb(PositiveInt.arb()))
        roundTrips(BoundedListSerializer(SlugSerializer, 1..4), BoundedList.arb(Slug.arb(), 1..4))
    }

    @Test
    fun `text serializers reject any bad input with InvalidValueException`() = runTest {
        listOf<KSerializer<*>>(
            NonBlankStringSerializer, SlugSerializer, SecretSerializer, EmailSerializer, PhoneSerializer,
            CountryCodeSerializer, LanguageTagSerializer, TypedIdSerializer<Unit>(), DecimalSerializer,
            PositiveDecimalSerializer, NonNegativeDecimalSerializer, NegativeDecimalSerializer, PercentageSerializer,
            CurrencySerializer, TimeZoneIdSerializer, DomainNameSerializer, HttpUrlSerializer,
            IpAddressSerializer, Ipv4AddressSerializer, Ipv6AddressSerializer,
        ).forEach { neverThrowsUnexpectedly(it) }
    }

    @Test
    fun `a secret is never written`() = runTest {
        val secret = Secret.fromTrusted("hunter2")
        try {
            json.encodeToString(SecretSerializer, secret)
            fail("a Secret was serialized")
        } catch (_: SerializationException) {
        }
    }
}



