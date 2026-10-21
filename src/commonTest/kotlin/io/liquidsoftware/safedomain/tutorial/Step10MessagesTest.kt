package io.liquidsoftware.safedomain.tutorial.step10

import arrow.core.NonEmptyList
import arrow.core.raise.either
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.FieldError
import io.liquidsoftware.safedomain.ProblemDetails
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.toProblemDetails
import io.liquidsoftware.safedomain.tutorial.step03.Isbn
import io.liquidsoftware.safedomain.tutorial.step09.Address
import io.liquidsoftware.safedomain.tutorial.step09.AddressInput
import io.liquidsoftware.safedomain.tutorial.step09.Order
import io.liquidsoftware.safedomain.tutorial.step09.OrderInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// Code for docs/tutorial/10-messages-and-problem-details.md. Keep the two in step:
// TutorialSnippetsTest checks that every snippet in the chapter appears here.

fun describe(error: Isbn.Error): ErrorMessage = when (error) {
    is Isbn.Error.InvalidText -> ErrorMessages.describe(error.cause)
    Isbn.Error.NotThirteenDigits -> ErrorMessage("isbn.not_thirteen_digits", "Enter the 13 digits of an ISBN, without hyphens.")
    Isbn.Error.WrongCheckDigit -> ErrorMessage("isbn.wrong_check_digit", "Check the ISBN for a mistyped digit.")
}

fun describe(error: Address.Error): ErrorMessage = when (error) {
    is Address.Error.Street -> ErrorMessages.describe(error.cause)
    is Address.Error.City -> ErrorMessages.describe(error.cause)
    is Address.Error.Country -> ErrorMessages.describe(error.cause)
}

fun describe(error: Order.Error): ErrorMessage = when (error) {
    is Order.Error.InvalidCustomerId -> ErrorMessages.describe(error.cause)
    Order.Error.MissingEmail -> ErrorMessage("order.missing_email", "Enter an email address for the receipt.")
    is Order.Error.InvalidEmail -> ErrorMessages.describe(error.cause)
    is Order.Error.InvalidAddress -> describe(error.cause)
    is Order.Error.InvalidIsbn -> describe(error.cause)
    is Order.Error.NoBooks -> ErrorMessage("order.no_books", "Add at least one book to the order.")
    is Order.Error.InvalidTotal -> ErrorMessages.describe(error.cause)
    is Order.Error.TotalNotPositive -> ErrorMessages.describe(error.cause)
}

sealed interface PlaceOrderResponse {
    data class Placed(val order: Order) : PlaceOrderResponse
    data class Invalid(val problem: ProblemDetails) : PlaceOrderResponse
}

fun placeOrder(input: OrderInput): PlaceOrderResponse =
    either<NonEmptyList<FieldError<Order.Error>>, Order> { Order.from(input) }.fold(
        ifLeft = { failures -> PlaceOrderResponse.Invalid(failures.toProblemDetails({ describe(it) }, instance = "/orders")) },
        ifRight = { order -> PlaceOrderResponse.Placed(order) },
    )

val german: Map<String, String> = mapOf(
    "isbn.wrong_check_digit" to "Bitte prüfen Sie die ISBN auf einen Tippfehler.",
    "non_blank_string.too_long" to "Höchstens {maximum} Zeichen, nicht {actual}.",
)

fun localize(message: ErrorMessage, catalog: Map<String, String>): String {
    val template = catalog[message.code] ?: return message.text
    return message.arguments.entries.fold(template) { text, (name, value) -> text.replace("{$name}", value) }
}

class Step10MessagesTest {
    private val valid = OrderInput(
        customerId = "550e8400-e29b-41d4-a716-446655440000",
        email = "ada@example.com",
        shipTo = AddressInput(street = "12 Analytical Row", city = "London", country = "GB"),
        isbns = listOf("9780441013593"),
        total = Money.Text("12.99", "USD"),
    )

    @Test
    fun `every error has a stable code and an English default`() {
        assertEquals(ErrorMessage("non_blank_string.blank", "Enter some text."), ErrorMessages.describe(NonBlankString.Error.Blank))
        assertEquals("isbn.wrong_check_digit", describe(Isbn.Error.WrongCheckDigit).code)
    }

    @Test
    fun `a batch of field errors becomes one problem details response`() {
        val response = placeOrder(valid.copy(email = null, isbns = listOf("9780441013594"), total = Money.Text("12.999", "USD")))
        val problem = assertIs<PlaceOrderResponse.Invalid>(response).problem
        assertEquals(422, problem.status)
        assertEquals("/orders", problem.instance)
        assertEquals("The request has 3 problems.", problem.detail)
        assertEquals(
            listOf(
                ProblemDetails.InvalidField("/email", "order.missing_email", "Enter an email address for the receipt."),
                ProblemDetails.InvalidField("/isbns/0", "isbn.wrong_check_digit", "Check the ISBN for a mistyped digit."),
                ProblemDetails.InvalidField(
                    "/total",
                    "money.not_in_minor_units",
                    "Enter an amount with at most 2 decimal places.",
                    mapOf("decimal_places" to "2"),
                ),
            ),
            problem.errors,
        )
        assertIs<PlaceOrderResponse.Placed>(placeOrder(valid))
    }

    @Test
    fun `codes and arguments drive your own translations`() {
        assertEquals("Bitte prüfen Sie die ISBN auf einen Tippfehler.", localize(describe(Isbn.Error.WrongCheckDigit), german))
        assertEquals(
            "Höchstens 24 Zeichen, nicht 30.",
            localize(ErrorMessages.describe(NonBlankString.Error.TooLong(maximum = 24, actual = 30)), german),
        )
        assertEquals("Enter some text.", localize(ErrorMessages.describe(NonBlankString.Error.Blank), german))
    }
}
