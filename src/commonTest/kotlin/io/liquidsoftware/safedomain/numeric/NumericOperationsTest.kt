package io.liquidsoftware.safedomain.numeric

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NumericOperationsTest {
    @Test
    fun `positive integers add until they overflow and widen without validation`() {
        val two = PositiveInt.fromTrusted(2)
        assertEquals(Either.Right(PositiveInt.fromTrusted(3)), either<PositiveInt.Overflow, PositiveInt> { two + PositiveInt.ONE })
        assertEquals(Either.Left(PositiveInt.Overflow), either<PositiveInt.Overflow, PositiveInt> { PositiveInt.fromTrusted(Int.MAX_VALUE) + PositiveInt.ONE })
        assertEquals(2L, two.toPositiveLong().value)
        assertEquals(PositiveDecimal.fromTrusted(Decimal.of(2)), two.toPositiveDecimal())
        assertTrue(PositiveInt.ONE < two)
    }

    @Test
    fun `positive longs reject zero and overflow`() {
        assertEquals(Either.Left(PositiveLong.Error.NonPositive(0)), either<PositiveLong.Error, PositiveLong> { PositiveLong.from(0) })
        assertEquals(Either.Left(PositiveLong.Overflow), either<PositiveLong.Overflow, PositiveLong> { PositiveLong.fromTrusted(Long.MAX_VALUE) + PositiveLong.ONE })
        assertEquals(Long.MAX_VALUE, PositiveLong.fromTrusted(Long.MAX_VALUE).value)
    }

    @Test
    fun `decimal sums keep their sign invariant`() {
        val half = PositiveDecimal.fromTrusted(Decimal.parse("0.5"))
        assertEquals(PositiveDecimal.fromTrusted(Decimal.parse("1.0")), half + half)
        assertEquals(NonNegativeDecimal.fromTrusted(Decimal.parse("0.5")), half.toNonNegativeDecimal())
        assertEquals(NonNegativeDecimal.fromTrusted(Decimal.parse("0.5")), NonNegativeDecimal.ZERO + half.toNonNegativeDecimal())
    }

    @Test
    fun `percentage exposes its exact ratio`() {
        assertEquals(Decimal.parse("0.125"), Percentage.fromTrusted(Decimal.parse("12.5")).ratio)
        assertEquals(Decimal.parse("1.00"), Percentage.fromTrusted(Decimal.of(100)).ratio)
    }

    @Test
    fun `bounded integers are inclusive and include their bounds in equality`() {
        val rating = BoundedInt.factory(1..5)
        for (value in listOf(0, 6, Int.MIN_VALUE)) {
            assertEquals(Either.Left(BoundedInt.Error.OutOfRange(value, 1..5)), either<BoundedInt.Error, BoundedInt> { rating.from(value) })
        }
        assertEquals(listOf(1, 5), listOf(1, 5).map { rating.fromTrusted(it).value })
        assertEquals(rating.fromTrusted(3), BoundedInt.factory(1..5).fromTrusted(3))
        assertNotEquals(rating.fromTrusted(3), BoundedInt.factory(0..10).fromTrusted(3))
        assertFailsWith<IllegalArgumentException> { BoundedInt.factory(5..1) }
    }
}
