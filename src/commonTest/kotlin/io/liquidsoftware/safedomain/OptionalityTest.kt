package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OptionalityTest {
    @Test
    fun `optional maps null to null and still validates present input in full`() {
        val middleName = NonBlankString.optional()
        assertEquals(Either.Right(null), either<NonBlankString.Error, NonBlankString?> { middleName.from(null) })
        assertEquals(
            Either.Left(NonBlankString.Error.Blank),
            either<NonBlankString.Error, NonBlankString?> { middleName.from("") },
        )
        assertEquals("Ann", either<NonBlankString.Error, NonBlankString?> { middleName.from("Ann") }.getOrNull()?.value)
    }

    @Test
    fun `required turns null into a typed missing error instead of a default`() {
        val email = Email.required()
        assertEquals(
            Either.Left(RequiredError.Missing),
            either<RequiredError<Email.Error>, Email> { email.from(null) },
        )
        assertEquals(
            Either.Left(RequiredError.Invalid(Email.Error.InvalidFormat("nope"))),
            either<RequiredError<Email.Error>, Email> { email.from("nope") },
        )
    }

    @Test
    fun `a composite declares optional and required fields explicitly and accumulates both`() {
        val failed = either { Contact.from(ContactInput(email = null, middleName = " ", nickname = null)) }
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    Contact.Error.MissingEmail,
                    Contact.Error.InvalidMiddleName(NonBlankString.Error.Blank),
                ),
            ),
            failed,
        )

        val invalid = either { Contact.from(ContactInput(email = "nope", middleName = null, nickname = null)) }
        assertEquals(Either.Left(nonEmptyListOf(Contact.Error.InvalidEmail(Email.Error.InvalidFormat("nope")))), invalid)

        val contact = either { Contact.from(ContactInput(email = "a@example.com", middleName = null, nickname = "Al")) }
            .getOrNull()!!
        assertEquals("a@example.com", contact.email.value)
        assertNull(contact.middleName)
        assertEquals("Al", contact.nickname?.value)
    }

    @Test
    fun `trusted construction is for literals and throws on programmer error`() {
        assertEquals(3, PositiveInt.fromTrusted(3).value)
        assertFailsWith<IllegalArgumentException> { PositiveInt.fromTrusted(0) }
    }

    private data class ContactInput(val email: String?, val middleName: String?, val nickname: String?)

    private data class Contact(val email: Email, val middleName: NonBlankString?, val nickname: NonBlankString?) {
        sealed interface Error {
            data object MissingEmail : Error
            data class InvalidEmail(val cause: Email.Error) : Error
            data class InvalidMiddleName(val cause: NonBlankString.Error) : Error
            data class InvalidNickname(val cause: NonBlankString.Error) : Error
        }

        companion object {
            context(raise: Raise<NonEmptyList<Error>>)
            fun from(value: ContactInput): Contact = validate {
                val email by Email.required().field(value.email, { Error.MissingEmail }, Error::InvalidEmail)
                val middleName by NonBlankString.optional().field(value.middleName, Error::InvalidMiddleName)
                val nickname by NonBlankString.optional().field(value.nickname, Error::InvalidNickname)
                Contact(email, middleName, nickname)
            }
        }
    }
}
