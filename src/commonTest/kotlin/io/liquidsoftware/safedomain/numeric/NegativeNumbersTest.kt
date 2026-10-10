package io.liquidsoftware.safedomain.numeric

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NegativeNumbersTest {
    @Test
    fun `negative integers reject zero and positives`() {
        for (value in listOf(0, 1, Int.MAX_VALUE)) {
            assertEquals(Either.Left(NegativeInt.Error.NonNegative(value)), either<NegativeInt.Error, NegativeInt> { NegativeInt.from(value) })
        }
        assertEquals(Int.MIN_VALUE, NegativeInt.fromTrusted(Int.MIN_VALUE).value)
        assertEquals(-1, NegativeInt.MINUS_ONE.value)
        assertTrue(NegativeInt.fromTrusted(-2) < NegativeInt.MINUS_ONE)
    }

    @Test
    fun `negative integers add and negate until they overflow`() {
        val minusTwo = NegativeInt.fromTrusted(-2)
        assertEquals(Either.Right(NegativeInt.fromTrusted(-3)), either<NegativeInt.Overflow, NegativeInt> { minusTwo + NegativeInt.MINUS_ONE })
        assertEquals(Either.Left(NegativeInt.Overflow), either<NegativeInt.Overflow, NegativeInt> { NegativeInt.fromTrusted(Int.MIN_VALUE) + NegativeInt.MINUS_ONE })
        assertEquals(Either.Right(PositiveInt.fromTrusted(2)), either<NegativeInt.Overflow, PositiveInt> { -minusTwo })
        assertEquals(Either.Left(NegativeInt.Overflow), either<NegativeInt.Overflow, PositiveInt> { -NegativeInt.fromTrusted(Int.MIN_VALUE) })
        assertEquals(NegativeInt.fromTrusted(-Int.MAX_VALUE), -PositiveInt.fromTrusted(Int.MAX_VALUE))
        assertEquals(-2L, minusTwo.toNegativeLong().value)
        assertEquals(NegativeDecimal.fromTrusted(Decimal.of(-2)), minusTwo.toNegativeDecimal())
    }

    @Test
    fun `negative longs mirror positive longs`() {
        assertEquals(Either.Left(NegativeLong.Error.NonNegative(0)), either<NegativeLong.Error, NegativeLong> { NegativeLong.from(0) })
        assertEquals(Either.Left(NegativeLong.Overflow), either<NegativeLong.Overflow, NegativeLong> { NegativeLong.fromTrusted(Long.MIN_VALUE) + NegativeLong.MINUS_ONE })
        assertEquals(Either.Left(NegativeLong.Overflow), either<NegativeLong.Overflow, PositiveLong> { -NegativeLong.fromTrusted(Long.MIN_VALUE) })
        assertEquals(NegativeLong.fromTrusted(-Long.MAX_VALUE), -PositiveLong.fromTrusted(Long.MAX_VALUE))
    }

    @Test
    fun `negative decimals reject zero and negate to positive`() {
        assertEquals(Either.Left(NegativeDecimal.Error.NonNegative(Decimal.parse("0.00"))), either<NegativeDecimal.Error, NegativeDecimal> { NegativeDecimal.from(Decimal.parse("0.00")) })
        val minusHalf = NegativeDecimal.fromTrusted(Decimal.parse("-0.5"))
        assertEquals(NegativeDecimal.fromTrusted(Decimal.parse("-1.00")), minusHalf + minusHalf)
        assertEquals(PositiveDecimal.fromTrusted(Decimal.parse("0.5")), -minusHalf)
        assertEquals(minusHalf, -PositiveDecimal.fromTrusted(Decimal.parse("0.50")))
    }
}
