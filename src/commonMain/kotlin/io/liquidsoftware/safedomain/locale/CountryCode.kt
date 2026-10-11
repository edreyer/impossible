package io.liquidsoftware.safedomain.locale

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/**
 * An officially assigned ISO 3166-1 alpha-2 country code from the library's bundled
 * list, such as `DE` or `US`. Input is preserved: lowercase codes and whitespace are
 * rejected. Reserved and user-assigned codes (`EU`, `XK`, `ZZ`) are rejected.
 * Validation is identical on every platform.
 */
@JvmInline
public value class CountryCode private constructor(public val value: String) {
    public sealed interface Error {
        public data class UnsupportedCode(val value: String) : Error
    }

    public companion object : ValueFactory<String, Error, CountryCode> {
        context(raise: Raise<Error>)
        override fun from(value: String): CountryCode {
            raise.ensure(value in isoCountryCodes) { Error.UnsupportedCode(value) }
            return CountryCode(value)
        }
    }
}
