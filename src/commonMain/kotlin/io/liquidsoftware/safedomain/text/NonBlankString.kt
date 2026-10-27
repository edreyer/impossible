package io.liquidsoftware.safedomain.text

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.numeric.PositiveInt
import kotlin.jvm.JvmInline

/**
 * A string containing at least one character that Kotlin does not classify as whitespace.
 *
 * The default factory preserves the input exactly: no trimming, case folding, or
 * normalization. [bounded] adds a maximum length and an explicit, opt-in trim.
 * This guarantees nonblank text, not visible content or suitability for a particular domain.
 */
@JvmInline
public value class NonBlankString private constructor(public val value: String) {
    public sealed interface Error : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)

        public data object Blank : Error

        /** Lengths are counted in Unicode code points. */
        public data class TooLong(val maximum: Int, val actual: Int) : Error
    }

    public companion object : ValueFactory<String, Error, NonBlankString> {
        /** Returns the unchanged [value], wrapped, or raises [Error.Blank] if it is blank. */
        context(raise: Raise<Error>)
        override fun from(value: String): NonBlankString {
            raise.ensure(value.isNotBlank()) { Error.Blank }
            return NonBlankString(value)
        }

        /**
         * Returns a factory that also limits length to [maxLength] Unicode code points, the
         * unit SQL character columns use, so a surrogate pair counts once. With [trim], leading
         * and trailing whitespace is removed before both checks, and the stored value is the
         * trimmed text; without it, input is preserved exactly.
         */
        public fun bounded(maxLength: PositiveInt, trim: Boolean = false): ValueFactory<String, Error, NonBlankString> =
            object : ValueFactory<String, Error, NonBlankString> {
                context(raise: Raise<Error>)
                override fun from(value: String): NonBlankString {
                    val text = if (trim) value.trim() else value
                    raise.ensure(text.isNotBlank()) { Error.Blank }
                    val length = text.codePointLength()
                    raise.ensure(length <= maxLength.value) { Error.TooLong(maxLength.value, length) }
                    return NonBlankString(text)
                }
            }
    }
}

/** Code points in this string; an unpaired surrogate counts as one. */
internal fun String.codePointLength(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        val pair = this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()
        index += if (pair) 2 else 1
        count++
    }
    return count
}
