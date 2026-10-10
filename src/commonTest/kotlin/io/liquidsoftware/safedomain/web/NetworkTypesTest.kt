package io.liquidsoftware.safedomain.web

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NetworkTypesTest {
    private fun domain(value: String) = either<DomainName.Error, DomainName> { DomainName.from(value) }

    private fun ip(value: String) = either<IpAddress.Error, IpAddress> { IpAddress.from(value) }

    @Test
    fun `domain names follow RFC 1123 labels`() {
        for (value in listOf("example.com", "localhost", "a-b.c0", "xn--bcher-kva.example", "1.example", "${"a".repeat(63)}.com")) {
            assertTrue(domain(value).isRight(), value)
        }
        for (value in listOf("", ".", "example.com.", "a..b", "-a.com", "a-.com", "exa mple.com", "bücher.de", "1.2.3.4", "${"a".repeat(64)}.com", List(127) { "a" }.joinToString(".") + ".bc")) {
            assertEquals(Either.Left(DomainName.Error.InvalidFormat(value)), domain(value), value)
        }
    }

    @Test
    fun `domain names compare without case and keep their spelling`() {
        val mixed = DomainName.fromTrusted("API.Example.com")
        assertEquals(DomainName.fromTrusted("api.example.COM"), mixed)
        assertEquals(DomainName.fromTrusted("api.example.COM").hashCode(), mixed.hashCode())
        assertEquals("API.Example.com", mixed.value)
        assertEquals(listOf("API", "Example", "com"), mixed.labels)
    }

    @Test
    fun `IPv4 addresses are strict dotted decimal`() {
        assertEquals("192.0.2.1", ip("192.0.2.1").getOrNull().toString())
        assertIs<IpAddress.V4>(ip("0.0.0.0").getOrNull())
        assertEquals("255.255.255.255", IpAddress.V4.fromTrusted("255.255.255.255").toString())
        for (value in listOf("", "1.2.3", "1.2.3.4.5", "256.0.0.1", "01.2.3.4", "1.2.3.-4", "1.2.3.4 ", "a.b.c.d", "1..3.4")) {
            assertEquals(Either.Left(IpAddress.Error.InvalidFormat(value)), ip(value), value)
        }
    }

    @Test
    fun `IPv6 addresses parse every text form and print RFC 5952`() {
        val cases = mapOf(
            "::1" to "::1",
            "::" to "::",
            "0:0:0:0:0:0:0:1" to "::1",
            "2001:DB8:0:0:0:0:2:1" to "2001:db8::2:1",
            "2001:db8:0:1:1:1:1:1" to "2001:db8:0:1:1:1:1:1",
            "2001:0:0:1:0:0:0:1" to "2001:0:0:1::1",
            "2001:db8:0:0:1:0:0:1" to "2001:db8::1:0:0:1",
            "::ffff:192.0.2.1" to "::ffff:c000:201",
            "fe80::" to "fe80::",
        )
        for ((input, canonical) in cases) {
            val address = ip(input).getOrNull()
            assertIs<IpAddress.V6>(address, input)
            assertEquals(canonical, address.toString(), input)
        }
        assertEquals(ip("::1"), ip("0000:0::0001"))
        for (value in listOf(":", ":::", "1::2::3", "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7::8", "12345::", "g::", "fe80::1%eth0", "[::1]", "::1.2.3", "1.2.3.4::", "::01.2.3.4")) {
            assertEquals(Either.Left(IpAddress.Error.InvalidFormat(value)), ip(value), value)
        }
    }

    @Test
    fun `addresses compare by bytes and redact rejected input`() {
        assertNotEquals<IpAddress?>(ip("0.0.0.0").getOrNull(), ip("::").getOrNull())
        assertEquals(listOf<Byte>(10, 0, 0, 1), IpAddress.V4.fromTrusted("10.0.0.1").toByteArray().toList())
        assertEquals(16, IpAddress.V6.fromTrusted("::1").toByteArray().size)
        assertEquals("InvalidFormat(value=<redacted>)", IpAddress.Error.InvalidFormat("10.0.0.999").toString())
    }
}
