# Step 9: Field paths

**You will learn** how to report each failure against the field that caused it,
including fields inside nested objects and lists, so an API client can highlight the
exact input to fix. This step puts most of the previous ones together into the
shop's order form.

Imports for this step: `io.liquidsoftware.safedomain.FieldError` and the path-aware
helpers `fieldAt`, `nestedFieldAt`, `eachFieldAt` and `flatFieldAt` from the root
package, plus `locale.CountryCode`, `collections.factory`, `collections.NonEmptyError`,
`Isbn` from step 3 and `CustomerId`/`customerIds` from step 8.

## Errors with an address

So far a failed `Listing` said *what* went wrong (`InvalidIsbn(...)`) but a client
had to work out *where* from the error's type. The path-aware helpers pair each error
with a `FieldPath` such as `ship_to.country` or `isbns[1]`, and a composite built with
them raises `NonEmptyList<FieldError<Error>>`.

Paths come from the property names, so they can't drift from the code. Pass
`name = ...` only when the wire name differs, as `customer_id` does below.

| Helper | Use it for | Path of a failure |
| --- | --- | --- |
| `Factory.fieldAt(raw) { ... }` | one value, also with `onMissing` for `required()` | `email` |
| `Composite.nestedFieldAt(raw) { ... }` | a nested composite whose factory reports paths | `ship_to.street` |
| `Factory.eachFieldAt(raws) { ... }` | every element of a list | `isbns[1]` |
| `Factory.flatFieldAt(raw) { ... }` | an accumulating factory without paths, such as `Money.parser` | `total`, once per error |
| `fieldAt { ... }` | a dependent step or a check across fields | the property's name |

## A nested address

An address is a composite of its own. Built with `fieldAt`, its failures carry paths
relative to the address:

```kotlin
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
```

`CountryCode` accepts the assigned ISO 3166-1 alpha-2 codes in capitals, so `"gb"`
fails.

## The order

```kotlin
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
            val email by Email.required().fieldAt(value.email, onMissing = { Error.MissingEmail }) {
                Error.InvalidEmail(it)
            }
            val shipTo by Address.nestedFieldAt(value.shipTo, name = "ship_to") { Error.InvalidAddress(it) }
            val isbns by Isbn.eachFieldAt(value.isbns) { Error.InvalidIsbn(it) }
            val amount by Money.parser.flatFieldAt(value.total, name = "total") { Error.InvalidTotal(it) }
            // Dependent fields come last: reading a failed field ends validation here.
            val books by fieldAt(name = "isbns") { NonEmptyList.factory<Isbn>().from(isbns) { Error.NoBooks(it) } }
            val total by fieldAt { PositiveMoney.from(amount) { Error.TotalNotPositive(it) } }
            Order(customer, email, shipTo, books, total)
        }
    }
}
```

The interesting lines:

- `nestedFieldAt` prefixes the address's own paths with `ship_to`, so a blank street
  is reported at `ship_to.street`. `Error.InvalidAddress` wraps `Address.Error`
  without repeating its cases.
- `eachFieldAt` reports each bad ISBN at `isbns[index]`; then the dependent `fieldAt`
  block (named `isbns` too) reports an empty list at `isbns` itself.
- `flatFieldAt` reports both of `Money.parser`'s possible errors at `total`, and the
  dependent block reports a zero total at `total` as well.
- The dependent blocks read fields declared *outside* them, and reading a field that
  failed ends the whole `validate` right there, with the errors collected so far. So
  they come after every independent field: if an ISBN is invalid, the order still
  reports every other independent field's problems, and only skips the checks that
  need valid ISBNs. (Fields declared *inside* a `field { }` block, as in steps 5 to 8,
  stop only that block.)

Every field wrong at once:

```text
customer_id      InvalidCustomerId(InvalidFormat)
email            MissingEmail
ship_to.street   InvalidAddress(Street(Blank))
ship_to.country  InvalidAddress(Country(UnsupportedCode))
isbns[1]         InvalidIsbn(NotThirteenDigits)
total            InvalidTotal(InvalidAmount)
total            InvalidTotal(InvalidCurrency(...))
```

## Rendering paths

`FieldPath.toString()` gives the dotted form above. `toJsonPointer()` gives the RFC
6901 form APIs use, such as `/ship_to/city` or `/isbns/1`. A check that belongs to no
single field can raise at `FieldPath.ROOT`, which renders as an empty pointer.

## What you have now

An order form that validates customer, contact, address, books and total, across
nested objects and lists, and reports every failure with the path of the input that caused it. The next step turns that into
an HTTP response.

The code and its tests are in
[Step09FieldPathsTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/step09/Step09FieldPathsTest.kt).

Next: [Step 10, messages and problem details](10-messages-and-problem-details.md).
