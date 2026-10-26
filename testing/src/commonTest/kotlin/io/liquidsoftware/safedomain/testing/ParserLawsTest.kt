package io.liquidsoftware.safedomain.testing

import arrow.core.Either
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.map
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.fromEither
import io.liquidsoftware.safedomain.identity.TypedId
import io.liquidsoftware.safedomain.locale.CountryCode
import io.liquidsoftware.safedomain.locale.LanguageTag
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.Decimal
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
import kotlin.test.assertTrue

/**
 * Every text parser is total: on any input it returns a value or a typed error, never an
 * exception. A parsed value renders to text that parses to the same value, so parsing
 * is idempotent and normalizing types settle after one step.
 */
class ParserLawsTest {
    /** A parser under test, with how to render what it produced. */
    private class Parser<A>(
        val name: String,
        val valid: Arb<String>,
        val render: (A) -> String,
        val parse: (String) -> Either<*, A>,
    )

    private val parsers: List<Parser<*>> = listOf(
        Parser(
            "NonBlankString",
            NonBlankString.arb().map {
                it.value
            },
            { it.value },
        ) { attempt { NonBlankString.from(it) } },
        Parser("Slug", Slug.arb().map { it.value }, { it.value }) { attempt { Slug.from(it) } },
        Parser("Secret", Secret.arb().map { it.reveal() }, { it.reveal() }) { attempt { Secret.from(it) } },
        Parser("Email", Email.arb().map { it.value }, { it.value }) { attempt { Email.from(it) } },
        Parser("Phone", Phone.arb().map { it.value }, { it.value }) { attempt { Phone.from(it) } },
        Parser("CountryCode", CountryCode.arb().map { it.value }, { it.value }) { attempt { CountryCode.from(it) } },
        Parser("LanguageTag", LanguageTag.arb().map { it.value }, { it.value }) { attempt { LanguageTag.from(it) } },
        Parser("Currency", Currency.arb().map { it.code }, { it.code }) { attempt { Currency.from(it) } },
        Parser("TimeZoneId", TimeZoneId.arb().map { it.value }, { it.value }) { attempt { TimeZoneId.from(it) } },
        Parser("TypedId", TypedId.arb<Unit>().map { it.value.toString() }, { it.value.toString() }) {
            attempt { TypedId.factory<Unit>().from(it) }
        },
        Parser("DomainName", DomainName.arb().map { it.value }, { it.value }) { attempt { DomainName.from(it) } },
        Parser(
            "IpAddress",
            IpAddress.arb().map {
                it.toString()
            },
            { it.toString() },
        ) { attempt { IpAddress.from(it) } },
        Parser("HttpUrl", HttpUrl.arb().map { it.value }, { it.value }) { attempt { HttpUrl.from(it) } },
        Parser("Decimal", Decimal.arb().map { it.toString() }, { it.toString() }) { text ->
            Decimal.parseOrNull(text)?.let { Either.Right(it) } ?: Either.Left(Unit)
        },
        Parser("Money", Money.arb().map { "${it.amount.toPlainString()} ${it.currency.code}" }, {
            "${it.amount.toPlainString()} ${it.currency.code}"
        }) { text ->
            attempt { Money.parser.from(Money.Text(text.substringBefore(' '), text.substringAfter(' ', ""))) }
        },
    )

    @Test
    fun `parsers never throw and reparse what they produce`() = runTest {
        for (parser in parsers) checkParser(parser)
    }

    private suspend fun <A> checkParser(parser: Parser<A>) {
        val inputs = Arb.choice(Arb.untrustedText(), Arb.nearMiss(parser.valid), parser.valid)
        forAll(inputs, iterations = 600) { text ->
            val result = try {
                parser.parse(text)
            } catch (exception: Throwable) {
                throw AssertionError("${parser.name} threw on \"$text\"", exception)
            }
            result.onRight { value ->
                val reparsed = parser.parse(parser.render(value))
                assertEquals(Either.Right(value), reparsed, "${parser.name} did not reparse its own output for \"$text\"")
            }
        }
    }

    @Test
    fun `generated valid text is always accepted`() = runTest {
        for (parser in parsers) {
            forAll(parser.valid) { text ->
                assertTrue(parser.parse(text).isRight(), "${parser.name} rejected \"$text\"")
            }
        }
    }

    /** A text factory and generated valid text for it. */
    private class TextFactory<Error, A>(val name: String, val factory: ValueFactory<String, Error, A>, val valid: Arb<String>)

    private val textFactories: List<TextFactory<*, *>> = listOf(
        TextFactory("NonBlankString", NonBlankString, NonBlankString.arb().map { it.value }),
        TextFactory("Slug", Slug, Slug.arb().map { it.value }),
        TextFactory("Secret", Secret, Secret.arb().map { it.reveal() }),
        TextFactory("Email", Email, Email.arb().map { it.value }),
        TextFactory("Phone", Phone, Phone.arb().map { it.value }),
        TextFactory("CountryCode", CountryCode, CountryCode.arb().map { it.value }),
        TextFactory("LanguageTag", LanguageTag, LanguageTag.arb().map { it.value }),
        TextFactory("Currency", Currency, Currency.arb().map { it.code }),
        TextFactory("TimeZoneId", TimeZoneId, TimeZoneId.arb().map { it.value }),
        TextFactory("TypedId", TypedId.factory<Unit>(), TypedId.arb<Unit>().map { it.value.toString() }),
        TextFactory("DomainName", DomainName, DomainName.arb().map { it.value }),
        TextFactory("IpAddress", IpAddress, IpAddress.arb().map { it.toString() }),
        TextFactory("HttpUrl", HttpUrl, HttpUrl.arb().map { it.value }),
    )

    @Test
    fun `fromEither agrees with either around from on any text`() = runTest {
        for (entry in textFactories) checkFromEither(entry)
    }

    private suspend fun <Error, A> checkFromEither(entry: TextFactory<Error, A>) {
        forAll(Arb.choice(Arb.untrustedText(), entry.valid), iterations = 200) { text ->
            assertEquals(
                attempt {
                    entry.factory.from(text)
                },
                entry.factory.fromEither(text),
                "${entry.name} on \"$text\"",
            )
        }
    }
}
