package io.liquidsoftware.safedomain.serialization

import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.geo.GeoPoint
import io.liquidsoftware.safedomain.identity.TypedId
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.PositiveMoney
import io.liquidsoftware.safedomain.numeric.BoundedInt
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.Secret
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.PositiveDuration
import io.liquidsoftware.safedomain.time.TimeWindow
import io.liquidsoftware.safedomain.web.IpAddress
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private class User

private object RatingSerializer : KSerializer<BoundedInt> by BoundedIntSerializer(1..5)

@Serializable
private data class Signup(
    @Serializable(with = EmailSerializer::class) val email: Email,
    @Serializable(with = SecretSerializer::class) val password: Secret,
    @Serializable(with = TypedIdSerializer::class) val referrer: TypedId<User>? = null,
)

@Serializable
private data class Order(
    @Serializable(with = PositiveIntSerializer::class) val quantity: PositiveInt,
    @Serializable(with = PositiveMoneySerializer::class) val price: PositiveMoney,
    @Serializable(with = RatingSerializer::class) val rating: BoundedInt,
)

@Serializable
private data class Profile(
    @Contextual val email: Email,
    @Contextual val total: Money,
    @Contextual val tags: UniqueList<String>,
)

class SerializersTest {
    private val json = Json { serializersModule = safeDomainSerializersModule }
    private val eur = Currency.fromTrusted("EUR")

    private inline fun <reified T> rejects(text: String): InvalidValueException =
        assertFailsWith<InvalidValueException> { json.decodeFromString<T>(text) }

    @Test
    fun `values are written in their plain wire form`() {
        val order = Order(
            PositiveInt.fromTrusted(3),
            PositiveMoney.fromTrusted(Money.fromTrusted(Money.Input(Decimal.parse("12.30"), "EUR"))),
            BoundedInt.factory(1..5).fromTrusted(4),
        )
        val text = json.encodeToString(Order.serializer(), order)
        assertEquals("""{"quantity":3,"price":{"amount":"12.30","currency":"EUR"},"rating":4}""", text)
        assertEquals(order, json.decodeFromString(Order.serializer(), text))
    }

    @Test
    fun `decoding runs the factory so invalid data never becomes a value`() {
        val error = rejects<Signup>("""{"email":"not-an-email","password":"pw"}""")
        assertEquals("io.liquidsoftware.safedomain.contact.Email", error.serialName)
        assertEquals(listOf("email.invalid_format"), error.messages.map { it.code })
        assertTrue(error.error is Email.Error)
        assertEquals(
            listOf("positive_int.non_positive"),
            rejects<Order>("""{"quantity":0,"price":{"amount":"1","currency":"EUR"},"rating":1}""").messages.map {
                it.code
            },
        )
        assertEquals(
            "io.liquidsoftware.safedomain.numeric.BoundedInt",
            rejects<Order>("""{"quantity":1,"price":{"amount":"1","currency":"EUR"},"rating":6}""").serialName,
        )
    }

    @Test
    fun `failure messages name codes and never the rejected input`() {
        val error = rejects<Signup>("""{"email":"jane.doe@@example.com","password":"pw"}""")
        assertFalse(error.message.orEmpty().contains("jane"))
        assertEquals("Invalid io.liquidsoftware.safedomain.contact.Email: email.invalid_format", error.message)
    }

    @Test
    fun `money reports every problem in one failure`() {
        val error = rejects<Order>("""{"quantity":1,"price":{"amount":"abc","currency":"XXX1"},"rating":1}""")
        assertEquals(2, error.messages.size)
    }

    @Test
    fun `refined money rejects a valid amount with the wrong sign`() {
        val error = rejects<Order>("""{"quantity":1,"price":{"amount":"0","currency":"EUR"},"rating":1}""")
        assertEquals("io.liquidsoftware.safedomain.money.PositiveMoney", error.serialName)
    }

    @Test
    fun `secrets are read but never written`() {
        val signup = json.decodeFromString(Signup.serializer(), """{"email":"a@example.com","password":"hunter2"}""")
        assertEquals("hunter2", signup.password.reveal())
        assertFailsWith<SerializationException> { json.encodeToString(Signup.serializer(), signup) }
    }

