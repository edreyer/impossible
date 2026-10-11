package io.liquidsoftware.safedomain.serialization

import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
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
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Instant

private const val PACKAGE = "io.liquidsoftware.safedomain"

private fun <E> one(describe: (E) -> ErrorMessage): (E) -> List<ErrorMessage> =
    { listOf(describe(it)) }

// Text

/** [NonBlankString] as its exact text. */
object NonBlankStringSerializer : KSerializer<NonBlankString> by NonBlankString.asSerializer(
    "$PACKAGE.text.NonBlankString",
    String.serializer(),
    NonBlankString::value,
    one(ErrorMessages::describe),
)

/** [Slug] as its text. */
object SlugSerializer : KSerializer<Slug> by Slug.asSerializer(
    "$PACKAGE.text.Slug",
    String.serializer(),
    Slug::value,
    one(ErrorMessages::describe),
)

/**
 * Reads a [Secret] from text and refuses to write one: serializing a secret would put
 * it in a response, a log, or a cache. Map it to what the output needs (a hash, a
 * masked hint) before encoding.
 */
object SecretSerializer : KSerializer<Secret> {
    private val reader = Secret.asSerializer(
        "$PACKAGE.text.Secret",
        String.serializer(),
        Secret::reveal,
        one(ErrorMessages::describe),
    )
    override val descriptor: SerialDescriptor = reader.descriptor
    override fun deserialize(decoder: Decoder): Secret = reader.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Secret): Unit =
        throw SerializationException("A Secret is never serialized; map it to a safe form first")
}

// Contact and locale

/** [Email] as the address it was created from. */
object EmailSerializer : KSerializer<Email> by Email.asSerializer(
    "$PACKAGE.contact.Email",
    String.serializer(),
    Email::value,
    one(ErrorMessages::describe),
)

/** [Phone] as E.164 text. */
object PhoneSerializer : KSerializer<Phone> by Phone.asSerializer(
    "$PACKAGE.contact.Phone",
    String.serializer(),
    Phone::value,
    one(ErrorMessages::describe),
)

/** [CountryCode] as its ISO 3166-1 alpha-2 code. */
object CountryCodeSerializer : KSerializer<CountryCode> by CountryCode.asSerializer(
    "$PACKAGE.locale.CountryCode",
    String.serializer(),
    CountryCode::value,
    one(ErrorMessages::describe),
)

/** [LanguageTag] as its BCP 47 text. */
object LanguageTagSerializer : KSerializer<LanguageTag> by LanguageTag.asSerializer(
    "$PACKAGE.locale.LanguageTag",
    String.serializer(),
    LanguageTag::value,
    one(ErrorMessages::describe),
)

// Identity

/**
 * [TypedId] as canonical UUID text. The tag exists only at compile time, so one
 * serializer serves every tag; use it as `@Serializable(with = TypedIdSerializer::class)`.
 */
class TypedIdSerializer<Tag> : KSerializer<TypedId<Tag>> by TypedId.factory<Tag>().asSerializer(
    "$PACKAGE.identity.TypedId",
    String.serializer(),
    { it.value.toString() },
    one(ErrorMessages::describe),
)

// Numeric

/**
 * [Decimal] as a JSON string such as `"12.30"`. Text keeps every digit and the scale on
 * every platform, where a JSON number would pass through a double on JS.
 */
object DecimalSerializer : KSerializer<Decimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("$PACKAGE.numeric.Decimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Decimal) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Decimal {
        val text = decoder.decodeString()
        return Decimal.parseOrNull(text) ?: throw InvalidValueException(
            "$PACKAGE.numeric.Decimal",
            text,
            listOf(ErrorMessage("decimal.invalid_format", "The value is not a decimal number.")),
        )
    }
}

/** [PositiveInt] as a number. */
object PositiveIntSerializer : KSerializer<PositiveInt> by PositiveInt.asSerializer(
    "$PACKAGE.numeric.PositiveInt",
    Int.serializer(),
    PositiveInt::value,
    one(ErrorMessages::describe),
)

/** [NegativeInt] as a number. */
object NegativeIntSerializer : KSerializer<NegativeInt> by NegativeInt.asSerializer(
    "$PACKAGE.numeric.NegativeInt",
    Int.serializer(),
    NegativeInt::value,
    one(ErrorMessages::describe),
)

/** [PositiveLong] as a number. */
object PositiveLongSerializer : KSerializer<PositiveLong> by PositiveLong.asSerializer(
    "$PACKAGE.numeric.PositiveLong",
    Long.serializer(),
    PositiveLong::value,
    one(ErrorMessages::describe),
)

/** [NegativeLong] as a number. */
object NegativeLongSerializer : KSerializer<NegativeLong> by NegativeLong.asSerializer(
    "$PACKAGE.numeric.NegativeLong",
    Long.serializer(),
    NegativeLong::value,
    one(ErrorMessages::describe),
)

