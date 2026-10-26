package io.liquidsoftware.safedomain.jackson

import arrow.core.NonEmptyList
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValueFactory
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
import kotlin.time.Duration
import kotlin.time.Instant

private const val PACKAGE = "io.liquidsoftware.safedomain"

/**
 * Jackson support for every library type, with the same JSON as `impossible-serialization`:
 * text types as strings, integers as numbers, decimals as decimal strings such as `"12.30"`,
 * money as `{"amount": "12.30", "currency": "EUR"}`, ranges and windows as objects with
 * ISO 8601 text, a `GeoPoint` as `{"latitude": .., "longitude": ..}`, and lists as arrays.
 * Every value is read through its type's factory, so invalid JSON data never becomes a
 * value; a rejection throws [InvalidValueException].
 *
 * A `Secret` is read but never written: serializing one throws. `BoundedInt` and
 * `BoundedList` need bounds, so they are written here but read only through a
 * [BoundedIntDeserializer] or [BoundedListDeserializer] you declare per use. `TypedId`'s
 * tag exists only at compile time, so one deserializer serves every tag.
 *
 * Register it once: `ObjectMapper().registerModule(ImpossibleModule())`. Kotlin value
 * classes held in Kotlin classes also need jackson-module-kotlin, which maps them to
 * these (de)serializers.
 */
public class ImpossibleModule : SimpleModule("impossible") {
    init {
        text()
        numbers()
        money()
        time()
        webAndGeo()
        collections()
    }

    private fun <A : Any> add(type: Class<A>, deserializer: JsonDeserializer<out A>?, serializer: JsonSerializer<A>) {
        if (deserializer != null) addDeserializer(type, deserializer)
        addSerializer(type, serializer)
    }

    private fun <A : Any, Error> string(
        type: Class<A>,
        name: String,
        factory: ValueFactory<String, Error, A>,
        render: (A) -> String,
        describe: (Error) -> ErrorMessage,
    ) = add(type, ValidatingDeserializer(name, factory, readString, one(describe)), writeString(render))

    private fun text() {
        string(NonBlankString::class.java, "$PACKAGE.text.NonBlankString", NonBlankString, {
            it.value
        }, ErrorMessages::describe)
        string(Slug::class.java, "$PACKAGE.text.Slug", Slug, { it.value }, ErrorMessages::describe)
        add(
            Secret::class.java,
            ValidatingDeserializer("$PACKAGE.text.Secret", Secret, readString, one(ErrorMessages::describe)),
            object : JsonSerializer<Secret>() {
                override fun serialize(value: Secret, gen: JsonGenerator, serializers: SerializerProvider): Unit =
                    throw JsonMappingException.from(gen, "A Secret is never serialized; map it to a safe form first")
            },
        )
        string(Email::class.java, "$PACKAGE.contact.Email", Email, { it.value }, ErrorMessages::describe)
        string(Phone::class.java, "$PACKAGE.contact.Phone", Phone, { it.value }, ErrorMessages::describe)
        string(CountryCode::class.java, "$PACKAGE.locale.CountryCode", CountryCode, {
            it.value
        }, ErrorMessages::describe)
        string(LanguageTag::class.java, "$PACKAGE.locale.LanguageTag", LanguageTag, {
            it.value
        }, ErrorMessages::describe)
        @Suppress("UNCHECKED_CAST") // The tag is a compile-time phantom; every TypedId has the same runtime class.
        string(
            TypedId::class.java as Class<TypedId<Any?>>,
            "$PACKAGE.identity.TypedId",
            TypedId.factory<Any?>(),
            { it.value.toString() },
            ErrorMessages::describe,
        )
    }

