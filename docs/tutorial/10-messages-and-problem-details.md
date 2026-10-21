# Step 10: Messages and problem details

**You will learn** how to describe errors with stable codes and default English text,
how to turn the order form's failures into an RFC 9457 problem details response, and
how a client or your own code can translate the messages.

Imports for this step: `io.liquidsoftware.safedomain.ErrorMessage`,
`io.liquidsoftware.safedomain.ErrorMessages`, `io.liquidsoftware.safedomain.ProblemDetails`,
`io.liquidsoftware.safedomain.toProblemDetails`, `FieldError`, and the `Order`,
`Address` and `Isbn` types from steps 3 and 9.

## Every error has a code

`ErrorMessages.describe(error)` has an overload for every error the library defines.
Each returns an `ErrorMessage` with:

- a stable `code` such as `email.invalid_format` or `money.not_in_minor_units`. Codes
  are public API and never renamed, so clients can branch on them and translators can
  key on them;
- a default English `text`, such as "Enter some text.";
- the `arguments` a translation needs, such as `maximum` and `actual` for a text
  that's too long.

Descriptions never quote the rejected input. It may be personal data, a password, or
text an attacker chose, and the caller already has it anyway.

## Describing your own errors

Write a `describe` for each of your error types. Delegate to the library for wrapped
errors, and give your own cases codes of their own:

```kotlin
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
```

The `when`s are exhaustive, so adding a case to `Order.Error` won't compile until it
has a message. `NoBooks` gets a message specific to orders instead of the library's
generic "Add at least one item."

## An HTTP response

`toProblemDetails(describe)` turns a batch of `FieldError`s into a `ProblemDetails`:
by default status 422, a fixed title, a `detail` that counts the problems, and an
`errors` list with each field's JSON Pointer, code, text and arguments:

```kotlin
sealed interface PlaceOrderResponse {
    data class Placed(val order: Order) : PlaceOrderResponse
    data class Invalid(val problem: ProblemDetails) : PlaceOrderResponse
}

fun placeOrder(input: OrderInput): PlaceOrderResponse =
    either<NonEmptyList<FieldError<Order.Error>>, Order> { Order.from(input) }.fold(
        ifLeft = { failures -> PlaceOrderResponse.Invalid(failures.toProblemDetails({ describe(it) }, instance = "/orders")) },
        ifRight = { order -> PlaceOrderResponse.Placed(order) },
    )
```

An order with no email, a mistyped ISBN and a total of `12.999` renders, once your
JSON library writes it as `application/problem+json` (empty `arguments` left out), as:

```json
{
  "type": "about:blank",
  "title": "The request is not valid.",
  "status": 422,
  "detail": "The request has 3 problems.",
  "instance": "/orders",
  "errors": [
    { "pointer": "/email", "code": "order.missing_email", "detail": "Enter an email address for the receipt." },
    { "pointer": "/isbns/0", "code": "isbn.wrong_check_digit", "detail": "Check the ISBN for a mistyped digit." },
    { "pointer": "/total", "code": "money.not_in_minor_units", "detail": "Enter an amount with at most 2 decimal places.",
      "arguments": { "decimal_places": "2" } }
  ]
}
```

`ProblemDetails` is plain data, so it works with any JSON library or web framework.
`title`, `status`, `type`, `instance` and `detail` are parameters; pass `detail = null`
to leave it out.

## Translating

The text is a default. To show messages in another language, look the code up in your
own catalogue and fill in the arguments; fall back to the English text for codes you
haven't translated yet:

```kotlin
val german: Map<String, String> = mapOf(
    "isbn.wrong_check_digit" to "Bitte prüfen Sie die ISBN auf einen Tippfehler.",
    "non_blank_string.too_long" to "Höchstens {maximum} Zeichen, nicht {actual}.",
)

fun localize(message: ErrorMessage, catalog: Map<String, String>): String {
    val template = catalog[message.code] ?: return message.text
    return message.arguments.entries.fold(template) { text, (name, value) -> text.replace("{$name}", value) }
}
```

Because the problem details carry codes and arguments, a client can do the same on its
side instead.

## What you have now

The order form from step 9, answering an invalid request with a standard, machine
readable and translatable error response, with no error-to-text mapping hidden in a
controller.

The code and its tests are in
[Step10MessagesTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/Step10MessagesTest.kt).

Next: [Step 11, JSON](11-json.md).
