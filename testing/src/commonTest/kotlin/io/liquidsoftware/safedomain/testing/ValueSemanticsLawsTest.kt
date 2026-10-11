package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveDecimal
import io.liquidsoftware.safedomain.numeric.RoundingMode
import io.liquidsoftware.safedomain.text.Secret
import io.liquidsoftware.safedomain.web.DomainName
import io.liquidsoftware.safedomain.web.HttpUrl
import io.liquidsoftware.safedomain.web.IpAddress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Equality, hashing, and ordering agree with each documented normalization. */
class ValueSemanticsLawsTest {
    private fun <A> assertSameValue(expected: A, actual: A) {
        assertEquals(expected, actual)
        assertEquals(expected.hashCode(), actual.hashCode())
    }

    @Test
    fun `email domains compare without case and local parts with case`() = runTest {
        forAll(Email.arb()) { email ->
            assertSameValue(email, Email.fromTrusted(email.localPart + "@" + email.domain.uppercase()))
            val shouted = email.localPart.uppercase()
            if (shouted != email.localPart) {
                assertNotEquals(email, Email.fromTrusted(shouted + "@" + email.domain))
            }
        }
    }

    @Test
    fun `domain names and URL schemes and hosts compare without case`() = runTest {
        forAll(DomainName.arb()) { name ->
            assertSameValue(name, DomainName.fromTrusted(name.value.uppercase()))
            assertSameValue(name, DomainName.fromTrusted(name.value.lowercase()))
        }
        forAll(HttpUrl.arb()) { url ->
            val user = url.userInfo?.let { "$it@" } ?: ""
            val afterHost = url.value.substring(url.scheme.length + 3 + user.length + url.host.length)
            val shouted = url.scheme.uppercase() + "://" + user + url.host.uppercase() + afterHost
            assertSameValue(url, HttpUrl.fromTrusted(shouted))
        }
    }

    @Test
    fun `IP addresses compare by bytes whatever their text form`() = runTest {
        forAll(IpAddress.V6.arb()) { address ->
            val bytes = address.toByteArray()
            val expanded = (0 until 8).joinToString(":") { group ->
                val value = ((bytes[2 * group].toInt() and 0xFF) shl 8) or (bytes[2 * group + 1].toInt() and 0xFF)
                value.toString(16).padStart(4, '0').uppercase()
            }
            val reparsed = IpAddress.V6.fromTrusted(expanded)
            assertSameValue(address, reparsed)
            assertEquals(address.toString(), reparsed.toString())
        }
    }

    @Test
    fun `numeric types compare by value whatever their scale`() = runTest {
        forAll(PositiveDecimal.arb()) { number ->
            val rescaled = PositiveDecimal.fromTrusted(number.value.setScale(number.value.scale + 3, RoundingMode.UNNECESSARY))
            assertSameValue(number, rescaled)
            assertEquals(0, number.compareTo(rescaled))
        }
        forAll(Percentage.arb()) { percentage ->
            val rescaled = Percentage.fromTrusted(percentage.value.setScale(percentage.value.scale + 2, RoundingMode.UNNECESSARY))
            assertSameValue(percentage, rescaled)
        }
    }

    @Test
    fun `decimal equality includes scale while ordering does not`() = runTest {
        forAll(Decimal.arb()) { decimal ->
            val rescaled = decimal.setScale(decimal.scale + 1, RoundingMode.UNNECESSARY)
            assertNotEquals(decimal, rescaled)
            assertEquals(0, decimal.compareTo(rescaled))
        }
    }

    @Test
    fun `ordering is antisymmetric and agrees with equality`() = runTest {
        val pairs = arbitrary {
            PositiveDecimal.arb(
                maxDigits = 3,
                scales = 0..2,
            ).bind() to PositiveDecimal.arb(maxDigits = 3, scales = 0..2).bind()
        }
        forAll(pairs) { (left, right) ->
            assertEquals(-left.compareTo(right).coerceIn(-1, 1), right.compareTo(left).coerceIn(-1, 1))
            assertEquals(left.compareTo(right) == 0, left == right)
        }
    }

    @Test
    fun `rejected personal data never reaches error text`() = runTest {
        forAll(Arb.untrustedText(3..40)) { text ->
            attempt { Email.from(text) }.onLeft { assertEquals("InvalidFormat(value=<redacted>)", it.toString()) }
            attempt { Phone.from(text) }.onLeft { assertEquals("InvalidFormat(value=<redacted>)", it.toString()) }
            attempt { IpAddress.from(text) }.onLeft { assertEquals("InvalidFormat(value=<redacted>)", it.toString()) }
        }
        forAll(Secret.arb()) { assertEquals("Secret(<redacted>)", it.toString()) }
    }
}
