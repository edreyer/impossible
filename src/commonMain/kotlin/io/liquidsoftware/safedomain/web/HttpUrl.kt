package io.liquidsoftware.safedomain.web

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An absolute HTTP/HTTPS URL with a server host and an optional port in 0..65535.
 * Parsed in common code following RFC 3986 and the JDK's `java.net.URI` rules, so
 * validation is identical on every platform. Allows user information, query, and
 * fragment; [value] preserves the input exactly, without trimming, encoding, or path
 * normalization. Equality ignores scheme and host case and percent-escape hex case,
 * while path, query, and fragment case are significant. An omitted default port is
 * not equal to an explicit one. No DNS or network access is performed. This does not
 * establish reachability, trust, or suitability for a particular outbound request policy.
 */
class HttpUrl private constructor(
    val value: String,
    val scheme: String,
    val userInfo: String?,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
    val fragment: String?,
) {
    sealed interface Error {
        data class InvalidSyntax(val value: String) : Error
        data object NotAbsolute : Error
        data class UnsupportedScheme(val scheme: String) : Error
        data object MissingHost : Error
        data class InvalidPort(val port: Int) : Error
    }

    private val comparisonKey: String by lazy {
        buildString {
            append(scheme.lowercase()).append("://")
            userInfo?.let { append(normalizeEscapes(it)).append('@') }
            append(host.lowercase())
            port?.let { append(':').append(it) }
            append(normalizeEscapes(path))
            query?.let { append('?').append(normalizeEscapes(it)) }
            fragment?.let { append('#').append(normalizeEscapes(it)) }
        }
    }

    override fun equals(other: Any?): Boolean = other is HttpUrl && comparisonKey == other.comparisonKey

    override fun hashCode(): Int = comparisonKey.hashCode()

    override fun toString(): String = value

    companion object : ValueFactory<String, Error, HttpUrl> {
        context(raise: Raise<Error>)
        override fun from(value: String): HttpUrl {
            val syntaxError = Error.InvalidSyntax(value)

            // Reference structure: [scheme ":"] ["//" authority] path ["?" query] ["#" fragment]
            val fragmentStart = value.indexOf('#')
            val beforeFragment = if (fragmentStart < 0) value else value.substring(0, fragmentStart)
            val fragment = if (fragmentStart < 0) null else value.substring(fragmentStart + 1)
            val queryStart = beforeFragment.indexOf('?')
            val beforeQuery = if (queryStart < 0) beforeFragment else beforeFragment.substring(0, queryStart)
            val query = if (queryStart < 0) null else beforeFragment.substring(queryStart + 1)

            val schemeEnd = beforeQuery.indexOf(':').takeIf { colon ->
                colon >= 0 && beforeQuery.substring(0, colon).none { it == '/' }
            }
            val scheme = schemeEnd?.let { beforeQuery.substring(0, it) }
            raise.ensure(scheme == null || schemePattern.matches(scheme)) { syntaxError }
            val hierarchical = if (schemeEnd == null) beforeQuery else beforeQuery.substring(schemeEnd + 1)

            val hasAuthority = hierarchical.startsWith("//")
            val authority = if (hasAuthority) hierarchical.substring(2).substringBefore('/') else null
            val path = if (hasAuthority) hierarchical.substring(2 + authority!!.length) else hierarchical

            raise.ensure(fragment == null || fragment.isValid(queryChars)) { syntaxError }
            raise.ensure(query == null || query.isValid(queryChars)) { syntaxError }
            raise.ensure(path.isValid(pathChars)) { syntaxError }
            raise.ensure(authority == null || authority.isValid(authorityChars)) { syntaxError }
            // As in the JDK: a scheme needs a scheme-specific part, and "//" cannot end the input.
            raise.ensure(scheme == null || beforeFragment.length > scheme.length + 1) { syntaxError }
            raise.ensure(authority != "" || path.isNotEmpty() || query != null || fragment != null) { syntaxError }

            raise.ensure(scheme != null) { Error.NotAbsolute }
            raise.ensure(scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
                Error.UnsupportedScheme(scheme)
            }
            raise.ensure(!authority.isNullOrEmpty()) { Error.MissingHost }

            val at = authority.lastIndexOf('@')
            val userInfo = if (at < 0) null else authority.substring(0, at)
            raise.ensure(userInfo == null || userInfo.isValid(userInfoChars)) { syntaxError }
            val hostAndPort = authority.substring(at + 1)

            val portSeparator = if (hostAndPort.startsWith("[")) {
                val close = hostAndPort.indexOf(']')
                raise.ensure(close > 0) { syntaxError }
                raise.ensure(close == hostAndPort.lastIndex || hostAndPort[close + 1] == ':') { syntaxError }
                if (close == hostAndPort.lastIndex) -1 else close + 1
            } else {
                hostAndPort.indexOf(':')
            }
            val host = if (portSeparator < 0) hostAndPort else hostAndPort.substring(0, portSeparator)
            val portText = if (portSeparator < 0) "" else hostAndPort.substring(portSeparator + 1)
            raise.ensure(isValidHost(host)) { syntaxError }

            // An empty port is absent, as in the JDK; digits that overflow Int are a syntax error.
            val port = if (portText.isEmpty()) {
                null
            } else {
                raise.ensure(portText.all { it in '0'..'9' }) { syntaxError }
                raise.ensureNotNull(portText.toIntOrNull()) { syntaxError }
            }
            raise.ensure(port == null || port in 0..65535) { Error.InvalidPort(port!!) }

            return HttpUrl(value, scheme, userInfo, host, port, path, query, fragment)
        }

        private val schemePattern = Regex("[A-Za-z][A-Za-z0-9+.-]*")
        private val dottedQuadPattern = Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")
        private val labelPattern = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")
        private val hexGroupPattern = Regex("[0-9A-Fa-f]{1,4}")

        // Character sets follow java.net.URI: unreserved and punctuation marks, escapes,
        // and (outside the authority) non-ASCII characters that are neither controls nor spaces.
        private const val unreserved = "-_.!~*'()"
        private const val userInfoChars = "$unreserved;:&=+$,"
        private const val pathChars = "$unreserved:@&=+$,;/"
        private const val queryChars = "$pathChars?[]"
        private const val authorityChars = "$unreserved;:&=+$,@[]"

        private fun String.isValid(allowed: String): Boolean {
            var index = 0
            while (index < length) {
                val char = this[index]
                when {
                    char == '%' -> {
                        if (index + 2 >= length || !this[index + 1].isHexDigit() || !this[index + 2].isHexDigit()) return false
                        index += 3
                        continue
                    }
                    char.isLetterOrDigit() && char.code < 128 -> Unit
                    char in allowed -> Unit
                    char.code >= 128 && allowed != authorityChars && !char.isISOControl() && !char.isWhitespace() -> Unit
                    else -> return false
                }
                index++
            }
            return true
        }

        private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        private fun isValidHost(host: String): Boolean = when {
            host.startsWith("[") -> host.endsWith("]") && isValidIpv6(host.substring(1, host.length - 1))
            dottedQuadPattern.matches(host) -> isValidIpv4(host)
            else -> isValidHostName(host)
        }

        /** Four decimal octets of at most three digits each, up to 255; leading zeros are accepted, as in the JDK. */
        private fun isValidIpv4(address: String): Boolean =
            dottedQuadPattern.matches(address) &&
                address.split('.').all { it.length <= 3 && it.toInt() <= 255 }

        /**
         * RFC 2396 host names as the JDK accepts them: alphanumeric labels with inner hyphens and
         * an optional trailing dot. When there is more than one label, the last starts with a letter.
         */
        private fun isValidHostName(host: String): Boolean {
            val labels = host.removeSuffix(".").split('.')
            if (!labels.all(labelPattern::matches)) return false
            return labels.size == 1 || labels.last().first().isLetter()
        }

        /** RFC 4291 text form: eight hex groups, at most one `::`, optionally ending in an IPv4 address. */
        private fun isValidIpv6(address: String): Boolean {
            val halves = address.split("::")
            if (halves.size > 2) return false
            val groups = halves.map { half -> if (half.isEmpty()) emptyList() else half.split(':') }
            val all = groups.flatten()
            val endsWithIpv4 = all.lastOrNull()?.contains('.') == true
            if (endsWithIpv4 && !isValidIpv4(all.last())) return false
            val hex = if (endsWithIpv4) all.dropLast(1) else all
            if (!hex.all(hexGroupPattern::matches)) return false
            val units = hex.size + if (endsWithIpv4) 2 else 0
            return if (halves.size == 2) units <= 7 else units == 8
        }

        private fun normalizeEscapes(text: String): String {
            if ('%' !in text) return text
            val result = StringBuilder(text.length)
            var index = 0
            while (index < text.length) {
                if (text[index] == '%' && index + 2 < text.length) {
                    result.append('%').append(text[index + 1].uppercaseChar()).append(text[index + 2].uppercaseChar())
                    index += 3
                } else {
                    result.append(text[index++])
                }
            }
            return result.toString()
        }
    }
}
