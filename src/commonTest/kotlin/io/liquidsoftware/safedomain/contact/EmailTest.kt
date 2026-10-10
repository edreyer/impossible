package io.liquidsoftware.safedomain.contact

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmailTest {
    @Test
    fun `accepts ASCII dot atom addresses and preserves their exact spelling`() {
        for (raw in listOf("Alice.Example+tag@EXAMPLE.com", "o'reilly@example.com", "user@localhost", "user@xn--bcher-kva.example")) {
            assertEquals(raw, assertIs<Either.Right<Email>>(either<Email.Error, Email> { Email.from(raw) }).value.value)
        }
    }

    @Test
    fun `rejects malformed or unsupported addresses without trimming`() {
        for (raw in listOf("", "user", "@example.com", "user@", "a@@b.com", "a..b@example.com", ".a@example.com", "a.@example.com", "a b@example.com", "a@-example.com", "a@example-.com", "a@example..com", "a@example.com.", " a@example.com", "a@example.com\n", "\"a b\"@example.com", "ü@example.com", "a@bücher.example", "a@[127.0.0.1]")) {
            assertEquals(Either.Left(Email.Error.InvalidFormat(raw)), either<Email.Error, Email> { Email.from(raw) }, raw)
        }
    }

    @Test
    fun `enforces local part and DNS label length boundaries`() {
        val valid = "a".repeat(64) + "@" + "b".repeat(63) + ".example"
        assertIs<Either.Right<Email>>(either<Email.Error, Email> { Email.from(valid) })
        for (raw in listOf("a".repeat(65) + "@example.com", "a@" + "b".repeat(64) + ".com")) {
            assertEquals(Either.Left(Email.Error.InvalidFormat(raw)), either<Email.Error, Email> { Email.from(raw) })
        }
    }

    @Test
    fun `enforces total ASCII mailbox length of 254`() {
        val raw = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61)
        assertEquals(254, raw.length)
        assertIs<Either.Right<Email>>(either<Email.Error, Email> { Email.from(raw) })
        assertEquals(Either.Left(Email.Error.InvalidFormat(raw + "d")), either<Email.Error, Email> { Email.from(raw + "d") })
    }

    @Test
    fun `email equality uses the exact preserved text`() {
        val result = either {
            val email = Email.from("User@example.com")
            assertEquals(2, setOf(email, Email.from("User@example.com"), Email.from("user@example.com")).size)
            assertEquals("contact", mapOf(email to "contact")[Email.from("User@example.com")])
        }
        assertEquals(Either.Right(Unit), result)
    }
}
