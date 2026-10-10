package io.liquidsoftware.safedomain.numeric

import java.math.BigDecimal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks Decimal against java.math.BigDecimal, which it mirrors on every platform. */
class DecimalJdkParityTest {
    private val samples = listOf(
        "0", "0.00", "-0", "1", "-1", "0.5", "-0.5", "1.005", "-1.005", "2.5", "-2.5", "3.5", "12.30", "1E+4",
        "-1E+4", "1E-7", "0.000001", "0.0000001", "123456789012345678901234567890.123456789", "9.990", "1e2",
        "-1E100", "1E-100", "0E+3", "0E-10", "+7.25", "100.000", ".5", "5.", "99999999999999999999",
    )

    private fun mode(mode: RoundingMode) = java.math.RoundingMode.valueOf(mode.name)

    private fun same(expected: () -> BigDecimal, actual: () -> Decimal, label: String) {
        val jdk = runCatching(expected)
        val ours = runCatching(actual)
        if (jdk.isFailure) {
            assertEquals(true, ours.exceptionOrNull() is ArithmeticException, "$label: expected ArithmeticException, got $ours")
        } else {
            assertEquals(jdk.getOrThrow().toString(), ours.getOrThrow().toString(), label)
            assertEquals(jdk.getOrThrow().toPlainString(), ours.getOrThrow().toPlainString(), "$label plain")
        }
    }

    @Test
    fun `parsing and formatting match BigDecimal`() {
        for (raw in samples) {
            val ours = Decimal.parse(raw)
            val jdk = BigDecimal(raw)
            assertEquals(jdk.toString(), ours.toString(), raw)
            assertEquals(jdk.toPlainString(), ours.toPlainString(), raw)
            assertEquals(jdk.scale(), ours.scale, raw)
            assertEquals(jdk.precision(), ours.precision, raw)
            assertEquals(jdk.signum(), ours.sign, raw)
        }
    }

    @Test
    fun `arithmetic comparison and rounding match BigDecimal`() {
        for (a in samples) for (b in samples) {
            val (x, y) = Decimal.parse(a) to Decimal.parse(b)
            val (p, q) = BigDecimal(a) to BigDecimal(b)
            assertEquals(p.compareTo(q), x.compareTo(y), "$a <=> $b")
            assertEquals(p == q, x == y, "$a == $b")
            same({ p.add(q) }, { x + y }, "$a + $b")
            same({ p.subtract(q) }, { x - y }, "$a - $b")
            same({ p.multiply(q) }, { x * y }, "$a * $b")
            for (m in RoundingMode.entries) for (scale in listOf(-2, 0, 2, 5)) {
                same({ p.divide(q, scale, mode(m)) }, { x.divide(y, scale, m) }, "$a / $b @$scale $m")
            }
        }
        for (a in samples) for (m in RoundingMode.entries) for (scale in -3..4) {
            same({ BigDecimal(a).setScale(scale, mode(m)) }, { Decimal.parse(a).setScale(scale, m) }, "$a setScale $scale $m")
        }
    }

    @Test
    fun `random values round like BigDecimal`() {
        val random = Random(42)
        repeat(2_000) {
            val unscaled = random.nextLong()
            val scale = random.nextInt(-20, 40)
            val divisor = random.nextLong(1, 1_000_000) * (if (random.nextBoolean()) 1 else -1)
            val p = BigDecimal(java.math.BigInteger.valueOf(unscaled), scale)
            val x = Decimal.of(unscaled, scale)
            val target = random.nextInt(-5, 30)
            val m = RoundingMode.entries.random(random)
            same({ p.setScale(target, mode(m)) }, { x.setScale(target, m) }, "$p setScale $target $m")
            same({ p.divide(BigDecimal.valueOf(divisor), target, mode(m)) }, { x.divide(Decimal.of(divisor), target, m) }, "$p / $divisor @$target $m")
        }
    }

    @Test
    fun `extreme scales match BigDecimal`() {
        val tiny = Decimal.of(1, Int.MAX_VALUE)
        assertEquals(BigDecimal(java.math.BigInteger.ONE, Int.MAX_VALUE).toString(), tiny.toString())
        assertEquals(BigDecimal(java.math.BigInteger.TEN, Int.MIN_VALUE).toString(), Decimal.of(10, Int.MIN_VALUE).toString())
        same({ BigDecimal("5").setScale(2, java.math.RoundingMode.UP) }, { Decimal.of(5).setScale(2, RoundingMode.UP) }, "upscale")
        same({ BigDecimal(java.math.BigInteger.ONE, 1_000_000).setScale(0, java.math.RoundingMode.UP) },
            { Decimal.of(1, 1_000_000).setScale(0, RoundingMode.UP) }, "tiny round up")
        same({ BigDecimal("7").divide(BigDecimal("1E+1000000"), 0, java.math.RoundingMode.CEILING) },
            { Decimal.of(7).divide(Decimal.parse("1E+1000000"), 0, RoundingMode.CEILING) }, "tiny quotient")
    }
}
