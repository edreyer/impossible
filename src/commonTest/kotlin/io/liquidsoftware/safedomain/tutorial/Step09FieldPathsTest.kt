package io.liquidsoftware.safedomain.tutorial.step09

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.FieldError
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.collections.NonEmptyError
import io.liquidsoftware.safedomain.collections.factory
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.eachFieldAt
import io.liquidsoftware.safedomain.fieldAt
import io.liquidsoftware.safedomain.flatFieldAt
import io.liquidsoftware.safedomain.identity.TypedId
import io.liquidsoftware.safedomain.locale.CountryCode
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.PositiveMoney
import io.liquidsoftware.safedomain.nestedFieldAt
import io.liquidsoftware.safedomain.required
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.tutorial.step03.Isbn
import io.liquidsoftware.safedomain.tutorial.step08.CustomerId
import io.liquidsoftware.safedomain.tutorial.step08.customerIds
import io.liquidsoftware.safedomain.validate
import kotlin.test.Test
import kotlin.test.assertEquals

// Code for docs/tutorial/09-field-paths.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

data class AddressInput(val street: String, val city: String, val country: String)

class Address private constructor(val street: NonBlankString, val city: NonBlankString, val country: CountryCode) {
    sealed interface Error {
        data class Street(val cause: NonBlankString.Error) : Error
        data class City(val cause: NonBlankString.Error) : Error
        data class Country(val cause: CountryCode.Error) : Error
    }

    companion object : ValueFactory<AddressInput, NonEmptyList<FieldError<Error>>, Address> {
        context(raise: Raise<NonEmptyList<FieldError<Error>>>)
        override fun from(value: AddressInput): Address = validate {
            val street by NonBlankString.fieldAt(value.street) { Error.Street(it) }
            val city by NonBlankString.fieldAt(value.city) { Error.City(it) }
            val country by CountryCode.fieldAt(value.country) { Error.Country(it) }
            Address(street, city, country)
        }
    }
}

data class OrderInput(
    val customerId: String,
    val email: String?,
    val shipTo: AddressInput,
    val isbns: List<String>,
    val total: Money.Text,
)

class Order private constructor(
    val customer: CustomerId,
    val email: Email,
    val shipTo: Address,
    val books: NonEmptyList<Isbn>,
    val total: PositiveMoney,
) {
    sealed interface Error {
        data class InvalidCustomerId(val cause: TypedId.Error) : Error
        data object MissingEmail : Error
        data class InvalidEmail(val cause: Email.Error) : Error
        data class InvalidAddress(val cause: Address.Error) : Error
        data class InvalidIsbn(val cause: Isbn.Error) : Error
        data class NoBooks(val cause: NonEmptyError) : Error
        data class InvalidTotal(val cause: Money.Error) : Error
        data class TotalNotPositive(val cause: PositiveMoney.Error) : Error
    }

    companion object : ValueFactory<OrderInput, NonEmptyList<FieldError<Error>>, Order> {
        context(raise: Raise<NonEmptyList<FieldError<Error>>>)
        override fun from(value: OrderInput): Order = validate {
            val customer by customerIds.fieldAt(value.customerId, name = "customer_id") { Error.InvalidCustomerId(it) }
            val email by Email.required().fieldAt(value.email, onMissing = { Error.MissingEmail }) { Error.InvalidEmail(it) }
            val shipTo by Address.nestedFieldAt(value.shipTo, name = "ship_to") { Error.InvalidAddress(it) }
            val isbns by Isbn.eachFieldAt(value.isbns) { Error.InvalidIsbn(it) }
            val books by fieldAt(name = "isbns") { NonEmptyList.factory<Isbn>().from(isbns) { Error.NoBooks(it) } }
            val amount by Money.parser.flatFieldAt(value.total, name = "total") { Error.InvalidTotal(it) }
            val total by fieldAt { PositiveMoney.from(amount) { Error.TotalNotPositive(it) } }
            Order(customer, email, shipTo, books, total)
        }
    }
}

class Step09FieldPathsTest {
    private val valid = OrderInput(
        customerId = "550e8400-e29b-41d4-a716-446655440000",
        email = "ada@example.com",
        shipTo = AddressInput(street = "12 Analytical Row", city = "London", country = "GB"),
        isbns = listOf("9780441013593"),
        total = Money.Text("12.99", "USD"),
    )

    private fun order(input: OrderInput): Either<NonEmptyList<FieldError<Order.Error>>, Order> = either { Order.from(input) }

    private fun pathsOf(input: OrderInput): List<String> = order(input).leftOrNull().orEmpty().map { it.path.toString() }

    @Test
    fun `every failure carries the path of the field that caused it`() {
        val input = OrderInput(
            customerId = "42",
            email = null,
            shipTo = AddressInput(street = "", city = "London", country = "gb"),
            isbns = listOf("9780441013593", "978"),
            total = Money.Text("ten", "ZZZ"),
        )
        assertEquals(
            listOf("customer_id", "email", "ship_to.street", "ship_to.country", "isbns[1]", "total", "total"),
            pathsOf(input),
        )
    }

    @Test
    fun `dependent fields report at their own path`() {
        assertEquals(listOf("isbns"), pathsOf(valid.copy(isbns = emptyList())))
        assertEquals(listOf("total"), pathsOf(valid.copy(total = Money.Text("0", "USD"))))
    }

    @Test
    fun `paths render as JSON pointers for APIs`() {
        val failure = order(valid.copy(shipTo = valid.shipTo.copy(city = " "))).leftOrNull()!!.single()
        assertEquals("/ship_to/city", failure.path.toJsonPointer())
        assertEquals(Order.Error.InvalidAddress(Address.Error.City(NonBlankString.Error.Blank)), failure.error)
    }

    @Test
    fun `valid input builds the order`() {
        assertEquals(1299L, order(valid).getOrNull()?.total?.money?.minor)
    }
}
