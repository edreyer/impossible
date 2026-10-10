package io.liquidsoftware.safedomain.numeric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DecimalTest {
    @Test
    fun `parsing keeps the written scale and rejects anything outside BigDecimal syntax`() {
        assertEquals("12.30", Decimal.parse("12.30").toString())
        assertEquals(2, Decimal.parse("-12.30").scale)
        assertEquals(-4, Decimal.parse("1E+4").scale)
        assertEquals("0.5", Decimal.parse(".5").toString())
        assertEquals("5", Decimal.parse("5.").toString())
        for (raw in listOf("", "-", ".", "NaN", "Infinity", " 1", "1 ", "1,0", "1e", "1e+", "1..0", "1e2147483649", "١")) {
            assertNull(Decimal.parseOrNull(raw), raw)
        }
        assertFailsWith<NumberFormatException> { Decimal.parse("oops") }
    }

    @Test
    fun `equality includes scale while comparison is numeric`() {
        assertNotEquals(Decimal.parse("1.0"), Decimal.parse("1.00"))
        assertEquals(0, Decimal.parse("1.0").compareTo(Decimal.parse("1.00")))
        assertEquals(Decimal.parse("1.0").numericHashCode(), Decimal.parse("1.00").numericHashCode())
        assertEquals(-1, Decimal.parse("-2").compareTo(Decimal.parse("-1.5")))
        assertEquals(1, Decimal.parse("1E+3").compareTo(Decimal.parse("999.999")))
    }

    @Test
    fun `arithmetic is exact and rounding is explicit`() {
        assertEquals(Decimal.parse("0.30"), Decimal.parse("0.10") + Decimal.parse("0.2"))
        assertEquals(Decimal.parse("0.0150"), Decimal.parse("0.10") * Decimal.parse("0.15"))
        assertEquals(Decimal.parse("0.33"), Decimal.ONE.divide(Decimal.of(3), 2, RoundingMode.DOWN))
        assertEquals(Decimal.parse("1.00"), Decimal.parse("1.005").setScale(2, RoundingMode.HALF_EVEN))
        assertFailsWith<ArithmeticException> { Decimal.ONE.divide(Decimal.ZERO, 2, RoundingMode.DOWN) }
        assertFailsWith<ArithmeticException> { Decimal.parse("1.005").setScale(2, RoundingMode.UNNECESSARY) }
        assertFailsWith<ArithmeticException> { Decimal.of(1, Int.MAX_VALUE) * Decimal.parse("0.1") }
    }
}
