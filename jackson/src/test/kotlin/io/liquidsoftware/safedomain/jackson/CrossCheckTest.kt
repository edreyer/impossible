package io.liquidsoftware.safedomain.jackson

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
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
import io.liquidsoftware.safedomain.serialization.BoundedIntSerializer
import io.liquidsoftware.safedomain.serialization.BoundedListSerializer
import io.liquidsoftware.safedomain.serialization.CountryCodeSerializer
import io.liquidsoftware.safedomain.serialization.CurrencySerializer
import io.liquidsoftware.safedomain.serialization.DateRangeSerializer
import io.liquidsoftware.safedomain.serialization.DecimalSerializer
import io.liquidsoftware.safedomain.serialization.DomainNameSerializer
import io.liquidsoftware.safedomain.serialization.EmailSerializer
import io.liquidsoftware.safedomain.serialization.GeoPointSerializer
import io.liquidsoftware.safedomain.serialization.HttpUrlSerializer
import io.liquidsoftware.safedomain.serialization.IpAddressSerializer
import io.liquidsoftware.safedomain.serialization.Ipv4AddressSerializer
import io.liquidsoftware.safedomain.serialization.Ipv6AddressSerializer
import io.liquidsoftware.safedomain.serialization.LanguageTagSerializer
import io.liquidsoftware.safedomain.serialization.MoneySerializer
import io.liquidsoftware.safedomain.serialization.NegativeDecimalSerializer
import io.liquidsoftware.safedomain.serialization.NegativeIntSerializer
import io.liquidsoftware.safedomain.serialization.NegativeLongSerializer
import io.liquidsoftware.safedomain.serialization.NegativeMoneySerializer
import io.liquidsoftware.safedomain.serialization.NonBlankStringSerializer
import io.liquidsoftware.safedomain.serialization.NonNegativeDecimalSerializer
import io.liquidsoftware.safedomain.serialization.NonNegativeMoneySerializer
import io.liquidsoftware.safedomain.serialization.PercentageSerializer
import io.liquidsoftware.safedomain.serialization.PhoneSerializer
import io.liquidsoftware.safedomain.serialization.PositiveDecimalSerializer
import io.liquidsoftware.safedomain.serialization.PositiveDurationSerializer
import io.liquidsoftware.safedomain.serialization.PositiveIntSerializer
import io.liquidsoftware.safedomain.serialization.PositiveLongSerializer
import io.liquidsoftware.safedomain.serialization.PositiveMoneySerializer
import io.liquidsoftware.safedomain.serialization.SlugSerializer
import io.liquidsoftware.safedomain.serialization.TimeWindowSerializer
import io.liquidsoftware.safedomain.serialization.TimeZoneIdSerializer
import io.liquidsoftware.safedomain.serialization.TypedIdSerializer
import io.liquidsoftware.safedomain.serialization.UniqueListSerializer
import io.liquidsoftware.safedomain.testing.arb
import io.liquidsoftware.safedomain.text.NonBlankString
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * For every type's generator, this module and impossible-serialization write the same
 * JSON, and each reads what the other wrote back to an equal value.
 */
class CrossCheckTest {
    @Suppress("UNCHECKED_CAST")
    private val mapper = ObjectMapper()
        .registerModule(ImpossibleModule())
        .registerModule(
            SimpleModule()
                .addDeserializer(BoundedInt::class.java, BoundedIntDeserializer(-3..7))
                .addDeserializer(BoundedList::class.java as Class<BoundedList<Any?>>, BoundedListDeserializer(1..4)),
        )

    private fun type(raw: Class<*>, vararg parameters: Class<*>): JavaType =
        if (parameters.isEmpty()) mapper.constructType(raw) else mapper.typeFactory.constructParametricType(raw, *parameters)

    /** Numbers compare by value, so `1.0E10` and `10000000000.0` count as the same JSON. */
    private fun normalized(element: JsonElement): Any? = when (element) {
        is JsonObject -> element.mapValues { normalized(it.value) }
        is JsonArray -> element.map { normalized(it) }
        is JsonPrimitive -> if (element.isString) {
            "s:${element.content}"
        } else {
            element.content.toBigDecimalOrNull()?.stripTrailingZeros()
                ?: element.content
        }
    }

