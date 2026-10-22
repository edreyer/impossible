package io.liquidsoftware.safedomain.tutorial.step05

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.optional
import io.liquidsoftware.safedomain.required
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// Code for docs/tutorial/05-optional-and-dependent-fields.md. Keep the two in step:
// TutorialSnippetsTest checks that every snippet in the chapter appears here.

/** What a sign-up form posts: any field may be missing. */
data class SignupForm(val name: String?, val nickname: String?, val email: String?, val phone: String?)

/** How to reach a customer. There is always at least one way. */
sealed interface Contact {
    data class ByEmail(val email: Email) : Contact
    data class ByPhone(val phone: Phone) : Contact
    data class Both(val email: Email, val phone: Phone) : Contact
}

private fun contactOf(email: Email?, phone: Phone?): Contact? = when {
    email != null && phone != null -> Contact.Both(email, phone)
    email != null -> Contact.ByEmail(email)
    phone != null -> Contact.ByPhone(phone)
    else -> null
}

class Customer private constructor(val name: NonBlankString, val nickname: NonBlankString?, val contact: Contact) {
    sealed interface Error {
        data object MissingName : Error
        data class InvalidName(val cause: NonBlankString.Error) : Error
        data class InvalidNickname(val cause: NonBlankString.Error) : Error
        data class InvalidEmail(val cause: Email.Error) : Error
        data class InvalidPhone(val cause: Phone.Error) : Error
        data object NoContact : Error
    }

    companion object : ValueFactory<SignupForm, NonEmptyList<Error>, Customer> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: SignupForm): Customer = validate {
            val name by NonBlankString.required().field(value.name, { Error.MissingName }, Error::InvalidName)
            val nickname by NonBlankString.optional().field(value.nickname, Error::InvalidNickname)
            val contact by field {
                val email by Email.optional().field(value.email, Error::InvalidEmail)
                val phone by Phone.optional().field(value.phone, Error::InvalidPhone)
                ensureNotNull(contactOf(email, phone)) { Error.NoContact }
            }
            Customer(name, nickname, contact)
        }
    }
}

data class PreorderInput(val copies: Int, val limit: Int)

class Preorder private constructor(val copies: PositiveInt, val limit: PositiveInt) {
    sealed interface Error {
        data class InvalidCopies(val cause: PositiveInt.Error) : Error
        data class InvalidLimit(val cause: PositiveInt.Error) : Error
        data class OverLimit(val copies: Int, val limit: Int) : Error
    }

    companion object : ValueFactory<PreorderInput, NonEmptyList<Error>, Preorder> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: PreorderInput): Preorder = validate {
            val checked by field {
                val copies by PositiveInt.field(value.copies, Error::InvalidCopies)
                val limit by PositiveInt.field(value.limit, Error::InvalidLimit)
                ensure(copies <= limit) { Error.OverLimit(copies.value, limit.value) }
                copies to limit
            }
            Preorder(checked.first, checked.second)
        }
    }
}

class Step05OptionalAndDependentTest {
    private val valid = SignupForm(name = "Ada", nickname = null, email = "ada@example.com", phone = null)

    private fun signUp(form: SignupForm): Either<NonEmptyList<Customer.Error>, Customer> =
        either { Customer.from(form) }

    @Test
    fun `required turns a missing value into a typed error`() {
        assertEquals(Either.Left(nonEmptyListOf(Customer.Error.MissingName)), signUp(valid.copy(name = null)))
        assertEquals(
            Either.Left(nonEmptyListOf(Customer.Error.InvalidName(NonBlankString.Error.Blank))),
            signUp(valid.copy(name = " ")),
        )
    }

    @Test
    fun `optional keeps null as null but still validates a present value`() {
        assertNull(signUp(valid).getOrNull()?.nickname)
        assertEquals(
            Either.Left(nonEmptyListOf(Customer.Error.InvalidNickname(NonBlankString.Error.Blank))),
            signUp(valid.copy(nickname = "")),
        )
    }

    @Test
    fun `a dependent field builds a sealed type from optional parts`() {
        assertIs<Contact.ByEmail>(signUp(valid).getOrNull()?.contact)
        assertIs<Contact.Both>(signUp(valid.copy(phone = "+442071838750")).getOrNull()?.contact)
        assertIs<Contact.ByPhone>(signUp(valid.copy(email = null, phone = "+442071838750")).getOrNull()?.contact)
        assertEquals(Either.Left(nonEmptyListOf(Customer.Error.NoContact)), signUp(valid.copy(email = null)))
    }

    @Test
    fun `a dependent field stops on its own failures while other fields still run`() {
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    Customer.Error.MissingName,
                    Customer.Error.InvalidEmail(Email.Error.InvalidFormat("ada")),
                    Customer.Error.InvalidPhone(Phone.Error.InvalidFormat("0207")),
                ),
            ),
            signUp(SignupForm(name = null, nickname = null, email = "ada", phone = "0207")),
        )
    }

    @Test
    fun `a cross field check runs once its inputs are valid`() {
        val over: Either<NonEmptyList<Preorder.Error>, Preorder> =
            either { Preorder.from(PreorderInput(copies = 3, limit = 2)) }
        assertEquals(Either.Left(nonEmptyListOf(Preorder.Error.OverLimit(3, 2))), over)

        val invalid: Either<NonEmptyList<Preorder.Error>, Preorder> =
            either { Preorder.from(PreorderInput(copies = 0, limit = 0)) }
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    Preorder.Error.InvalidCopies(PositiveInt.Error.NonPositive(0)),
                    Preorder.Error.InvalidLimit(PositiveInt.Error.NonPositive(0)),
                ),
            ),
            invalid,
        )
    }
}
