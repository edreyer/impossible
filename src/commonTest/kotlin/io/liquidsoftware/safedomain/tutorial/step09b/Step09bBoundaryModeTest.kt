package io.liquidsoftware.safedomain.tutorial.step09b

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.FieldError
import io.liquidsoftware.safedomain.FieldPath
import io.liquidsoftware.safedomain.RequiredError
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.collections.NonEmptyError
import io.liquidsoftware.safedomain.collections.factory
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.eachFieldAt
import io.liquidsoftware.safedomain.fieldAt
import io.liquidsoftware.safedomain.flatFieldAt
import io.liquidsoftware.safedomain.fromEither
import io.liquidsoftware.safedomain.locale.CountryCode
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.PositiveMoney
import io.liquidsoftware.safedomain.nestedFieldAt
import io.liquidsoftware.safedomain.required
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.toProblemDetails
import io.liquidsoftware.safedomain.tutorial.step03.Isbn
import io.liquidsoftware.safedomain.tutorial.step08.CustomerId
import io.liquidsoftware.safedomain.tutorial.step08.customerIds
import io.liquidsoftware.safedomain.tutorial.step09.AddressInput
import io.liquidsoftware.safedomain.tutorial.step09.OrderInput
import io.liquidsoftware.safedomain.validate
import kotlin.test.Test
import kotlin.test.assertEquals

// Code for docs/tutorial/09b-boundary-mode.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

class ShippingAddress private constructor(val street: NonBlankString, val city: NonBlankString, val country: CountryCode) {
    companion object : ValueFactory<AddressInput, NonEmptyList<FieldError<ValidationError>>, ShippingAddress> {
        context(raise: Raise<NonEmptyList<FieldError<ValidationError>>>)
        override fun from(value: AddressInput): ShippingAddress = validate {
            val street by NonBlankString.fieldAt(value.street)
            val city by NonBlankString.fieldAt(value.city)
            val country by CountryCode.fieldAt(value.country)
            ShippingAddress(street, city, country)
        }
    }
}

/** Isbn is the shop's own type, so its error joins the boundary through a small wrapper. */
data class InvalidIsbn(val cause: Isbn.Error) : ValidationError {
    override fun describe(): ErrorMessage = ErrorMessage("isbn.invalid", "Enter a 13-digit ISBN.")
}

class OrderRequest private constructor(
    val customer: CustomerId,
    val email: Email,
    val shipTo: ShippingAddress,
    val books: NonEmptyList<Isbn>,
    val total: PositiveMoney,
) {
    companion object : ValueFactory<OrderInput, NonEmptyList<FieldError<ValidationError>>, OrderRequest> {
        context(raise: Raise<NonEmptyList<FieldError<ValidationError>>>)
        override fun from(value: OrderInput): OrderRequest = validate {
            val customer by customerIds.fieldAt(value.customerId, name = "customer_id")
            val email by Email.required().fieldAt(value.email)
            val shipTo by ShippingAddress.nestedFieldAt(value.shipTo, name = "ship_to")
            val isbns by Isbn.eachFieldAt(value.isbns) { InvalidIsbn(it) }
            val amount by Money.parser.flatFieldAt(value.total, name = "total")
            val books by fieldAt(name = "isbns") { NonEmptyList.factory<Isbn>().from(isbns) }
            val total by fieldAt { PositiveMoney.from(amount) }
            OrderRequest(customer, email, shipTo, books, total)
        }
    }
}

class Step09bBoundaryModeTest {
    private val valid = OrderInput(
        customerId = "123e4567-e89b-12d3-a456-426614174000",
        email = "reader@example.com",
        shipTo = AddressInput("1 Library Lane", "Leeds", "GB"),
        isbns = listOf("9780306406157"),
        total = Money.Text("24.00", "GBP"),
    )

    @Test
    fun `library errors are recorded as they are`() {
        val failures = OrderRequest.fromEither(
            valid.copy(email = null, shipTo = AddressInput(" ", "Leeds", "gb"), isbns = listOf("9780306406157", "123")),
        ).leftOrNull()!!
        assertEquals(
            listOf(
                FieldError(FieldPath.of("email"), RequiredError.Missing),
                FieldError(FieldPath.of("ship_to").child("street"), NonBlankString.Error.Blank),
                FieldError(FieldPath.of("ship_to").child("country"), CountryCode.Error.UnsupportedCode("gb")),
                FieldError(FieldPath.of("isbns").index(1), InvalidIsbn(Isbn.Error.NotThirteenDigits)),
            ),
            failures.toList(),
        )
    }

    @Test
    fun `dependent fields record library errors too`() {
        val failures = OrderRequest.fromEither(valid.copy(isbns = emptyList(), total = Money.Text("0.00", "GBP")))
            .leftOrNull()!!
        assertEquals(listOf("isbns", "total"), failures.map { it.path.toString() })
        assertEquals(NonEmptyError.Empty, failures.first().error)
    }

    @Test
    fun `failures become problem details including the shop's own error`() {
        val failures = OrderRequest.fromEither(valid.copy(email = "nope", isbns = listOf("123"))).leftOrNull()!!
        assertEquals(
            listOf("/email" to "email.invalid_format", "/isbns/0" to "isbn.invalid"),
            failures.toProblemDetails().errors.map { it.pointer to it.code },
        )
    }

    @Test
    fun `valid input builds the order`() {
        val order = OrderRequest.fromEither(valid)
        assertEquals(true, order is Either.Right)
        assertEquals("Leeds", order.getOrNull()?.shipTo?.city?.value)
    }
}
