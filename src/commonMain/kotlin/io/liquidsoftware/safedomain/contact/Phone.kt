package io.liquidsoftware.safedomain.contact

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/**
 * International E.164-shaped text: '+' followed by 1..15 ASCII digits, the first
 * nonzero. Preserves input exactly; accepts no spacing, extensions, or national
 * formats. This establishes syntax, not a valid country code, assigned number,
 * reachability, or ownership. Equality uses the full preserved text.
 */
@JvmInline
public value class Phone private constructor(public val value: String) {
    public sealed interface Error : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)

        /** [toString] redacts [value], so a rejected number does not reach logs. */
        public data class InvalidFormat(val value: String) : Error {
            override fun toString(): String = "InvalidFormat(value=<redacted>)"
        }
    }

    public companion object : ValueFactory<String, Error, Phone> {
        private val internationalSyntax = Regex("\\+[1-9][0-9]{0,14}")

        context(raise: Raise<Error>)
        override fun from(value: String): Phone {
            raise.ensure(internationalSyntax.matches(value)) { Error.InvalidFormat(value) }
            return Phone(value)
        }
    }
}