    @Test
    fun `typed ids serialize as uuid text for any tag`() {
        val text = """{"email":"a@example.com","password":"x","referrer":"6f1c2c4e-8b1d-4a8e-9f6a-0c2b1e3d4f5a"}"""
        val signup = json.decodeFromString(Signup.serializer(), text)
        assertEquals("6f1c2c4e-8b1d-4a8e-9f6a-0c2b1e3d4f5a", signup.referrer?.value.toString())
        assertEquals(
            listOf("typed_id.invalid_format"),
            rejects<Signup>("""{"email":"a@example.com","password":"x","referrer":"nope"}""").messages.map { it.code },
        )
    }

    @Test
    fun `the contextual module covers fields marked Contextual`() {
        val text = """{"email":"a@example.com","total":{"amount":"-5.00","currency":"EUR"},"tags":["a","b"]}"""
        val value = json.decodeFromString(Profile.serializer(), text)
        assertEquals(Money.fromTrusted(Money.Input(Decimal.parse("-5"), "EUR")), value.total)
        assertEquals(text, json.encodeToString(Profile.serializer(), value))
        assertEquals(
            listOf("unique_list.duplicate"),
            rejects<Profile>(text.replace("\"b\"", "\"a\"")).messages.map {
                it.code
            },
        )
    }

    @Test
    fun `composite values round trip`() {
        val range = DateRange.fromTrusted(DateRange.Input(LocalDate(2026, 1, 1), LocalDate(2026, 1, 31)))
        assertEquals("""{"start":"2026-01-01","endInclusive":"2026-01-31"}""", json.encodeToString(DateRangeSerializer, range))
        assertEquals(range, json.decodeFromString(DateRangeSerializer, json.encodeToString(DateRangeSerializer, range)))
        assertFailsWith<InvalidValueException> {
            json.decodeFromString(
                DateRangeSerializer,
                """{"start":"2026-02-01","endInclusive":"2026-01-31"}""",
            )
        }

        val window = TimeWindow.fromTrusted(TimeWindow.Input(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T01:00:00Z")))
        assertEquals(window, json.decodeFromString(TimeWindowSerializer, json.encodeToString(TimeWindowSerializer, window)))

        val point = GeoPoint.fromTrusted(GeoPoint.Input(52.37, 4.89))
        assertEquals("""{"latitude":52.37,"longitude":4.89}""", json.encodeToString(GeoPointSerializer, point))
        assertEquals(
            2,
            assertFailsWith<InvalidValueException> {
                json.decodeFromString(
                    GeoPointSerializer,
                    """{"latitude":91.0,"longitude":181.0}""",
                )
            }.messages.size,
        )

        val duration = PositiveDuration.fromTrusted(90.minutes)
        assertEquals("\"PT1H30M\"", json.encodeToString(PositiveDurationSerializer, duration))
        assertEquals(duration, json.decodeFromString(PositiveDurationSerializer, "\"PT1H30M\""))
    }

    @Test
    fun `decimals keep every digit and their scale`() {
        val big = Decimal.parse("12345678901234567890.120")
        assertEquals("\"12345678901234567890.120\"", json.encodeToString(DecimalSerializer, big))
        assertEquals(big, json.decodeFromString(DecimalSerializer, "\"12345678901234567890.120\""))
        assertEquals(
            listOf("decimal.invalid_format"),
            assertFailsWith<InvalidValueException> {
                json.decodeFromString(DecimalSerializer, "\"1,5\"")
            }.messages.map { it.code },
        )
    }

    @Test
    fun `ip addresses are written in canonical form`() {
        val address = json.decodeFromString(IpAddressSerializer, "\"0:0:0:0:0:0:0:1\"")
        assertEquals("\"::1\"", json.encodeToString(IpAddressSerializer, address))
        assertTrue(address is IpAddress.V6)
    }

    @Test
    fun `malformed dates and durations fail as serialization errors without quoting the input`() {
        val bad = listOf(
            { json.decodeFromString(PositiveDurationSerializer, "\"bogus-1\"") },
            { json.decodeFromString(DateRangeSerializer, """{"start":"bogus-1","endInclusive":"2026-01-31"}""") },
            { json.decodeFromString(TimeWindowSerializer, """{"start":"bogus-1","endExclusive":"bogus-1"}""") },
        )
        bad.forEach { decode ->
            val error = assertFailsWith<SerializationException> { decode() }
            assertFalse(error is InvalidValueException)
            assertFalse(error.message.orEmpty().contains("bogus"))
        }
    }
}
