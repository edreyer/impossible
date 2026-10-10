package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PhoneTest {
    @Test
    fun `accepts international syntax and preserves the leading plus`() {
        for (raw in listOf("+14155552671", "+34612345678", "+123456789012345", "+1")) {
            assertEquals(raw, assertIs<Either.Right<Phone>>(either<Phone.Error, Phone> { Phone.from(raw) }).value.value)
        }
    }

    @Test
    fun `rejects national formatting extensions leading zero and non ASCII digits`() {
        for (raw in listOf("", "+", "14155552671", "+012345", "+1234567890123456", "+1 415 555 2671", "+1-415-555-2671", "+14155552671x123", " +14155552671", "+14155552671\n", "+١٢٣")) {
            assertEquals(Either.Left(Phone.Error.InvalidFormat(raw)), either<Phone.Error, Phone> { Phone.from(raw) }, raw)
        }
    }

    @Test
    fun `phone equality uses its full international value`() {
        val result = either {
            val phone = Phone.from("+14155552671")
            assertEquals(1, setOf(phone, Phone.from("+14155552671")).size)
            assertEquals("contact", mapOf(phone to "contact")[Phone.from("+14155552671")])
        }
        assertEquals(Either.Right(Unit), result)
    }
}
