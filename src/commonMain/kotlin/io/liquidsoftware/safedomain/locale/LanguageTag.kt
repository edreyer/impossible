package io.liquidsoftware.safedomain.locale

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/**
 * A well-formed BCP 47 language tag in canonical case, limited to the common subset:
 * a 2-3 letter language, an optional 4-letter script, an optional 2-letter or 3-digit
 * region, and optional variants, such as `en`, `en-US`, `zh-Hant-TW` or `es-419`.
 * Extensions, private use, extended language subtags, and grandfathered tags are
 * rejected. Case must be canonical (`en-US`, not `EN-us`), so equality is simple text
 * equality. This checks syntax only, not that subtags are registered.
 */
@JvmInline
public value class LanguageTag private constructor(public val value: String) {
    public sealed interface Error : ValidationError {
        public data class InvalidFormat(val value: String) : Error
    }

    /** The primary language subtag, such as `en`. */
    public val language: String get() = value.substringBefore('-')

    public companion object : ValueFactory<String, Error, LanguageTag> {
        // The trailing `$` matters on JS, where Regex.matches accepts only the first match
        // and does not backtrack to a longer one: without it, `de-1996` matches `de-199`
        // with `199` as a region and is rejected, while the JVM accepts the variant.
        private val syntax = Regex(
            "[a-z]{2,3}" +
                "(?:-[A-Z][a-z]{3})?" +
                "(?:-(?:[A-Z]{2}|[0-9]{3}))?" +
                "(?:-(?:[a-z0-9]{5,8}|[0-9][a-z0-9]{3}))*$",
        )

        context(raise: Raise<Error>)
        override fun from(value: String): LanguageTag {
            raise.ensure(syntax.matches(value)) { Error.InvalidFormat(value) }
            return LanguageTag(value)
        }
    }
}
