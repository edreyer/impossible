# Normalize, then parse

The library's types never normalize input. `Phone.from("020 7946 0000")` fails, because
the type's invariant is E.164 syntax and repairing text silently would hide what the
user actually typed. When your product wants to accept friendlier input, write the
normalization yourself, as a separate, visible step, and hand the result to the factory.
The factory still has the last word on validity.

This recipe is compiled: its code lives in
[NormalizeThenParseTest.kt](../../src/commonTest/kotlin/io/liquidsoftware/safedomain/recipes/NormalizeThenParseTest.kt)
and runs on every target.

## Phone numbers

People type spaces, parentheses, dots, hyphens, a `00` international prefix, or a
national number with no country code at all. Rewrite those, then parse:

```kotlin
fun normalizePhone(raw: String, defaultCountryCode: String): String {
    val compact = raw.trim().filterNot { it == ' ' || it == '(' || it == ')' || it == '.' || it == '-' }
    return when {
        compact.startsWith("+") -> compact
        compact.startsWith("00") -> "+" + compact.drop(2)
        else -> "+" + defaultCountryCode + compact.removePrefix("0")
    }
}

context(raise: Raise<Phone.Error>)
fun phoneFromForm(raw: String): Phone = Phone.from(normalizePhone(raw, defaultCountryCode = "44"))
```

`"020 7946 0000"` becomes `+442079460000`, `"0049 30 123456"` becomes `+4930123456`,
and `"call me"` still fails with `Phone.Error.InvalidFormat`. Wrap the policy in a
factory and it composes like any library type, in `validate { }`, with `fieldAt`, or
through `fromEither`:

```kotlin
val formPhone: ValueFactory<String, Phone.Error, Phone> = object : ValueFactory<String, Phone.Error, Phone> {
    context(raise: Raise<Phone.Error>)
    override fun from(value: String): Phone = phoneFromForm(value)
}
```

`Phone` checks syntax only: not that the country code exists, that the number is
assigned, or that it can receive calls. A JVM team that needs real numbering-plan
rules can run Google's [libphonenumber](https://github.com/google/libphonenumber) as the
normalization step (format to E.164) and still finish with `Phone.from`.

## Email addresses

Trim, then parse. Don't lowercase the whole address: the local part is case-sensitive
by the standard, and `Email` keeps it as typed. The domain is already compared without
regard to case, so `Ann@Example.COM` equals `Ann@example.com`:

```kotlin
context(raise: Raise<Email.Error>)
fun emailFromForm(raw: String): Email = Email.from(raw.trim())
```

Lowercase the domain yourself only if you need a canonical form for display or for a
database unique index.

## Text with surrounding whitespace

`NonBlankString.bounded(maxLength, trim = true)` is the one normalization the library
offers, and it is opt-in: leading and trailing whitespace is removed before the blank
and length checks, and the stored value is the trimmed text:

```kotlin
val displayName: ValueFactory<String, NonBlankString.Error, NonBlankString> =
    NonBlankString.bounded(PositiveInt.fromTrusted(80), trim = true)
```
