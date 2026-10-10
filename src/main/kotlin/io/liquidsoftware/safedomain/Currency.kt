package io.liquidsoftware.safedomain

import arrow.core.raise.Raise
import java.util.Currency as JdkCurrency

/**
 * An ISO 4217 code supported by the running JDK's currency data, including historic
 * and pseudo-currencies. Input is preserved: lowercase codes and whitespace are
 * rejected. This does not establish that a currency is currently legal tender.
 */
@JvmInline
value class Currency private constructor(val code: String) {
    sealed interface Error {
        data class UnsupportedCode(val value: String) : Error
    }

    companion object : ValueFactory<String, Error, Currency> {
        context(raise: Raise<Error>)
        override fun from(value: String): Currency {
            try {
                JdkCurrency.getInstance(value)
            } catch (_: IllegalArgumentException) {
                raise.raise(Error.UnsupportedCode(value))
            }
            return Currency(value)
        }
    }
}
