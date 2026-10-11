package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.locale.CountryCode
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import kotlin.test.Test
import kotlin.test.assertEquals

/** A nested composite whose own factory reports paths. */
private data class Address(val street: NonBlankString, val country: CountryCode) {
    data class Input(val street: String, val country: String)

    sealed interface Error {
        data class Street(val cause: NonBlankString.Error) : Error
        data class Country(val cause: CountryCode.Error) : Error
    }

    companion object : ValueFactory<Input, NonEmptyList<FieldError<Error>>, Address> {
        context(raise: Raise<NonEmptyList<FieldError<Error>>>)
        override fun from(value: Input): Address = validate {
            val street by NonBlankString.fieldAt(value.street) { Error.Street(it) }
            val country by CountryCode.fieldAt(value.country) { Error.Country(it) }
            Address(street, country)
        }
    }
}

private data class Shipment(
    val contact: Email,
    val address: Address,
    val quantities: List<PositiveInt>,
    val price: Money,
    val maximum: PositiveInt,
) {
    data class Input(
        val contact: String?,
        val address: Address.Input,
        val quantities: List<Int>,
        val price: Money.Text,
        val maximum: Int,
    )

    sealed interface Error {
        data object ContactMissing : Error
        data class Contact(val cause: Email.Error) : Error
        data class Address(val cause: io.liquidsoftware.safedomain.Address.Error) : Error
        data class Quantity(val cause: PositiveInt.Error) : Error
        data class Price(val cause: Money.Error) : Error
        data class Maximum(val cause: PositiveInt.Error) : Error
        data class OverMaximum(val total: Int, val maximum: Int) : Error
    }

    companion object : ValueFactory<Input, NonEmptyList<FieldError<Error>>, Shipment> {
        context(raise: Raise<NonEmptyList<FieldError<Error>>>)
        override fun from(value: Input): Shipment = validate {
            val contact by Email.required().fieldAt(value.contact, onMissing = { Error.ContactMissing }) { Error.Contact(it) }
            val address by Address.nestedFieldAt(value.address) { Error.Address(it) }
            val quantities by PositiveInt.eachFieldAt(value.quantities) { Error.Quantity(it) }
            val price by Money.parser.flatFieldAt(value.price, name = "unit_price") { Error.Price(it) }
            val maximum by fieldAt {
                val limit = PositiveInt.from(value.maximum) { Error.Maximum(it) }
                val total = quantities.sumOf { it.value }
                ensure(total <= limit.value) { Error.OverMaximum(total, limit.value) }
                limit
            }
            Shipment(contact, address, quantities, price, maximum)
        }
    }
}

class PathValidationTest {
    private val valid = Shipment.Input(
        contact = "ada@example.com",
        address = Address.Input("1 Main St", "GB"),
        quantities = listOf(1, 2),
        price = Money.Text("9.99", "USD"),
        maximum = 10,
    )

    private fun shipment(input: Shipment.Input): Either<NonEmptyList<FieldError<Shipment.Error>>, Shipment> =
        either { Shipment.from(input) }

    @Test
    fun `each failure is reported at the property that declared it`() {
        val result = shipment(valid.copy(contact = null, quantities = listOf(1, 0, -3)))
        assertEquals(Either.Left(nonEmptyListOf(
            FieldError(FieldPath.of("contact"), Shipment.Error.ContactMissing),
            FieldError(FieldPath.of("quantities").index(1), Shipment.Error.Quantity(PositiveInt.Error.NonPositive(0))),
            FieldError(FieldPath.of("quantities").index(2), Shipment.Error.Quantity(PositiveInt.Error.NonPositive(-3))),
        )), result)
    }

    @Test
    fun `nested composites prefix their own paths`() {
        val result = shipment(valid.copy(address = Address.Input(" ", "XX")))
        assertEquals(Either.Left(nonEmptyListOf(
            FieldError(
                FieldPath.of("address").child("street"),
                Shipment.Error.Address(Address.Error.Street(NonBlankString.Error.Blank)),
            ),
            FieldError(
                FieldPath.of("address").child("country"),
                Shipment.Error.Address(Address.Error.Country(CountryCode.Error.UnsupportedCode("XX"))),
            ),
        )), result)
    }

