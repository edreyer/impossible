package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.FieldPath
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.identity.TypedId
import io.liquidsoftware.safedomain.locale.CountryCode
import io.liquidsoftware.safedomain.locale.LanguageTag
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Secret
import io.liquidsoftware.safedomain.text.Slug
import io.liquidsoftware.safedomain.time.TimeZoneId
import io.liquidsoftware.safedomain.web.DomainName
import io.liquidsoftware.safedomain.web.HttpUrl
import io.liquidsoftware.safedomain.web.IpAddress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Error descriptions are safe to show: stable codes, and never the rejected text. */
class MessageLawsTest {
    private val code = Regex("[a-z]+(?:_[a-z]+)*\\.[a-z]+(?:_[a-z]+)*")

    /** Every text parser, with the description of its failure. */
    private val describers: List<(String) -> ErrorMessage?> = listOf(
        { text -> attempt { NonBlankString.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { Slug.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { Secret.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { Email.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { Phone.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { CountryCode.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { LanguageTag.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { Currency.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { TimeZoneId.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { TypedId.factory<Unit>().from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { DomainName.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { IpAddress.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { HttpUrl.from(text) }.leftOrNull()?.let(ErrorMessages::describe) },
        { text -> attempt { Money.parser.from(Money.Text(text, text)) }.leftOrNull()?.head?.let(ErrorMessages::describe) },
    )

    @Test
    fun `descriptions have stable codes and never quote the rejected text`() = runTest {
        val inputs = Arb.choice(Arb.untrustedText(4..40), Arb.nearMiss(Email.arb().let { arb -> arbitrary { arb.bind().value } }))
        forAll(inputs) { text ->
            for (describe in describers) {
                val message = describe(text) ?: continue
                assertTrue(code.matches(message.code), "bad code ${message.code}")
                assertTrue(message.text.isNotBlank())
                if (text.isNotBlank()) {
                    assertFalse(text in message.text, "${message.code} quoted \"$text\"")
                    assertFalse(message.arguments.values.any { text in it }, "${message.code} quoted \"$text\" in its arguments")
                }
            }
        }
    }

    @Test
    fun `JSON pointers decode back to their field names`() = runTest {
        val names = Arb.list(Arb.untrustedText(0..8), 0..5)
        forAll(arbitrary { names.bind() to Arb.int(0..3).bind() }) { (segments, index) ->
            val path = segments.fold(FieldPath.ROOT) { path, name -> path.child(name) }.index(index)
            val decoded = path.toJsonPointer().split('/').drop(1).map { it.replace("~1", "/").replace("~0", "~") }
            assertEquals(segments + index.toString(), decoded)
        }
    }
}
