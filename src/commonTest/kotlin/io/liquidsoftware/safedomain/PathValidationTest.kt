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
import kotlin.test.assertFailsWith

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
            val contact by Email.required().fieldAt(value.contact, onMissing = {
                Error.ContactMissing
            }) { Error.Contact(it) }
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

/** A nested boundary composite: library errors are recorded as they are. */
private data class BoundaryAddress(val street: NonBlankString, val country: CountryCode) {
    data class Input(val street: String, val country: String)

    companion object : ValueFactory<Input, NonEmptyList<FieldError<ValidationError>>, BoundaryAddress> {
        context(raise: Raise<NonEmptyList<FieldError<ValidationError>>>)
        override fun from(value: Input): BoundaryAddress = validate {
            val street by NonBlankString.fieldAt(value.street)
            val country by CountryCode.fieldAt(value.country)
            BoundaryAddress(street, country)
        }
    }
}

/** A consumer error that joins a boundary composite. */
private data class TooManyItems(val maximum: Int) : ValidationError

/** A boundary composite with no wrapper errors: every identity overload, plus a precise and a dependent field. */
private data class Signup(
    val email: Email,
    val address: BoundaryAddress,
    val quantities: List<PositiveInt>,
    val price: Money,
    val nickname: NonBlankString,
    val items: Int,
) {
    data class Input(
        val email: String?,
        val address: BoundaryAddress.Input,
        val quantities: List<Int>,
        val price: Money.Text,
        val nickname: String,
    )

    companion object : ValueFactory<Input, NonEmptyList<FieldError<ValidationError>>, Signup> {
        context(raise: Raise<NonEmptyList<FieldError<ValidationError>>>)
        override fun from(value: Input): Signup = validate {
            val email by Email.required().fieldAt(value.email)
            val address by BoundaryAddress.nestedFieldAt(value.address)
            val quantities by PositiveInt.eachFieldAt(value.quantities)
            val price by Money.parser.flatFieldAt(value.price, name = "unit_price")
            // The precise form, with a lambda, still binds in the same block.
            val nickname by NonBlankString.fieldAt(value.nickname) { it }
            val items by fieldAt {
                ensure(quantities.size <= 3) { TooManyItems(3) }
                quantities.size
            }
            Signup(email, address, quantities, price, nickname, items)
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
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    FieldError(FieldPath.of("contact"), Shipment.Error.ContactMissing),
                    FieldError(FieldPath.of("quantities").index(1), Shipment.Error.Quantity(PositiveInt.Error.NonPositive(0))),
                    FieldError(FieldPath.of("quantities").index(2), Shipment.Error.Quantity(PositiveInt.Error.NonPositive(-3))),
                ),
            ),
            result,
        )
    }

    @Test
    fun `nested composites prefix their own paths`() {
        val result = shipment(valid.copy(address = Address.Input(" ", "XX")))
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    FieldError(
                        FieldPath.of("address").child("street"),
                        Shipment.Error.Address(Address.Error.Street(NonBlankString.Error.Blank)),
                    ),
                    FieldError(
                        FieldPath.of("address").child("country"),
                        Shipment.Error.Address(Address.Error.Country(CountryCode.Error.UnsupportedCode("XX"))),
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun `an explicit name overrides the property name and every flat error shares it`() {
        val result = shipment(valid.copy(price = Money.Text("x", "ZZZ")))
        val path = FieldPath.of("unit_price")
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    FieldError(path, Shipment.Error.Price(Money.Error.InvalidAmount("x"))),
                    FieldError(path, Shipment.Error.Price(Money.Error.InvalidCurrency(Currency.Error.UnsupportedCode("ZZZ")))),
                ),
            ),
            result,
        )
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
            Either.Left(
                nonEmptyListOf(
                    FieldError(FieldPath.of("contact"), Shipment.Error.Contact(Email.Error.InvalidFormat("not an email"))),
                ),
            ),
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

    @Test
    fun `problem details carry message arguments and take a caller supplied detail`() {
        val failures = nonEmptyListOf(FieldError(FieldPath.of("items"), 50))
        val describe = { maximum: Int ->
            ErrorMessage("order.too_many_items", "Order at most $maximum items.", mapOf("maximum" to "$maximum"))
        }
        val field = ProblemDetails.InvalidField("/items", "order.too_many_items", "Order at most 50 items.", mapOf("maximum" to "50"))

        assertEquals(listOf(field), failures.toProblemDetails(describe).errors)
        assertEquals("The request has 1 problem.", failures.toProblemDetails(describe).detail)
        assertEquals("Die Anfrage ist ungültig.", failures.toProblemDetails(describe, detail = "Die Anfrage ist ungültig.").detail)
        assertEquals(null, failures.toProblemDetails(describe, detail = null).detail)
    }

    private val validSignup = Signup.Input(
        email = "ada@example.com",
        address = BoundaryAddress.Input("1 Main St", "GB"),
        quantities = listOf(1, 2),
        price = Money.Text("9.99", "USD"),
        nickname = "Ada",
    )

    @Test
    fun `boundary mode records library errors as they are at their paths`() {
        val input = validSignup.copy(
            email = "nope",
            address = BoundaryAddress.Input(" ", "gb"),
            quantities = listOf(1, 0),
            price = Money.Text("x", "usd"),
            nickname = "",
        )
        val priceErrors = either { Money.parser.from(input.price) }.leftOrNull()!!
        val expected: List<FieldError<ValidationError>> = listOf(
            FieldError(FieldPath.of("email"), Email.Error.InvalidFormat("nope")),
            FieldError(FieldPath.of("address").child("street"), NonBlankString.Error.Blank),
            FieldError(FieldPath.of("address").child("country"), either { CountryCode.from("gb") }.leftOrNull()!!),
            FieldError(FieldPath.of("quantities").index(1), PositiveInt.Error.NonPositive(0)),
        ) + priceErrors.map { FieldError(FieldPath.of("unit_price"), it) } +
            FieldError(FieldPath.of("nickname"), NonBlankString.Error.Blank)
        assertEquals(expected, either { Signup.from(input) }.leftOrNull()?.toList())
    }

    @Test
    fun `boundary mode records a missing required value and a consumer error`() {
        val result = either { Signup.from(validSignup.copy(email = null, quantities = listOf(1, 1, 1, 1))) }
        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    FieldError(FieldPath.of("email"), RequiredError.Missing),
                    FieldError(FieldPath.of("items"), TooManyItems(3)),
                ),
            ),
            result,
        )
    }

    @Test
    fun `boundary mode builds the composite from valid input`() {
        val signup = either { Signup.from(validSignup) }.getOrNull()!!
        assertEquals("ada@example.com", signup.email.value)
        assertEquals(2, signup.items)
    }

    @Test
    fun `boundary failures become problem details with one call`() {
        val failures = either { Signup.from(validSignup.copy(email = null, quantities = listOf(0))) }.leftOrNull()!!
        assertEquals(
            listOf(
                ProblemDetails.InvalidField("/email", "required.missing", "A value is required."),
                ProblemDetails.InvalidField("/quantities/0", "positive_int.non_positive", "Enter a number greater than zero."),
            ),
            failures.toProblemDetails().errors,
        )
        assertEquals("The request has 2 problems.", failures.toProblemDetails().detail)
    }

    @Test
    fun `a consumer error is described by the consumer and library errors by the dispatcher`() {
        val input = validSignup.copy(nickname = " ", quantities = listOf(1, 1, 1, 1))
        val failures = either { Signup.from(input) }.leftOrNull()!!
        val describe = { error: ValidationError ->
            when (error) {
                is TooManyItems -> ErrorMessage("signup.too_many_items", "Choose at most ${error.maximum} items.")
                else -> ErrorMessages.describe(error)
            }
        }
        assertEquals(
            listOf("non_blank_string.blank", "signup.too_many_items"),
            failures.toProblemDetails(describe).errors.map { it.code },
        )
    }

    @Test
    fun `the dispatcher rejects an error the library does not define`() {
        assertFailsWith<IllegalArgumentException> { ErrorMessages.describe(TooManyItems(3)) }
    }
}
