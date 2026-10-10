package io.liquidsoftware.safedomain.web

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class HttpUrlTest {
    @Test
    fun `accepts absolute HTTP and HTTPS addresses including IP hosts and explicit ports`() {
        for (raw in listOf("https://example.com", "HTTP://Example.com:8080/a%20b?q=1#part", "http://localhost",
            "https://127.0.0.1:0/", "http://[::1]:65535/", "https://xn--bcher-kva.example/é",
            "https://user:pass@example.com/path", "https://example.com./")) {
            val url = assertIs<Either.Right<HttpUrl>>(either { HttpUrl.from(raw) }).value
            assertEquals(raw, url.value)
        }
    }

    @Test
    fun `rejects relative addresses and other schemes with precise errors`() {
        for (raw in listOf("", "relative/path", "//example.com/path")) {
            val result: Either<HttpUrl.Error, HttpUrl> = either { HttpUrl.from(raw) }
            assertEquals(Either.Left(HttpUrl.Error.NotAbsolute), result)
        }
        for ((raw, scheme) in listOf("ftp://example.com" to "ftp", "mailto:a@example.com" to "mailto", "file:///tmp/a" to "file")) {
            val result: Either<HttpUrl.Error, HttpUrl> = either { HttpUrl.from(raw) }
            assertEquals(Either.Left(HttpUrl.Error.UnsupportedScheme(scheme)), result)
        }
    }

    @Test
    fun `HTTP scheme alone does not establish a server host`() {
        for (raw in listOf("https:opaque", "https:/path", "https:///path")) {
            val result: Either<HttpUrl.Error, HttpUrl> = either { HttpUrl.from(raw) }
            assertEquals(Either.Left(HttpUrl.Error.MissingHost), result)
        }
    }

    @Test
    fun `rejects malformed URI syntax and malformed server authority without repairing input`() {
        for (raw in listOf(" https://example.com", "https://example.com ", "https://example.com/\n", "https://example.com/%zz",
            "https://bad_host.example", "https://-bad.example", "https://example.com:abc", "https://example.com:-1",
            "https://bücher.example", "http://[invalid]/")) {
            val result: Either<HttpUrl.Error, HttpUrl> = either { HttpUrl.from(raw) }
            assertEquals(Either.Left(HttpUrl.Error.InvalidSyntax(raw)), result, raw)
        }
    }

    @Test
    fun `rejects numeric ports outside the unsigned sixteen bit range`() {
        for (port in listOf(65536, 99999)) {
            val result: Either<HttpUrl.Error, HttpUrl> = either { HttpUrl.from("https://example.com:$port/") }
            assertEquals(Either.Left(HttpUrl.Error.InvalidPort(port)), result)
        }
    }

    @Test
    fun `URI value semantics fold scheme host and escape hex case while preserving path case`() {
        val result = either {
            val first = HttpUrl.from("HTTP://Example.com/a%2fb")
            val same = HttpUrl.from("http://example.com/a%2Fb")
            assertEquals(first, same)
            assertEquals(first.hashCode(), same.hashCode())
            assertEquals("url", mapOf(first to "url")[same])
            assertNotEquals(first, HttpUrl.from("http://example.com/A%2Fb"))
            assertNotEquals(HttpUrl.from("http://example.com"), HttpUrl.from("http://example.com:80"))
            assertNotEquals(HttpUrl.from("http://example.com/a/../b"), HttpUrl.from("http://example.com/b"))
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `URL failures map into a consuming domain without replacing their cause`() {
        data class InvalidEndpoint(val cause: HttpUrl.Error)
        val result: Either<InvalidEndpoint, HttpUrl> = either {
            HttpUrl.from("relative", onError = ::InvalidEndpoint)
        }
        assertEquals(Either.Left(InvalidEndpoint(HttpUrl.Error.NotAbsolute)), result)
    }

    @Test
    fun `exposes parsed components without changing their spelling`() {
        val url = assertIs<Either.Right<HttpUrl>>(either {
            HttpUrl.from("HTTPS://user:p%41ss@Example.com:8443/a/B?q=[1]&r#Frag")
        }).value
        assertEquals("HTTPS", url.scheme)
        assertEquals("user:p%41ss", url.userInfo)
        assertEquals("Example.com", url.host)
        assertEquals(8443, url.port)
        assertEquals("/a/B", url.path)
        assertEquals("q=[1]&r", url.query)
        assertEquals("Frag", url.fragment)
    }

    @Test
    fun `an empty port is absent and IPv6 forms follow RFC 4291`() {
        val result = either {
            assertEquals(null, HttpUrl.from("http://example.com:/").port)
            assertEquals(null, HttpUrl.from("http://example.com").port)
            for (host in listOf("[2001:db8::1]", "[::]", "[::ffff:192.0.2.1]", "[1:2:3:4:5:6:7:8]")) {
                assertEquals(host, HttpUrl.from("http://$host/").host)
            }
        }
        assertEquals(Either.Right(Unit), result)
        for (raw in listOf("http://[1::2::3]/", "http://[1:2:3:4:5:6:7:8:9]/", "http://[12345::]/", "http://[::1]x/",
            "https://a.example/b[1]", "https://example.com/#a#b", "https://:80/", "https://example.com:99999999999/")) {
            val failed: Either<HttpUrl.Error, HttpUrl> = either { HttpUrl.from(raw) }
            assertEquals(Either.Left(HttpUrl.Error.InvalidSyntax(raw)), failed, raw)
        }
    }
}