/** [PositiveDecimal] as decimal text, like [DecimalSerializer]. */
object PositiveDecimalSerializer : KSerializer<PositiveDecimal> by PositiveDecimal.asSerializer(
    "$PACKAGE.numeric.PositiveDecimal",
    DecimalSerializer,
    PositiveDecimal::value,
    one(ErrorMessages::describe),
)

/** [NonNegativeDecimal] as decimal text, like [DecimalSerializer]. */
object NonNegativeDecimalSerializer : KSerializer<NonNegativeDecimal> by NonNegativeDecimal.asSerializer(
    "$PACKAGE.numeric.NonNegativeDecimal",
    DecimalSerializer,
    NonNegativeDecimal::value,
    one(ErrorMessages::describe),
)

/** [NegativeDecimal] as decimal text, like [DecimalSerializer]. */
object NegativeDecimalSerializer : KSerializer<NegativeDecimal> by NegativeDecimal.asSerializer(
    "$PACKAGE.numeric.NegativeDecimal",
    DecimalSerializer,
    NegativeDecimal::value,
    one(ErrorMessages::describe),
)

/** [Percentage] as decimal text, like [DecimalSerializer]. */
object PercentageSerializer : KSerializer<Percentage> by Percentage.asSerializer(
    "$PACKAGE.numeric.Percentage",
    DecimalSerializer,
    Percentage::value,
    one(ErrorMessages::describe),
)

/**
 * [BoundedInt] as a number, checked against [bounds]. The bounds are configuration, not
 * data, so name a serializer per use: `object RatingSerializer : KSerializer<BoundedInt> by BoundedIntSerializer(1..5)`.
 */
class BoundedIntSerializer(bounds: IntRange) : KSerializer<BoundedInt> by BoundedInt.factory(bounds).asSerializer(
    "$PACKAGE.numeric.BoundedInt",
    Int.serializer(),
    BoundedInt::value,
    one(ErrorMessages::describe),
)

// Money

/** [Currency] as its ISO 4217 code. */
object CurrencySerializer : KSerializer<Currency> by Currency.asSerializer(
    "$PACKAGE.money.Currency",
    String.serializer(),
    Currency::code,
    one(ErrorMessages::describe),
)

@Serializable
@SerialName("$PACKAGE.money.Money.Text")
private class MoneyText(val amount: String, val currency: String)

/**
 * [Money] as `{"amount": "12.30", "currency": "EUR"}`. The amount is decimal text so no
 * platform rounds it; both fields are checked and every problem is reported.
 */
object MoneySerializer : KSerializer<Money> by Money.parser.asSerializer(
    "$PACKAGE.money.Money",
    MoneyText.serializer().mapped(
        "$PACKAGE.money.Money.Input",
        { Money.Text(it.amount, it.currency) },
        { MoneyText(it.amount, it.currency) },
    ),
    { Money.Text(it.amount.toPlainString(), it.currency.code) },
    { errors -> errors.map(ErrorMessages::describe) },
)

/** [PositiveMoney] in the same form as [MoneySerializer]. */
object PositiveMoneySerializer : KSerializer<PositiveMoney> by PositiveMoney.asSerializer(
    "$PACKAGE.money.PositiveMoney",
    MoneySerializer,
    PositiveMoney::money,
    one(ErrorMessages::describe),
)

/** [NonNegativeMoney] in the same form as [MoneySerializer]. */
object NonNegativeMoneySerializer : KSerializer<NonNegativeMoney> by NonNegativeMoney.asSerializer(
    "$PACKAGE.money.NonNegativeMoney",
    MoneySerializer,
    NonNegativeMoney::money,
    one(ErrorMessages::describe),
)

/** [NegativeMoney] in the same form as [MoneySerializer]. */
object NegativeMoneySerializer : KSerializer<NegativeMoney> by NegativeMoney.asSerializer(
    "$PACKAGE.money.NegativeMoney",
    MoneySerializer,
    NegativeMoney::money,
    one(ErrorMessages::describe),
)

// Time

@Serializable
@SerialName("$PACKAGE.time.DateRange.Input")
private class DateRangeInput(val start: LocalDate, val endInclusive: LocalDate)

/** [DateRange] as `{"start": "2026-01-01", "endInclusive": "2026-01-31"}`. */
object DateRangeSerializer : KSerializer<DateRange> by DateRange.asSerializer(
    "$PACKAGE.time.DateRange",
    DateRangeInput.serializer().mapped(
        "$PACKAGE.time.DateRange.Fields",
        { DateRange.Input(it.start, it.endInclusive) },
        { DateRangeInput(it.start, it.endInclusive) },
    ),
    { DateRange.Input(it.start, it.endInclusive) },
    one(ErrorMessages::describe),
)