    private fun numbers() {
        add(Decimal::class.java, DecimalDeserializer, writeString { it.toString() })
        add(
            PositiveInt::class.java,
            ValidatingDeserializer("$PACKAGE.numeric.PositiveInt", PositiveInt, readInt, one(ErrorMessages::describe)),
            writeInt { it.value },
        )
        add(
            NegativeInt::class.java,
            ValidatingDeserializer("$PACKAGE.numeric.NegativeInt", NegativeInt, readInt, one(ErrorMessages::describe)),
            writeInt { it.value },
        )
        add(
            PositiveLong::class.java,
            ValidatingDeserializer("$PACKAGE.numeric.PositiveLong", PositiveLong, readLong, one(ErrorMessages::describe)),
            writeLong { it.value },
        )
        add(
            NegativeLong::class.java,
            ValidatingDeserializer("$PACKAGE.numeric.NegativeLong", NegativeLong, readLong, one(ErrorMessages::describe)),
            writeLong { it.value },
        )
        decimal(PositiveDecimal::class.java, "$PACKAGE.numeric.PositiveDecimal", PositiveDecimal, {
            it.value
        }, ErrorMessages::describe)
        decimal(
            NonNegativeDecimal::class.java,
            "$PACKAGE.numeric.NonNegativeDecimal",
            NonNegativeDecimal,
            { it.value },
            ErrorMessages::describe,
        )
        decimal(NegativeDecimal::class.java, "$PACKAGE.numeric.NegativeDecimal", NegativeDecimal, {
            it.value
        }, ErrorMessages::describe)
        decimal(Percentage::class.java, "$PACKAGE.numeric.Percentage", Percentage, {
            it.value
        }, ErrorMessages::describe)
        add(BoundedInt::class.java, null, writeInt { it.value })
    }

    private fun <A : Any, Error> decimal(
        type: Class<A>,
        name: String,
        factory: ValueFactory<Decimal, Error, A>,
        render: (A) -> Decimal,
        describe: (Error) -> ErrorMessage,
    ) = add(
        type,
        ValidatingDeserializer(name, factory, readDecimal, one(describe)),
        writeString {
            render(it).toString()
        },
    )

    private fun money() {
        string(Currency::class.java, "$PACKAGE.money.Currency", Currency, { it.code }, ErrorMessages::describe)
        add(
            Money::class.java,
            ValidatingDeserializer("$PACKAGE.money.Money", Money.parser, readMoneyText) { errors ->
                errors.map(ErrorMessages::describe)
            },
            writeMoney { it },
        )
        add(
            PositiveMoney::class.java,
            refinedMoney("$PACKAGE.money.PositiveMoney", PositiveMoney, ErrorMessages::describe),
            writeMoney { it.money },
        )
        add(
            NonNegativeMoney::class.java,
            refinedMoney("$PACKAGE.money.NonNegativeMoney", NonNegativeMoney, ErrorMessages::describe),
            writeMoney { it.money },
        )
        add(
            NegativeMoney::class.java,
            refinedMoney("$PACKAGE.money.NegativeMoney", NegativeMoney, ErrorMessages::describe),
            writeMoney { it.money },
        )
    }

    private fun time() {
        add(
            DateRange::class.java,
            ValidatingDeserializer(
                "$PACKAGE.time.DateRange",
                DateRange,
                readObject("start", "endInclusive") {
                    DateRange.Input(parsed("start", "date", LocalDate::parse), parsed("endInclusive", "date", LocalDate::parse))
                },
                one(ErrorMessages::describe),
            ),
            writeObject { range, gen ->
                gen.writeStringField("start", range.start.toString())
                gen.writeStringField("endInclusive", range.endInclusive.toString())
            },
        )
        add(
            TimeWindow::class.java,
            ValidatingDeserializer(
                "$PACKAGE.time.TimeWindow",
                TimeWindow,
                readObject("start", "endExclusive") {
                    TimeWindow.Input(parsed("start", "instant", Instant::parse), parsed("endExclusive", "instant", Instant::parse))
                },
                one(ErrorMessages::describe),
            ),
            writeObject { window, gen ->
                gen.writeStringField("start", window.start.toString())
                gen.writeStringField("endExclusive", window.endExclusive.toString())
            },
        )
        add(
            PositiveDuration::class.java,
            ValidatingDeserializer(
                "$PACKAGE.time.PositiveDuration",
                PositiveDuration,
                readParsed("duration", Duration::parseIsoString),
                one(ErrorMessages::describe),
            ),
            writeString { it.value.toIsoString() },
        )
        string(TimeZoneId::class.java, "$PACKAGE.time.TimeZoneId", TimeZoneId, { it.value }, ErrorMessages::describe)
    }

