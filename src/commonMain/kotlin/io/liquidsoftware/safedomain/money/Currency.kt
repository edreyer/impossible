package io.liquidsoftware.safedomain.money

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/**
 * An ISO 4217 alphabetic code from the library's bundled code list, including historic
 * and fund codes. Input is preserved: lowercase codes and whitespace are rejected.
 * Validation is identical on every platform. This does not establish that a currency
 * is currently legal tender.
 */
@JvmInline
value class Currency private constructor(val code: String) {
    sealed interface Error {
        data class UnsupportedCode(val value: String) : Error
    }

    companion object : ValueFactory<String, Error, Currency> {
        context(raise: Raise<Error>)
        override fun from(value: String): Currency {
            raise.ensure(value in isoCurrencyCodes) { Error.UnsupportedCode(value) }
            return Currency(value)
        }
    }
}
