package io.liquidsoftware.safedomain.contact

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An ASCII dot-atom mailbox with a DNS-label domain (including single-label domains).
 * The local part has at most 64 characters, each domain label 63, and the whole
 * address 254. Quoted local parts, address literals, and Unicode are unsupported;
 * already encoded ASCII IDN labels are accepted. This is syntax validation only.
 * Input is preserved exactly in [value]. Equality and hashing ignore ASCII case in the
 * domain, which DNS treats case-insensitively, and keep the local part case-sensitive,
 * as RFC 5321 permits. [toString] shows the address as written.
 */
public class Email private constructor(public val value: String) {
    public sealed interface Error {
        /** [toString] redacts [value], so a rejected address does not reach logs. */
        public data class InvalidFormat(val value: String) : Error {
            override fun toString(): String = "InvalidFormat(value=<redacted>)"
        }
    }

    /** The text before the last '@', as written. */
    public val localPart: String get() = value.substringBeforeLast('@')

    /** The text after the last '@', as written. */
    public val domain: String get() = value.substringAfterLast('@')

    private val comparisonKey: String get() = localPart + "@" + domain.lowercase()

    override fun equals(other: Any?): Boolean = other is Email && comparisonKey == other.comparisonKey

    override fun hashCode(): Int = comparisonKey.hashCode()

    override fun toString(): String = "Email(value=$value)"

    public companion object : ValueFactory<String, Error, Email> {
        private const val MAX_LENGTH = 254
        private const val MAX_LOCAL_PART_LENGTH = 64
        private val localAtom = Regex("[A-Za-z0-9!#\u0024%&'*+/=?^_`{|}~-]+")
        private val domainLabel = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

        context(raise: Raise<Error>)
        override fun from(value: String): Email {
            val at = value.indexOf('@')
            raise.ensure(value.length <= MAX_LENGTH && at in 1..MAX_LOCAL_PART_LENGTH && at == value.lastIndexOf('@')) {
                Error.InvalidFormat(value)
            }
            val local = value.substring(0, at)
            val domain = value.substring(at + 1)
            raise.ensure(
                local.split('.').all(localAtom::matches) &&
                    domain.split('.').all(domainLabel::matches),
            ) { Error.InvalidFormat(value) }
            return Email(value)
        }
    }
}