@Serializable
@SerialName("$PACKAGE.time.TimeWindow.Input")
private class TimeWindowInput(val start: Instant, val endExclusive: Instant)

/** [TimeWindow] as `{"start": "...Z", "endExclusive": "...Z"}` with ISO 8601 instants. */
object TimeWindowSerializer : KSerializer<TimeWindow> by TimeWindow.asSerializer(
    "$PACKAGE.time.TimeWindow",
    TimeWindowInput.serializer().mapped(
        "$PACKAGE.time.TimeWindow.Fields",
        { TimeWindow.Input(it.start, it.endExclusive) },
        { TimeWindowInput(it.start, it.endExclusive) },
    ),
    { TimeWindow.Input(it.start, it.endExclusive) },
    one(ErrorMessages::describe),
)

/** [PositiveDuration] as ISO 8601 duration text such as `"PT1H30M"`. */
object PositiveDurationSerializer : KSerializer<PositiveDuration> by PositiveDuration.asSerializer(
    "$PACKAGE.time.PositiveDuration",
    Duration.serializer(),
    PositiveDuration::value,
    one(ErrorMessages::describe),
)

/** [TimeZoneId] as its IANA id. */
object TimeZoneIdSerializer : KSerializer<TimeZoneId> by TimeZoneId.asSerializer(
    "$PACKAGE.time.TimeZoneId",
    String.serializer(),
    TimeZoneId::value,
    one(ErrorMessages::describe),
)

// Web and geo

/** [DomainName] as the name it was created from. */
object DomainNameSerializer : KSerializer<DomainName> by DomainName.asSerializer(
    "$PACKAGE.web.DomainName",
    String.serializer(),
    DomainName::value,
    one(ErrorMessages::describe),
)

/** [HttpUrl] as its text. */
object HttpUrlSerializer : KSerializer<HttpUrl> by HttpUrl.asSerializer(
    "$PACKAGE.web.HttpUrl",
    String.serializer(),
    HttpUrl::value,
    one(ErrorMessages::describe),
)

/** Either kind of [IpAddress] as its canonical text. */
object IpAddressSerializer : KSerializer<IpAddress> by IpAddress.asSerializer(
    "$PACKAGE.web.IpAddress",
    String.serializer(),
    IpAddress::toString,
    one(ErrorMessages::describe),
)

/** [IpAddress.V4] as dotted quad text. */
object Ipv4AddressSerializer : KSerializer<IpAddress.V4> by IpAddress.V4.asSerializer(
    "$PACKAGE.web.IpAddress.V4",
    String.serializer(),
    IpAddress.V4::toString,
    one(ErrorMessages::describe),
)

/** [IpAddress.V6] as canonical RFC 5952 text. */
object Ipv6AddressSerializer : KSerializer<IpAddress.V6> by IpAddress.V6.asSerializer(
    "$PACKAGE.web.IpAddress.V6",
    String.serializer(),
    IpAddress.V6::toString,
    one(ErrorMessages::describe),
)

@Serializable
@SerialName("$PACKAGE.geo.GeoPoint.Input")
private class GeoPointInput(val latitude: Double, val longitude: Double)

/** [GeoPoint] as `{"latitude": 52.37, "longitude": 4.89}`; both coordinates are checked. */
object GeoPointSerializer : KSerializer<GeoPoint> by GeoPoint.asSerializer(
    "$PACKAGE.geo.GeoPoint",
    GeoPointInput.serializer().mapped(
        "$PACKAGE.geo.GeoPoint.Fields",
        { GeoPoint.Input(it.latitude, it.longitude) },
        { GeoPointInput(it.latitude, it.longitude) },
    ),
    { GeoPoint.Input(it.latitude, it.longitude) },
    { errors -> errors.map(ErrorMessages::describe) },
)

// Collections

/** [UniqueList] as a JSON array whose elements must be distinct. */
class UniqueListSerializer<A>(element: KSerializer<A>) : KSerializer<UniqueList<A>> by UniqueList.factory<A>().asSerializer(
    "$PACKAGE.collections.UniqueList",
    ListSerializer(element),
    UniqueList<A>::value,
    { errors -> errors.map(ErrorMessages::describe) },
)

/**
 * [BoundedList] as a JSON array whose size must be within [bounds]. Like
 * [BoundedIntSerializer], name a serializer per use.
 */
class BoundedListSerializer<A>(element: KSerializer<A>, bounds: IntRange) : KSerializer<BoundedList<A>> by BoundedList.factory<A>(
    bounds,
).asSerializer(
    "$PACKAGE.collections.BoundedList",
    ListSerializer(element),
    BoundedList<A>::value,
    one(ErrorMessages::describe),
)
