package io.liquidsoftware.safedomain.jackson

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.geo.GeoPoint
import io.liquidsoftware.safedomain.map
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.BoundedInt
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Secret
import io.liquidsoftware.safedomain.text.Slug
import io.liquidsoftware.safedomain.time.DateRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RatingDeserializer : BoundedIntDeserializer(1..5)

class TagsDeserializer : BoundedListDeserializer(1..3)

/** A Java-style holder: nullable value-class properties are boxed, so plain Jackson can bind them. */
class Review {
    @JsonDeserialize(using = RatingDeserializer::class)
    var rating: BoundedInt? = null

    @JsonDeserialize(using = TagsDeserializer::class)
    var tags: BoundedList<Slug>? = null

    var price: Money? = null
}

/** A Kotlin data class with value-class properties, read with jackson-module-kotlin as well. */
data class Signup(val email: Email, val seats: PositiveInt, val name: NonBlankString)

/** A consumer's own refined type. */
@JvmInline
value class Sku private constructor(val value: String) {
    companion object : ValueFactory<String, Slug.Error, Sku> by Slug.map({ Sku(it.value) })
}

class JacksonModuleTest {
    private val mapper = JsonMapper.builder().addModule(ImpossibleModule()).build()

    @Test
    fun `text and numbers use their plain JSON forms`() {
        assertEquals("\"ann@example.com\"", mapper.writeValueAsString(Email.fromTrusted("ann@example.com")))
        assertEquals("7", mapper.writeValueAsString(PositiveInt.fromTrusted(7)))
        assertEquals("\"12.30\"", mapper.writeValueAsString(Decimal.parse("12.30")))
        assertEquals(Decimal.parse("12.30"), mapper.readValue("\"12.30\"", Decimal::class.java))
    }

    @Test
    fun `money ranges and points are objects`() {
        val money = Money.parser.fromTrusted(Money.Text("12.30", "EUR"))
        assertEquals("""{"amount":"12.30","currency":"EUR"}""", mapper.writeValueAsString(money))
        val range = """{"start":"2026-01-01","endInclusive":"2026-01-31"}"""
        assertEquals(range, mapper.writeValueAsString(mapper.readValue(range, DateRange::class.java)))
        val point = mapper.readValue("""{"latitude":52.37,"longitude":4.89}""", GeoPoint::class.java)
        assertEquals(52.37, point.latitude)
    }

    @Test
    fun `lists are arrays whose elements and invariants are checked`() {
        val type = object : TypeReference<UniqueList<PositiveInt>>() {}
        assertEquals(listOf(1, 2), mapper.readValue("[1,2]", type).value.map { it.value })
        val duplicate = assertFailsWith<InvalidValueException> { mapper.readValue("[1,1]", type) }
        assertEquals(listOf("unique_list.duplicate"), duplicate.messages.map { it.code })
        assertFailsWith<InvalidValueException> { mapper.readValue("[1,0]", type) }
    }

    @Test
    fun `an invalid value names its codes and pointer but never the input`() {
        val failure = assertFailsWith<InvalidValueException> {
            mapper.readValue("""{"price":{"amount":"x","currency":"secret-input"}}""", Review::class.java)
        }
        assertEquals("/price", failure.pointer)
        assertEquals(listOf("money.invalid_amount", "currency.unsupported"), failure.messages.map { it.code })
        assertTrue("money.invalid_amount" in failure.message.orEmpty())
        assertFalse("secret-input" in failure.message.orEmpty())

        val email = assertFailsWith<InvalidValueException> { mapper.readValue("\"ann@\"", Email::class.java) }
        assertEquals("", email.pointer)
        assertFalse("ann@" in email.message.orEmpty())
    }

