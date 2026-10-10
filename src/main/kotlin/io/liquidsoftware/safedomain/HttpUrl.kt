package io.liquidsoftware.safedomain

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import java.net.URI
import java.net.URISyntaxException

/**
 * An absolute HTTP/HTTPS URI with a JDK-parsed server host and an optional port
 * in 0..65535. Allows user information, query, and fragment; preserves spelling
 * without trimming, encoding, or path normalization. Equality follows URI:
 * scheme/host and escape hex case are ignored, while path case is significant.
 * No DNS or network access is performed. This does not establish reachability,
 * trust, or suitability for a particular outbound request policy.
 */
@JvmInline
value class HttpUrl private constructor(val value: URI) {
    sealed interface Error {
        data class InvalidSyntax(val value: String) : Error
        data object NotAbsolute : Error
        data class UnsupportedScheme(val scheme: String) : Error
        data object MissingHost : Error
        data class InvalidPort(val port: Int) : Error
    }

    companion object : ValueFactory<String, Error, HttpUrl> {
        context(raise: Raise<Error>)
        override fun from(value: String): HttpUrl {
            val uri = try {
                URI(value)
            } catch (_: URISyntaxException) {
                raise.raise(Error.InvalidSyntax(value))
            }
            raise.ensure(uri.isAbsolute) { Error.NotAbsolute }
            raise.ensure(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
                Error.UnsupportedScheme(uri.scheme)
            }
            val server = try {
                uri.parseServerAuthority()
            } catch (_: URISyntaxException) {
                raise.raise(Error.InvalidSyntax(value))
            }
            raise.ensure(!server.isOpaque && server.host != null) { Error.MissingHost }
            // The JDK uses -1 to represent an absent port.
            raise.ensure(server.port == -1 || server.port in 0..65535) { Error.InvalidPort(server.port) }
            return HttpUrl(server)
        }
    }
}
