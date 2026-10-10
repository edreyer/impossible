package io.liquidsoftware.safedomain.locale

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleTypesTest {
    @Test
    fun `country codes are assigned uppercase alpha-2 codes`() {
        for (raw in listOf("DE", "US", "JP", "BQ")) {
            assertEquals(raw, CountryCode.fromTrusted(raw).value)
        }
        for (raw in listOf("", "de", "USA", "EU", "XK", "ZZ", " DE")) {
            assertEquals(Either.Left(CountryCode.Error.UnsupportedCode(raw)), either<CountryCode.Error, CountryCode> { CountryCode.from(raw) })
        }
    }

    @Test
    fun `language tags accept the common canonical subset`() {
        for (raw in listOf("en", "en-US", "zh-Hant-TW", "es-419", "sr-Latn", "de-CH-1996", "ast")) {
            assertEquals(raw, LanguageTag.fromTrusted(raw).value)
        }
        assertEquals("zh", LanguageTag.fromTrusted("zh-Hant-TW").language)
        for (raw in listOf("", "EN", "en-us", "en_US", "e", "english", "en-", "en-US-x-private", "i-klingon", "zh-hant")) {
            assertEquals(Either.Left(LanguageTag.Error.InvalidFormat(raw)), either<LanguageTag.Error, LanguageTag> { LanguageTag.from(raw) })
        }
    }
}
