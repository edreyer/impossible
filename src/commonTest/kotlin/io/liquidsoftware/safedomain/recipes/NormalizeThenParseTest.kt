package io.liquidsoftware.safedomain.recipes

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.fromEither
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import kotlin.test.Test
import kotlin.test.assertEquals

// Code for docs/recipes/normalize-then-parse.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the recipe appears here.

/**
 * Turns the ways people type a phone number into E.164 text: drops spaces, parentheses,
 * dots and hyphens, reads a leading `00` as `+`, and adds [defaultCountryCode] to a
 * national number. It only rewrites text; [Phone.from] still decides what is valid.
 */
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

/** The same policy as a factory, so it composes like any other: `field`, `fieldAt`, `fromEither`. */
val formPhone: ValueFactory<String, Phone.Error, Phone> = object : ValueFactory<String, Phone.Error, Phone> {
    context(raise: Raise<Phone.Error>)
    override fun from(value: String): Phone = phoneFromForm(value)
}

context(raise: Raise<Email.Error>)
fun emailFromForm(raw: String): Email = Email.from(raw.trim())

val displayName: ValueFactory<String, NonBlankString.Error, NonBlankString> =
    NonBlankString.bounded(PositiveInt.fromTrusted(80), trim = true)

class NormalizeThenParseTest {
    @Test
    fun `phone numbers typed by people normalize to E164 and then validate`() {
        assertEquals("+442079460000", formPhone.fromEither("020 7946 0000").getOrNull()?.value)
        assertEquals("+442079460000", formPhone.fromEither("(020) 7946-0000").getOrNull()?.value)
        assertEquals("+4930123456", formPhone.fromEither("0049 30 123456").getOrNull()?.value)
        assertEquals("+15550100", formPhone.fromEither("+1 555.0100").getOrNull()?.value)
        assertEquals(true, formPhone.fromEither("call me") is Either.Left)
    }

    @Test
    fun `email is trimmed and equality already ignores the case of the domain`() {
        val typed = either { emailFromForm(" Ann@Example.COM ") }.getOrNull()
        assertEquals(Email.fromEither("Ann@example.com").getOrNull(), typed)
        assertEquals("Ann@Example.COM", typed?.value)
    }

    @Test
    fun `bounded text can trim as its own opt in normalization`() {
        assertEquals("Ann Lee", displayName.fromEither("  Ann Lee ").getOrNull()?.value)
        assertEquals(Either.Left(NonBlankString.Error.Blank), displayName.fromEither("   "))
    }
}
