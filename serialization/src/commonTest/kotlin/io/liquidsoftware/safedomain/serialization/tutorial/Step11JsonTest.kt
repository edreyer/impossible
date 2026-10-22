package io.liquidsoftware.safedomain.serialization.tutorial

import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.ensure
import io.liquidsoftware.safedomain.map
import io.liquidsoftware.safedomain.mapError
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.BoundedInt
import io.liquidsoftware.safedomain.serialization.BoundedIntSerializer
import io.liquidsoftware.safedomain.serialization.EmailSerializer
import io.liquidsoftware.safedomain.serialization.InvalidValueException
import io.liquidsoftware.safedomain.serialization.NonBlankStringSerializer
import io.liquidsoftware.safedomain.serialization.SecretSerializer
import io.liquidsoftware.safedomain.serialization.asSerializer
import io.liquidsoftware.safedomain.serialization.safeDomainSerializersModule
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Secret
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

// Code for docs/tutorial/11-json.md. Keep the two in step: TutorialSnippetsTest checks
// that every snippet in the chapter appears here.

/** The ISBN-13 type from step 3, condensed: tests in this module cannot see the library module's tests. */
@JvmInline
value class Isbn private constructor(val value: String) {
    sealed interface Error {
        data object Invalid : Error
    }

    companion object : ValueFactory<String, Error, Isbn> by (
        NonBlankString
            .mapError { Error.Invalid }
            .ensure({ text -> text.value.length == 13 && text.value.all { it in '0'..'9' } }) { Error.Invalid }
            .map { Isbn(it.value) }
        )
}

object IsbnSerializer : KSerializer<Isbn> by Isbn.asSerializer(
    serialName = "com.example.bookshop.Isbn",
    input = String.serializer(),
    render = Isbn::value,
    describe = { listOf(ErrorMessage("isbn.invalid", "Enter the 13 digits of an ISBN.")) },
)

object RatingSerializer : KSerializer<BoundedInt> by BoundedIntSerializer(1..5)

@Serializable
data class Review(
    @Serializable(with = IsbnSerializer::class) val isbn: Isbn,
    @Serializable(with = EmailSerializer::class) val reviewer: Email,
    @Serializable(with = RatingSerializer::class) val rating: BoundedInt,
    @Serializable(with = NonBlankStringSerializer::class) val text: NonBlankString,
)

val json: Json = Json { serializersModule = safeDomainSerializersModule }

@Serializable
data class GiftCard(@Contextual val balance: Money, @Contextual val recipient: Email)

@Serializable
data class Login(@Contextual val email: Email, @Serializable(with = SecretSerializer::class) val password: Secret)

sealed interface ReviewSubmission {
    data class Accepted(val review: Review) : ReviewSubmission
    data class Rejected(val serialName: String, val codes: List<String>) : ReviewSubmission
}

fun submitReview(body: String): ReviewSubmission = try {
    ReviewSubmission.Accepted(json.decodeFromString<Review>(body))
} catch (e: InvalidValueException) {
    ReviewSubmission.Rejected(e.serialName, e.messages.map { it.code })
}

class Step11JsonTest {
    private val body = """{"isbn":"9780441013593","reviewer":"ada@example.com","rating":5,"text":"A classic."}"""

    @Test
    fun `values read and write in their plain wire form`() {
        val review = json.decodeFromString<Review>(body)
        assertEquals(5, review.rating.value)
        assertEquals(body, json.encodeToString(Review.serializer(), review))
    }

    @Test
    fun `decoding runs the factory so invalid data never becomes a value`() {
        val sixStars = body.replace("\"rating\":5", "\"rating\":6")
        val failure = assertFailsWith<InvalidValueException> { json.decodeFromString<Review>(sixStars) }
        assertEquals("io.liquidsoftware.safedomain.numeric.BoundedInt", failure.serialName)
        assertEquals(listOf("bounded_int.out_of_range"), failure.messages.map { it.code })
        assertIs<BoundedInt.Error.OutOfRange>(failure.error)
        assertEquals(
            ReviewSubmission.Rejected("com.example.bookshop.Isbn", listOf("isbn.invalid")),
            submitReview(body.replace("9780441013593", "978")),
        )
        assertIs<ReviewSubmission.Accepted>(submitReview(body))
    }

    @Test
    fun `the contextual module covers fields marked Contextual`() {
        val text = """{"balance":{"amount":"25.00","currency":"EUR"},"recipient":"ada@example.com"}"""
        val card = json.decodeFromString<GiftCard>(text)
        assertEquals(2500L, card.balance.minor)
        assertEquals(text, json.encodeToString(GiftCard.serializer(), card))
    }

    @Test
    fun `secrets are read but never written`() {
        val login = json.decodeFromString<Login>("""{"email":"ada@example.com","password":"correct horse"}""")
        assertEquals("correct horse", login.password.reveal())
        assertFailsWith<SerializationException> { json.encodeToString(Login.serializer(), login) }
    }
}
