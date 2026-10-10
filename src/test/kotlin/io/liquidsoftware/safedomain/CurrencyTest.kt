package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CurrencyTest {
    @Test
    fun `accepts JDK supported ISO codes including currencies without a minor unit`() {
        for (raw in listOf("USD", "EUR", "JPY", "KWD", "XDR")) {
            assertEquals(raw, assertIs<Either.Right<Currency>>(either { Currency.from(raw) }).value.code)
        }
    }

    @Test
    fun `rejects unsupported codes without trimming or changing case`() {
        for (raw in listOf("", "usd", " USD", "USD ", "ZZZ", "US", "USDD")) {
            val result: Either<Currency.Error, Currency> = either { Currency.from(raw) }
            assertEquals(Either.Left(Currency.Error.UnsupportedCode(raw)), result)
        }
    }

    @Test
    fun `currency values behave as collection keys`() {
        val result = either {
            val usd = Currency.from("USD")
            assertEquals(2, setOf(usd, Currency.from("USD"), Currency.from("EUR")).size)
            assertEquals("dollar", mapOf(usd to "dollar")[Currency.from("USD")])
        }
        assertEquals(Either.Right(Unit), result)
    }
}