    @OptIn(ExperimentalKotest::class)
    private suspend fun <A> agree(serializer: KSerializer<A>, arb: Arb<A>, type: JavaType, iterations: Int = 100) {
        checkAll(PropTestConfig(seed = 20_261_011L, iterations = iterations), arb) { value ->
            val kotlinx = Json.encodeToString(serializer, value)
            val jackson = mapper.writeValueAsString(value)
            assertEquals(normalized(Json.parseToJsonElement(kotlinx)), normalized(Json.parseToJsonElement(jackson)), jackson)
            assertEquals(value, mapper.readValue<A>(kotlinx, type), kotlinx)
            assertEquals(value, Json.decodeFromString(serializer, jackson), jackson)
        }
    }

    @Test
    fun `text contact and locale values agree`() = runTest {
        agree(NonBlankStringSerializer, NonBlankString.arb(), type(NonBlankString::class.java))
        agree(SlugSerializer, Slug.arb(), type(Slug::class.java))
        agree(EmailSerializer, Email.arb(), type(Email::class.java))
        agree(PhoneSerializer, Phone.arb(), type(Phone::class.java))
        agree(CountryCodeSerializer, CountryCode.arb(), type(CountryCode::class.java))
        agree(LanguageTagSerializer, LanguageTag.arb(), type(LanguageTag::class.java))
        agree(TypedIdSerializer<Unit>(), TypedId.arb<Unit>(), type(TypedId::class.java))
    }

    @Test
    fun `numeric values agree exactly`() = runTest {
        agree(DecimalSerializer, Decimal.arb(), type(Decimal::class.java))
        agree(PositiveIntSerializer, PositiveInt.arb(), type(PositiveInt::class.java))
        agree(NegativeIntSerializer, NegativeInt.arb(), type(NegativeInt::class.java))
        agree(PositiveLongSerializer, PositiveLong.arb(), type(PositiveLong::class.java))
        agree(NegativeLongSerializer, NegativeLong.arb(), type(NegativeLong::class.java))
        agree(PositiveDecimalSerializer, PositiveDecimal.arb(), type(PositiveDecimal::class.java))
        agree(NonNegativeDecimalSerializer, NonNegativeDecimal.arb(), type(NonNegativeDecimal::class.java))
        agree(NegativeDecimalSerializer, NegativeDecimal.arb(), type(NegativeDecimal::class.java))
        agree(PercentageSerializer, Percentage.arb(), type(Percentage::class.java))
        agree(BoundedIntSerializer(-3..7), BoundedInt.arb(-3..7), type(BoundedInt::class.java))
    }

    @Test
    fun `money values agree`() = runTest {
        agree(CurrencySerializer, Currency.arb(), type(Currency::class.java))
        agree(MoneySerializer, Money.arb(), type(Money::class.java))
        agree(PositiveMoneySerializer, PositiveMoney.arb(), type(PositiveMoney::class.java))
        agree(NonNegativeMoneySerializer, NonNegativeMoney.arb(), type(NonNegativeMoney::class.java))
        agree(NegativeMoneySerializer, NegativeMoney.arb(), type(NegativeMoney::class.java))
    }

    @Test
    fun `time web geo and collection values agree`() = runTest {
        agree(DateRangeSerializer, DateRange.arb(), type(DateRange::class.java))
        agree(TimeWindowSerializer, TimeWindow.arb(), type(TimeWindow::class.java))
        agree(PositiveDurationSerializer, PositiveDuration.arb(), type(PositiveDuration::class.java))
        agree(TimeZoneIdSerializer, TimeZoneId.arb(), type(TimeZoneId::class.java))
        agree(DomainNameSerializer, DomainName.arb(), type(DomainName::class.java))
        agree(HttpUrlSerializer, HttpUrl.arb(), type(HttpUrl::class.java))
        agree(IpAddressSerializer, IpAddress.arb(), type(IpAddress::class.java))
        agree(Ipv4AddressSerializer, IpAddress.V4.arb(), type(IpAddress.V4::class.java))
        agree(Ipv6AddressSerializer, IpAddress.V6.arb(), type(IpAddress.V6::class.java))
        agree(GeoPointSerializer, GeoPoint.arb(), type(GeoPoint::class.java))
        agree(
            UniqueListSerializer(PositiveIntSerializer),
            UniqueList.arb(PositiveInt.arb()),
            type(UniqueList::class.java, PositiveInt::class.java),
        )
        agree(
            BoundedListSerializer(SlugSerializer, 1..4),
            BoundedList.arb(Slug.arb(), 1..4),
            type(BoundedList::class.java, Slug::class.java),
        )
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (_: NumberFormatException) {
    null
}
