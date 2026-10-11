package io.liquidsoftware.safedomain.text

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/**
 * A URL-safe identifier: one or more groups of lowercase ASCII letters and digits joined
 * by single hyphens, such as `summer-sale-2026`. No leading, trailing, or repeated
 * hyphens. Input is preserved; nothing is lowercased or transliterated. Length is not
 * limited here; combine with a consuming-domain check where a limit applies.
 */
@JvmInline
public value class Slug private constructor(public val value: String) {
    public sealed interface Error {
        public data class InvalidFormat(val value: String) : Error
    }

    public companion object : ValueFactory<String, Error, Slug> {
        private val syntax = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

        context(raise: Raise<Error>)
        override fun from(value: String): Slug {
            raise.ensure(syntax.matches(value)) { Error.InvalidFormat(value) }
            return Slug(value)
        }
    }
}
