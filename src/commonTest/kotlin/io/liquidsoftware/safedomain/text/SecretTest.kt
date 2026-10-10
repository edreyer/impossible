package io.liquidsoftware.safedomain.text

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class SecretTest {
    @Test
    fun `secrets reject blank input and never print their content`() {
        for (value in listOf("", "   ", "\t")) {
            assertEquals(Either.Left(Secret.Error.Blank), either<Secret.Error, Secret> { Secret.from(value) })
        }
        val secret = Secret.fromTrusted(" hunter2 ")
        assertEquals(" hunter2 ", secret.reveal())
        assertEquals("Secret(<redacted>)", secret.toString())
        assertFalse("hunter2" in listOf(secret).toString())
    }

    @Test
    fun `equality compares the whole content`() {
        assertEquals(Secret.fromTrusted("abc"), Secret.fromTrusted("abc"))
        assertEquals(Secret.fromTrusted("abc").hashCode(), Secret.fromTrusted("abc").hashCode())
        assertNotEquals(Secret.fromTrusted("abc"), Secret.fromTrusted("abd"))
        assertNotEquals(Secret.fromTrusted("abc"), Secret.fromTrusted("abcd"))
        assertNotEquals(Secret.fromTrusted("abc"), Secret.fromTrusted("ab"))
    }
}
