package io.liquidsoftware.safedomain.text

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory

/**
 * A non-blank sensitive string, such as a password, API key, or token. [toString] never
 * shows the content, so a secret does not reach logs or error messages by accident;
 * read it explicitly with [reveal] at the point of use. Input is preserved exactly.
 * Equality compares content in time that does not depend on where the first difference
 * is; [hashCode] uses only the length, so hashing reveals nothing more than that.
 */
public class Secret private constructor(private val value: String) {
    public sealed interface Error : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)

        /** The input was empty or only whitespace. Carries no input, by design. */
        public data object Blank : Error
    }

    /** The secret content. Call it only where the value is used, never to log or display it. */
    public fun reveal(): String = value

    override fun equals(other: Any?): Boolean {
        if (other !is Secret) return false
        val length = maxOf(value.length, other.value.length)
        var difference = value.length xor other.value.length
        for (index in 0 until length) {
            difference = difference or (value.charCodeAt(index) xor other.value.charCodeAt(index))
        }
        return difference == 0
    }

    override fun hashCode(): Int = value.length

    override fun toString(): String = "Secret(<redacted>)"

    public companion object : ValueFactory<String, Error, Secret> {
        context(raise: Raise<Error>)
        override fun from(value: String): Secret {
            raise.ensure(value.isNotBlank()) { Error.Blank }
            return Secret(value)
        }

        private fun String.charCodeAt(index: Int): Int = if (index < length) this[index].code else -1
    }
}
