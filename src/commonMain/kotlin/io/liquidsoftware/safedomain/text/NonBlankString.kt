package io.liquidsoftware.safedomain.text

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/**
 * A string containing at least one character that Kotlin does not classify as whitespace.
 *
 * Construction preserves the input exactly: no trimming, case folding, or normalization.
 * This guarantees nonblank text, not visible content or suitability for a particular domain.
 */
@JvmInline
value class NonBlankString private constructor(val value: String) {
    sealed interface Error {
        data object Blank : Error
    }

    companion object : ValueFactory<String, Error, NonBlankString> {
        /** Returns the unchanged [value], wrapped, or raises [Error.Blank] if it is blank. */
        context(raise: Raise<Error>)
        override fun from(value: String): NonBlankString {
            raise.ensure(value.isNotBlank()) { Error.Blank }
            return NonBlankString(value)
        }
    }
}