    private fun webAndGeo() {
        string(DomainName::class.java, "$PACKAGE.web.DomainName", DomainName, { it.value }, ErrorMessages::describe)
        string(HttpUrl::class.java, "$PACKAGE.web.HttpUrl", HttpUrl, { it.value }, ErrorMessages::describe)
        string(IpAddress::class.java, "$PACKAGE.web.IpAddress", IpAddress, { it.toString() }, ErrorMessages::describe)
        string(IpAddress.V4::class.java, "$PACKAGE.web.IpAddress.V4", IpAddress.V4, {
            it.toString()
        }, ErrorMessages::describe)
        string(IpAddress.V6::class.java, "$PACKAGE.web.IpAddress.V6", IpAddress.V6, {
            it.toString()
        }, ErrorMessages::describe)
        add(
            GeoPoint::class.java,
            ValidatingDeserializer(
                "$PACKAGE.geo.GeoPoint",
                GeoPoint,
                readObject("latitude", "longitude") { GeoPoint.Input(double("latitude"), double("longitude")) },
            ) { errors -> errors.map(ErrorMessages::describe) },
            writeObject { point, gen ->
                gen.writeNumberField("latitude", point.latitude)
                gen.writeNumberField("longitude", point.longitude)
            },
        )
    }

    @Suppress("UNCHECKED_CAST") // Element types are erased; the list classes are the same for every element type.
    private fun collections() {
        add(
            UniqueList::class.java as Class<UniqueList<Any?>>,
            UniqueListDeserializer(null),
            jacksonSerializer { list: UniqueList<Any?> -> list.value },
        )
        add(
            BoundedList::class.java as Class<BoundedList<Any?>>,
            null,
            jacksonSerializer { list: BoundedList<Any?> ->
                list.value
            },
        )
    }
}

/**
 * Reads a [BoundedInt] within [bounds]. The bounds are configuration, not data, so declare
 * one subclass per use and name it on the property:
 * `class RatingDeserializer : BoundedIntDeserializer(1..5)` with
 * `@JsonDeserialize(using = RatingDeserializer::class)`.
 */
public open class BoundedIntDeserializer(bounds: IntRange) : JsonDeserializer<BoundedInt>() {
    private val delegate = ValidatingDeserializer(
        "$PACKAGE.numeric.BoundedInt",
        BoundedInt.factory(bounds),
        readInt,
        one(ErrorMessages::describe),
    )

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BoundedInt = delegate.deserialize(p, ctxt)
}

/**
 * Reads a [BoundedList] whose size is within [bounds], with elements of the property's
 * declared element type. Like [BoundedIntDeserializer], declare one subclass per use.
 */
public open class BoundedListDeserializer(private val bounds: IntRange) :
    JsonDeserializer<BoundedList<Any?>>(),
    ContextualDeserializer {
    override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> =
        ElementsOf(elementType(ctxt, property)) { elements ->
            ValidatingDeserializer(
                "$PACKAGE.collections.BoundedList",
                BoundedList.factory<Any?>(bounds),
                elements,
                one(ErrorMessages::describe),
            )
        }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BoundedList<Any?> =
        ValidatingDeserializer(
            "$PACKAGE.collections.BoundedList",
            BoundedList.factory<Any?>(bounds),
            readList(null),
            one(ErrorMessages::describe),
        ).deserialize(p, ctxt)
}

