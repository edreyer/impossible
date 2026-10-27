# Step 9b: Boundary mode

**You will learn** a shorter way to write a composite whose only job is to check a
request: no wrapper error per field, and one call to turn its failures into an HTTP
response.

Imports for this step: `io.liquidsoftware.safedomain.ValidationError`,
`io.liquidsoftware.safedomain.ErrorMessage`, `io.liquidsoftware.safedomain.toProblemDetails`
and `io.liquidsoftware.safedomain.fromEither`, plus the step 9 imports.

## Why step 9's order has so many errors

Step 9's `Order` declares eight error cases, and seven of them only wrap a library
error: `InvalidEmail(Email.Error)`, `InvalidTotal(Money.Error)`, and so on. That is the
right model when your domain *branches* on the errors: a checkout that offers to fix
the address but rejects a bad total needs to tell them apart in a `when`. A request
handler that only turns the errors into a response doesn't; for it the wrappers are
ceremony.

Every error the library defines implements one interface, `ValidationError`, whose only
member is `describe()`: the error's stable code and default text. A composite that raises `NonEmptyList<FieldError<ValidationError>>` can call the
path-aware helpers *without* the lambda, and each library error is recorded as it is.

## The address and the order, in boundary mode

```kotlin
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
```

`Isbn` is the shop's own type from step 3, and its error is not a library error, so it
joins the boundary through a small wrapper that implements `ValidationError`, which means
it must say how it is described. The precise form of a helper, the one with a lambda,
works in the same block:

```kotlin
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
```

Side by side with step 9, the fields and their paths are the same, and so are the
rules; only the error model differs. A missing email is now `RequiredError.Missing`
at `email`, a blank street is `NonBlankString.Error.Blank` at `ship_to.street`.

## One call to a response

`toProblemDetails()` with no arguments describes every failure with its own
`describe()`, the shop's `InvalidIsbn` included, so
`OrderRequest.fromEither(input).mapLeft { it.toProblemDetails() }` is the whole error
path of the handler. Because `describe()` is part of `ValidationError`, the compiler
rejects an error that joins the boundary without a description; nothing can reach the
response undescribed. Pass your own `describe` function to `toProblemDetails` only to
change the wording, for example to translate.

## Which to use

- Precise errors (step 9) inside the domain, wherever code branches on what went wrong.
- Boundary mode at the edge, where the only consumer is a message or a response.

The two mix: a domain composite with precise errors can sit behind a boundary request,
and the boundary request can wrap a precise error, as `InvalidIsbn` does.

The code and its tests are in
[Step09bBoundaryModeTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/step09b/Step09bBoundaryModeTest.kt).

Next: [Step 10, messages and problem details](10-messages-and-problem-details.md).
