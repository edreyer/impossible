package io.liquidsoftware.safedomain.web

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory

/**
 * A DNS host name: dot-separated labels of ASCII letters, digits, and inner hyphens
 * (RFC 1123), each at most 63 characters, at most 253 in total. A single label such as
 * `localhost` is accepted. The last label must not be all digits, so an IPv4 address
 * is never a domain name (RFC 3696). A trailing root dot, empty labels, and Unicode are
 * rejected; already encoded `xn--` labels are accepted. [value] preserves the input;
 * equality and hashing ignore ASCII case, as DNS does. No DNS lookup is performed.
 */
class DomainName private constructor(val value: String) {
    sealed interface Error {
        data class InvalidFormat(val value: String) : Error
    }

    /** The labels from left to right, as written: `api.example.com` is `[api, example, com]`. */
    val labels: List<String> get() = value.split('.')

    private val comparisonKey: String get() = value.lowercase()

    override fun equals(other: Any?): Boolean = other is DomainName && comparisonKey == other.comparisonKey

    override fun hashCode(): Int = comparisonKey.hashCode()

    override fun toString(): String = "DomainName(value=$value)"

    companion object : ValueFactory<String, Error, DomainName> {
        private val label = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

        context(raise: Raise<Error>)
        override fun from(value: String): DomainName {
            val labels = value.split('.')
            raise.ensure(
                value.length <= 253 &&
                    labels.all(label::matches) &&
                    !labels.last().all { it in '0'..'9' },
            ) { Error.InvalidFormat(value) }
            return DomainName(value)
        }
    }
}