internal class UniqueListDeserializer(private val element: JavaType?) :
    JsonDeserializer<UniqueList<Any?>>(),
    ContextualDeserializer {
    override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> =
        UniqueListDeserializer(elementType(ctxt, property))

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UniqueList<Any?> =
        ValidatingDeserializer("$PACKAGE.collections.UniqueList", UniqueList.factory<Any?>(), readList(element), describeAll)
            .deserialize(p, ctxt)
}

/** A list deserializer fixed to one element type, built when Jackson knows the property. */
private class ElementsOf<A>(element: JavaType?, build: (Reader<List<Any?>>) -> JsonDeserializer<A>) : JsonDeserializer<A>() {
    private val delegate = build(readList(element))

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): A = delegate.deserialize(p, ctxt)
}

private fun elementType(ctxt: DeserializationContext, property: BeanProperty?): JavaType? =
    (ctxt.contextualType ?: property?.type)?.containedType(0)

private fun readList(element: JavaType?): Reader<List<Any?>> = { p, ctxt ->
    if (p.currentToken != com.fasterxml.jackson.core.JsonToken.START_ARRAY) mismatch(p, "Expected a JSON array")
    val type = ctxt.typeFactory.constructCollectionType(List::class.java, element ?: ctxt.constructType(Any::class.java))
    ctxt.readValue(p, type)
}

private val readDecimal: Reader<Decimal> = { p, ctxt ->
    val text = readString(p, ctxt)
    Decimal.parseOrNull(text) ?: throw InvalidValueException(
        p,
        "$PACKAGE.numeric.Decimal",
        null,
        listOf(ErrorMessage("decimal.invalid_format", "The value is not a decimal number.")),
        pointerOf(p),
    )
}

private object DecimalDeserializer : JsonDeserializer<Decimal>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Decimal = readDecimal(p, ctxt)
}

private val readMoneyText: Reader<Money.Text> = readObject("amount", "currency") {
    Money.Text(string("amount"), string("currency"))
}

private fun <A, Error> refinedMoney(name: String, factory: ValueFactory<Money, Error, A>, describe: (Error) -> ErrorMessage) =
    ValidatingDeserializer(name, factory, readMoney, one(describe))

private val moneyDeserializer =
    ValidatingDeserializer("$PACKAGE.money.Money", Money.parser, readMoneyText) { errors ->
        errors.map(ErrorMessages::describe)
    }

private val readMoney: Reader<Money> = { p, ctxt -> moneyDeserializer.deserialize(p, ctxt) }

private val describeAll: (NonEmptyList<UniqueList.Error>) -> List<ErrorMessage> = { errors ->
    errors.map(ErrorMessages::describe)
}

private fun <E> one(describe: (E) -> ErrorMessage): (E) -> List<ErrorMessage> = { listOf(describe(it)) }

private fun <A> writeString(render: (A) -> String): JsonSerializer<A> = object : JsonSerializer<A>() {
    override fun serialize(value: A, gen: JsonGenerator, serializers: SerializerProvider) = gen.writeString(render(value))
}

private fun <A> writeInt(render: (A) -> Int): JsonSerializer<A> = object : JsonSerializer<A>() {
    override fun serialize(value: A, gen: JsonGenerator, serializers: SerializerProvider) = gen.writeNumber(render(value))
}

private fun <A> writeLong(render: (A) -> Long): JsonSerializer<A> = object : JsonSerializer<A>() {
    override fun serialize(value: A, gen: JsonGenerator, serializers: SerializerProvider) = gen.writeNumber(render(value))
}

private fun <A> writeObject(fields: (A, JsonGenerator) -> Unit): JsonSerializer<A> = object : JsonSerializer<A>() {
    override fun serialize(value: A, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        fields(value, gen)
        gen.writeEndObject()
    }
}

private fun <A> writeMoney(money: (A) -> Money): JsonSerializer<A> = writeObject { value, gen ->
    val amount = money(value)
    gen.writeStringField("amount", amount.amount.toPlainString())
    gen.writeStringField("currency", amount.currency.code)
}
