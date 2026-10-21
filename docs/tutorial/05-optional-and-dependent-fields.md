# Step 5: Optional and dependent fields

**You will learn** how to accept nullable input without weakening null safety, how to
require a value that may be missing, and how to check rules that involve more than one
field.

Imports for this step: everything from step 4, plus `io.liquidsoftware.safedomain.optional`,
`io.liquidsoftware.safedomain.required`, `arrow.core.raise.ensure`,
`arrow.core.raise.ensureNotNull`, `contact.Email` and `contact.Phone`.

## A sign-up form

Forms and JSON bodies arrive with missing fields, so their input types are nullable:

```kotlin
/** What a sign-up form posts: any field may be missing. */
data class SignupForm(val name: String?, val nickname: String?, val email: String?, val phone: String?)
```

Factories take non-null input, so `NonBlankString.field(form.name, ...)` doesn't
compile. You choose, field by field, what a missing value means:

| You write | Missing input | Present input |
| --- | --- | --- |
| `Factory.required()` | raises your "missing" error | validated in full |
| `Factory.optional()` | becomes `null` | validated in full |

Note "validated in full": an empty string is *present*, so an optional nickname of
`""` is still rejected as blank, not treated as absent.

## Reachable customers

The shop needs to be able to contact every customer, by email, by phone, or both.
Modelling that as two nullable fields would allow a customer with neither. A sealed
type says exactly what's allowed:

```kotlin
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
```

`Email` checks address syntax and `Phone` checks E.164 shape (`+442071838750`). Both
keep the input exactly as given and keep it out of their errors' `toString`, so
personal data doesn't end up in logs.

Now the customer. The new piece is `field { }`, a field computed by a block:

```kotlin
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
```

What each line does:

- `required().field(raw, onMissing, onError)` takes two mappers: one for absence and
  one for an invalid value.
- `optional().field(...)` gives a `NonBlankString?`, so `Customer.nickname` is
  nullable and callers keep Kotlin's null checks.
- `field { ... }` declares fields of its own and then reads them, which is allowed
  inside the block. If the email or phone is invalid, the block stops at that read and
  reports those errors. Only when both are valid does it go on to check that at least
  one is present.

A block's failure stops only that block. With no name, a bad email and a bad phone,
you get all three errors:
`[MissingName, InvalidEmail(...), InvalidPhone(...)]`.

## Rules across fields

The same block shape expresses a rule that relates two fields. A preorder may not
exceed the per-customer limit, which only makes sense once both numbers are valid:

```kotlin
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
```

`copies = 3, limit = 2` gives `[OverLimit(3, 2)]`; `copies = 0, limit = 0` gives both
`InvalidCopies` and `InvalidLimit`, and the limit check never runs on invalid numbers.

## When to use which

- `optional()` when absence means only "not given".
- `required()` when nullable boundary data must become a non-null domain value. Never
  `!!`, never a silent default.
- A sealed type (or Arrow's `Ior`) when absence carries meaning, or when at least one
  of several values must be present.

## What you have now

A `Customer` that always has a name and always has a way to be contacted, built from
a form where anything may be missing, with every problem reported at once.

The code and its tests are in
[Step05OptionalAndDependentTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial/step05/Step05OptionalAndDependentTest.kt).

Next: [Step 6, collections](06-collections.md).
