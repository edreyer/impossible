package io.liquidsoftware.safedomain.money

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/**
 * An ISO 4217 alphabetic code from the library's bundled code list, including historic
 * and fund codes. Input is preserved: lowercase codes and whitespace are rejected.
 * Validation is identical on every platform. This does not establish that a currency
 * is currently legal tender.
 */
@JvmInline
public value class Currency private constructor(public val code: String) {
    public sealed interface Error : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)

        public data class UnsupportedCode(val value: String) : Error
    }

    /**
     * Decimal places of the currency's minor unit, such as 2 for USD, 0 for JPY and
     * 3 for KWD; null for codes without one, such as XAU (gold) and XXX (no currency).
     */
    public val minorUnits: Int? get() = isoCurrencyMinorUnits.getValue(code)

    public companion object : ValueFactory<String, Error, Currency> {
        context(raise: Raise<Error>)
        override fun from(value: String): Currency {
            raise.ensure(value in isoCurrencyMinorUnits) { Error.UnsupportedCode(value) }
            return Currency(value)
        }
    }
}