    @Test
    fun `numbers are never coerced`() {
        assertFailsWith<MismatchedInputException> { mapper.readValue("2.7", PositiveInt::class.java) }
        assertFailsWith<MismatchedInputException> { mapper.readValue("\"3\"", PositiveInt::class.java) }
        assertFailsWith<MismatchedInputException> { mapper.readValue("3000000000", PositiveInt::class.java) }
        assertFailsWith<MismatchedInputException> { mapper.readValue("12.3", Decimal::class.java) }
        assertFailsWith<MismatchedInputException> {
            mapper.readValue(
                """{"start":"2026-02-30","endInclusive":"x"}""",
                DateRange::class.java,
            )
        }
        assertFailsWith<MismatchedInputException> { mapper.readValue("""{"amount":"1.00"}""", Money::class.java) }
    }

    @Test
    fun `unknown fields follow the mapper's setting`() {
        // Jackson 3 accepts unknown properties by default, and the library's object forms do the same.
        val extra = """{"amount":"1.00","currency":"EUR","extra":1}"""
        assertEquals("1.00", mapper.readValue(extra, Money::class.java).amount.toPlainString())
        val strict = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
        assertFailsWith<MismatchedInputException> { strict.readValue(extra, Money::class.java) }
    }

    @Test
    fun `a secret is read but never written`() {
        assertEquals("hunter2", mapper.readValue("\"hunter2\"", Secret::class.java).reveal())
        assertFailsWith<DatabindException> { mapper.writeValueAsString(Secret.fromTrusted("hunter2")) }
    }

    @Test
    fun `configured types are read through the deserializer named on the property`() {
        val review = mapper.readValue("""{"rating":4,"tags":["sci-fi","classic"]}""", Review::class.java)
        assertEquals(4, review.rating?.value)
        assertEquals(listOf("sci-fi", "classic"), review.tags?.value?.map { it.value })
        val tooMany = assertFailsWith<InvalidValueException> {
            mapper.readValue("""{"tags":["a","b","c","d"]}""", Review::class.java)
        }
        assertEquals("/tags", tooMany.pointer)
        val outOfRange = assertFailsWith<InvalidValueException> {
            mapper.readValue("""{"rating":9}""", Review::class.java)
        }
        assertEquals(listOf("bounded_int.out_of_range"), outOfRange.messages.map { it.code })
    }

    @Test
    fun `a consumer type works through asJacksonDeserializer and jacksonSerializer`() {
        val module = SimpleModule()
            .addDeserializer(
                Sku::class.java,
                Sku.asJacksonDeserializer("com.example.Sku", String::class.java) {
                    listOf(ErrorMessage("sku.invalid", "Not a SKU."))
                },
            )
            .addSerializer(Sku::class.java, jacksonSerializer(Sku::value))
        val skus = JsonMapper.builder().addModule(module).build()
        assertEquals("\"ab-12\"", skus.writeValueAsString(skus.readValue("\"ab-12\"", Sku::class.java)))
        assertEquals(
            listOf("sku.invalid"),
            assertFailsWith<InvalidValueException> {
                skus.readValue("\"A B\"", Sku::class.java)
            }.messages.map { it.code },
        )
    }

    @Test
    fun `kotlin data classes with value class properties work with jackson-module-kotlin`() {
        val kotlinMapper = jacksonMapperBuilder().addModule(ImpossibleModule()).build()
        val signup = kotlinMapper.readValue<Signup>("""{"email":"ann@example.com","seats":2,"name":"Ann"}""")
        assertEquals(Signup(Email.fromTrusted("ann@example.com"), PositiveInt.fromTrusted(2), NonBlankString.fromTrusted("Ann")), signup)
        assertEquals("""{"email":"ann@example.com","seats":2,"name":"Ann"}""", kotlinMapper.writeValueAsString(signup))
        val failure = assertFailsWith<InvalidValueException> {
            kotlinMapper.readValue<Signup>("""{"email":"nope","seats":2,"name":"Ann"}""")
        }
        assertEquals("/email", failure.pointer)
    }
}
