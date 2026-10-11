package io.liquidsoftware.safedomain.serialization

import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.geo.GeoPoint
import io.liquidsoftware.safedomain.locale.CountryCode
import io.liquidsoftware.safedomain.locale.LanguageTag
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.NegativeMoney
import io.liquidsoftware.safedomain.money.NonNegativeMoney
import io.liquidsoftware.safedomain.money.PositiveMoney
import io.liquidsoftware.safedomain.numeric.Decimal
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
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.PositiveDuration
import io.liquidsoftware.safedomain.time.TimeWindow
import io.liquidsoftware.safedomain.time.TimeZoneId
import io.liquidsoftware.safedomain.web.DomainName
import io.liquidsoftware.safedomain.web.HttpUrl
import io.liquidsoftware.safedomain.web.IpAddress
import kotlinx.serialization.modules.SerializersModule

/**
 * Contextual serializers for every type whose wire form needs no configuration, so a
 * class can mark fields `@Contextual val email: Email` and a format built with
 * `Json { serializersModule = safeDomainSerializersModule }` validates them on decode.
 * [io.liquidsoftware.safedomain.numeric.BoundedInt] and
 * [io.liquidsoftware.safedomain.collections.BoundedList] need bounds, so they are not included; register
 * your own [BoundedIntSerializer] or [BoundedListSerializer] for each use.
 * [io.liquidsoftware.safedomain.identity.TypedId] is not included either: the
 * serialization plugin (Kotlin 2.4.21) crashes on `@Contextual` for a generic value
 * class, so mark those fields `@Serializable(with = TypedIdSerializer::class)`.
 */
val safeDomainSerializersModule: SerializersModule = SerializersModule {
    contextual(NonBlankString::class, NonBlankStringSerializer)
    contextual(Slug::class, SlugSerializer)
    contextual(Secret::class, SecretSerializer)
    contextual(Email::class, EmailSerializer)
    contextual(Phone::class, PhoneSerializer)
    contextual(CountryCode::class, CountryCodeSerializer)
    contextual(LanguageTag::class, LanguageTagSerializer)
    contextual(Decimal::class, DecimalSerializer)
    contextual(PositiveInt::class, PositiveIntSerializer)
    contextual(NegativeInt::class, NegativeIntSerializer)
    contextual(PositiveLong::class, PositiveLongSerializer)
    contextual(NegativeLong::class, NegativeLongSerializer)
    contextual(PositiveDecimal::class, PositiveDecimalSerializer)
    contextual(NonNegativeDecimal::class, NonNegativeDecimalSerializer)
    contextual(NegativeDecimal::class, NegativeDecimalSerializer)
    contextual(Percentage::class, PercentageSerializer)
    contextual(Currency::class, CurrencySerializer)
    contextual(Money::class, MoneySerializer)
    contextual(PositiveMoney::class, PositiveMoneySerializer)
    contextual(NonNegativeMoney::class, NonNegativeMoneySerializer)
    contextual(NegativeMoney::class, NegativeMoneySerializer)
    contextual(DateRange::class, DateRangeSerializer)
    contextual(TimeWindow::class, TimeWindowSerializer)
    contextual(PositiveDuration::class, PositiveDurationSerializer)
    contextual(TimeZoneId::class, TimeZoneIdSerializer)
    contextual(DomainName::class, DomainNameSerializer)
    contextual(HttpUrl::class, HttpUrlSerializer)
    contextual(IpAddress::class, IpAddressSerializer)
    contextual(IpAddress.V4::class, Ipv4AddressSerializer)
    contextual(IpAddress.V6::class, Ipv6AddressSerializer)
    contextual(GeoPoint::class, GeoPointSerializer)
    contextual(UniqueList::class) { args -> UniqueListSerializer(args[0]) }
}
