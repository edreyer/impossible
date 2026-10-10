package io.liquidsoftware.safedomain.contact

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory

/**
 * International E.164-shaped text: '+' followed by 1..15 ASCII digits, the first
 * nonzero. Preserves input exactly; accepts no spacing, extensions, or national
 * formats. This establishes syntax, not a valid country code, assigned number,
 * reachability, or ownership. Equality uses the full preserved text.
 */
@JvmInline
value class Phone private constructor(val value: String) {
    sealed interface Error {
        data class InvalidFormat(val value: String) : Error
    }

    companion object : ValueFactory<String, Error, Phone> {
        private val internationalSyntax = Regex("\\+[1-9][0-9]{0,14}")

        context(raise: Raise<Error>)
        override fun from(value: String): Phone {
            raise.ensure(internationalSyntax.matches(value)) { Error.InvalidFormat(value) }
            return Phone(value)
        }
    }
}
