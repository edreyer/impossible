package io.liquidsoftware.safedomain.contact

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.web.DomainName

/**
 * An ASCII dot-atom mailbox whose domain is a valid [DomainName] (including
 * single-label domains such as `localhost`, but not an all-digit last label such as
 * `user@123`). The local part has at most 64 characters, each domain label 63, and the
 * whole address 254. Quoted local parts, address literals, and Unicode are unsupported;
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

    /** The domain as a [DomainName], which every accepted address has. */
    public val domainName: DomainName get() = DomainName.fromTrusted(domain)

    private val comparisonKey: String get() = localPart + "@" + domain.lowercase()

    override fun equals(other: Any?): Boolean = other is Email && comparisonKey == other.comparisonKey

    override fun hashCode(): Int = comparisonKey.hashCode()

    override fun toString(): String = "Email(value=$value)"

    public companion object : ValueFactory<String, Error, Email> {
        private const val MAX_LENGTH = 254
        private const val MAX_LOCAL_PART_LENGTH = 64
        private val localAtom = Regex("[A-Za-z0-9!#\u0024%&'*+/=?^_`{|}~-]+")

        context(raise: Raise<Error>)
        override fun from(value: String): Email {
            val at = value.indexOf('@')
            raise.ensure(value.length <= MAX_LENGTH && at in 1..MAX_LOCAL_PART_LENGTH && at == value.lastIndexOf('@')) {
                Error.InvalidFormat(value)
            }
            val local = value.substring(0, at)
            val domain = value.substring(at + 1)
            raise.ensure(local.split('.').all(localAtom::matches)) { Error.InvalidFormat(value) }
            DomainName.from(domain) { Error.InvalidFormat(value) }
            return Email(value)
        }
    }
}