    @Test
    fun `an explicit name overrides the property name and every flat error shares it`() {
        val result = shipment(valid.copy(price = Money.Text("x", "ZZZ")))
        val path = FieldPath.of("unit_price")
        assertEquals(Either.Left(nonEmptyListOf(
            FieldError(path, Shipment.Error.Price(Money.Error.InvalidAmount("x"))),
            FieldError(path, Shipment.Error.Price(Money.Error.InvalidCurrency(Currency.Error.UnsupportedCode("ZZZ")))),
        )), result)
    }

    @Test
    fun `a dependent block reports at its own path`() {
        val result = shipment(valid.copy(quantities = listOf(6, 7)))
        assertEquals(
            Either.Left(nonEmptyListOf(FieldError(FieldPath.of("maximum"), Shipment.Error.OverMaximum(13, 10)))),
            result,
        )
    }

    @Test
    fun `invalid contact text is reported with its cause`() {
        val result = shipment(valid.copy(contact = "not an email"))
        assertEquals(
            Either.Left(nonEmptyListOf(
                FieldError(FieldPath.of("contact"), Shipment.Error.Contact(Email.Error.InvalidFormat("not an email"))),
            )),
            result,
        )
    }

    @Test
    fun `valid input builds the composite`() {
        assertEquals(2, shipment(valid).getOrNull()?.quantities?.size)
    }

    @Test
    fun `paths render as dotted names and JSON pointers`() {
        val path = FieldPath.of("lines").index(2).child("unit/price~").plus(FieldPath.of("amount"))
        assertEquals("lines[2].unit/price~.amount", path.toString())
        assertEquals("/lines/2/unit~1price~0/amount", path.toJsonPointer())
        assertEquals("", FieldPath.ROOT.toJsonPointer())
        assertEquals("Blank", FieldError(FieldPath.ROOT, "Blank").toString())
        assertEquals("lines[2]: Blank", FieldError(FieldPath.of("lines").index(2), "Blank").toString())
    }

    @Test
    fun `failures render as RFC 9457 problem details`() {
        val describe = { error: Shipment.Error ->
            when (error) {
                Shipment.Error.ContactMissing -> ErrorMessage("shipment.contact_missing", "Enter a contact email address.")
                is Shipment.Error.Contact -> ErrorMessages.describe(error.cause)
                is Shipment.Error.Address -> when (val cause = error.cause) {
                    is Address.Error.Street -> ErrorMessages.describe(cause.cause)
                    is Address.Error.Country -> ErrorMessages.describe(cause.cause)
                }
                is Shipment.Error.Quantity -> ErrorMessages.describe(error.cause)
                is Shipment.Error.Price -> ErrorMessages.describe(error.cause)
                is Shipment.Error.Maximum -> ErrorMessages.describe(error.cause)
                is Shipment.Error.OverMaximum -> ErrorMessage(
                    "shipment.over_maximum",
                    "Ship at most ${error.maximum} items.",
                    mapOf("maximum" to "${error.maximum}"),
                )
            }
        }
        val failures = shipment(valid.copy(contact = "x", address = Address.Input("1 Main St", "gb"), quantities = listOf(0)))
            .leftOrNull()!!
        assertEquals(
            ProblemDetails(
                type = "about:blank",
                title = "The request is not valid.",
                status = 422,
                detail = "The request has 3 problems.",
                instance = null,
                errors = listOf(
                    ProblemDetails.InvalidField("/contact", "email.invalid_format", "Enter an email address such as name@example.com."),
                    ProblemDetails.InvalidField(
                        "/address/country",
                        "country_code.unsupported",
                        "Enter a two-letter ISO country code in capitals, such as DE or US.",
                    ),
                    ProblemDetails.InvalidField("/quantities/0", "positive_int.non_positive", "Enter a number greater than zero."),
                ),
            ),
            failures.toProblemDetails(describe),
        )
    }
}
