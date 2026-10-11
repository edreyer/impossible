package io.liquidsoftware.safedomain.web

import arrow.core.Either
import arrow.core.raise.either
import java.net.URI
import java.net.URISyntaxException
import kotlin.test.Test
import kotlin.test.assertEquals

/** The common parser must classify inputs exactly as the former java.net.URI-based implementation did. */
class HttpUrlJdkParityTest {
    private fun jdk(value: String): String {
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return "InvalidSyntax"
        }
        if (!uri.isAbsolute) return "NotAbsolute"
        if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) return "UnsupportedScheme(${uri.scheme})"
        val server = try {
            uri.parseServerAuthority()
        } catch (_: URISyntaxException) {
            return "InvalidSyntax"
        }
        if (server.isOpaque || server.host == null) return "MissingHost"
        if (server.port != -1 && server.port !in 1..65535) return "InvalidPort(${server.port})"
        return "Ok(${server.host},${server.port.takeIf {
            it != -1
        }},${server.rawPath},${server.rawQuery},${server.rawFragment},${server.rawUserInfo})"
    }

    private fun common(value: String): String = when (val result = either { HttpUrl.from(value) }) {
        is Either.Right -> with(result.value) { "Ok($host,$port,$path,$query,$fragment,$userInfo)" }
        is Either.Left -> when (val error = result.value) {
            is HttpUrl.Error.InvalidSyntax -> "InvalidSyntax"
            is HttpUrl.Error.NotAbsolute -> "NotAbsolute"
            is HttpUrl.Error.UnsupportedScheme -> "UnsupportedScheme(${error.scheme})"
            is HttpUrl.Error.MissingHost -> "MissingHost"
            is HttpUrl.Error.InvalidPort -> "InvalidPort(${error.port})"
        }
    }

    private val corpus = listOf(
        "https://example.com", "http://example.com/", "HTTP://Example.COM", "https://example.com:443/a?b=c#d",
        "https://a.b.c.example/x/y/z", "https://example.com/a%20b", "https://example.com/%zz", "https://example.com/%2",
        "https://example.com/a b", "https://example.com/a\"b", "https://example.com/a<b", "https://example.com/a{b}",
        "https://example.com/a|b", "https://example.com/a\\b", "https://example.com/a^b", "https://example.com/a`b",
        "https://example.com/é", "https://example.com/?é", "https://example.com/#é", "https://ex%41mple.com/",
        "https://example.com/?a=[1]", "https://example.com/#[x]", "https://example.com/a[1]", "https://example.com/##",
        "https://user@example.com", "https://user:pw@example.com:8080", "https://us er@example.com", "https://@example.com",
        "https://a@b@example.com", "https://example.com:", "https://example.com:0", "https://example.com:65535",
        "https://example.com:65536", "https://example.com:2147483647", "https://example.com:2147483648", "https://example.com:+1",
        "https://127.0.0.1", "https://256.0.0.1", "https://1.2.3", "https://1.2.3.4.5", "https://01.2.3.4", "https://123",
        "https://[::1]", "https://[::1]:80", "https://[2001:db8::1]/", "https://[::ffff:1.2.3.4]", "https://[1:2:3:4:5:6:7:8]",
        "https://[1:2:3:4:5:6:7:8:9]", "https://[1::2::3]", "https://[g::1]", "https://[::1", "https://::1]",
        "https://example", "https://example.", "https://example..com", "https://.example.com", "https://ex-ample.com",
        "https://example-.com", "https://1example.com", "https://example.1com", "https://example.c0m", "https://xn--bcher-kva.example",
        "https://bücher.example", "https://exa_mple.com", "https://", "https:///", "https:///path", "https:path", "https:",
        "http:/path", "ftp://example.com", "mailto:x@example.com", "urn:isbn:123", "1http://example.com", "+http://x",
        "", "/path", "//example.com", "?q", "#f", "a:b", " https://example.com", "https://example.com ", "https://exa mple.com",
        "https://example.com/\t", "https:?q", "https:#f", "//", "https://?q", "https://#f", "https://1234.1.1.1",
        "https://123.example", "https://a.123", "https://a.b-", "https://a-b.c-d.example", "https://[::1.2.3.4]",
        "https://[1:2::300.1.1.1]", "https://example.com/a;b=c", "https://example.com/a,b\$c&d+e=f!g*h'i(j)k~l",
    )

    @Test
    fun `classifies every corpus input like java net URI`() {
        val mismatches = corpus.mapNotNull { raw ->
            (raw to (jdk(raw) to common(raw))).takeIf { it.second.first != it.second.second }
        }
        assertEquals(
            emptyList(),
            mismatches.map { (raw, results) ->
                "'$raw' jdk=${results.first} common=${results.second}"
            },
        )
    }
}
