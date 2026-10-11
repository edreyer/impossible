package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.numeric.BoundedInt
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.NegativeDecimal
import io.liquidsoftware.safedomain.numeric.NegativeInt
import io.liquidsoftware.safedomain.numeric.NegativeLong
import io.liquidsoftware.safedomain.numeric.NonNegativeDecimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveDecimal
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.PositiveLong

/** Positive integers in [range], including its edges. */
fun PositiveInt.Companion.arb(range: IntRange = 1..Int.MAX_VALUE): Arb<PositiveInt> {
    require(range.first >= 1 && !range.isEmpty()) { "Range must be a non-empty subrange of 1..Int.MAX_VALUE: $range" }
    return Arb.int(range).map { PositiveInt.fromTrusted(it) }
}

/** Negative integers in [range], including its edges. */
fun NegativeInt.Companion.arb(range: IntRange = Int.MIN_VALUE..-1): Arb<NegativeInt> {
    require(range.last <= -1 && !range.isEmpty()) { "Range must be a non-empty subrange of Int.MIN_VALUE..-1: $range" }
    return Arb.int(range).map { NegativeInt.fromTrusted(it) }
}

/** Positive longs in [range], including its edges. */
fun PositiveLong.Companion.arb(range: LongRange = 1..Long.MAX_VALUE): Arb<PositiveLong> {
    require(range.first >= 1 && !range.isEmpty()) { "Range must be a non-empty subrange of 1..Long.MAX_VALUE: $range" }
    return Arb.long(range).map { PositiveLong.fromTrusted(it) }
}

/** Negative longs in [range], including its edges. */
fun NegativeLong.Companion.arb(range: LongRange = Long.MIN_VALUE..-1): Arb<NegativeLong> {
    require(range.last <= -1 && !range.isEmpty()) { "Range must be a non-empty subrange of Long.MIN_VALUE..-1: $range" }
    return Arb.long(range).map { NegativeLong.fromTrusted(it) }
}

/** Values of the factory's [bounds], including both edges. */
fun BoundedInt.Companion.arb(bounds: IntRange): Arb<BoundedInt> {
    val factory = BoundedInt.factory(bounds)
    return Arb.int(bounds).map { factory.fromTrusted(it) }
}

/**
 * Decimals with up to [maxDigits] significant digits and a scale in [scales], so values
 * such as `-0.000120`, `7E+3` and `123456789.01` all occur. Trailing zeros are kept:
 * `1.0` and `1.00` are distinct decimals, as in `java.math.BigDecimal`.
 */
fun Decimal.Companion.arb(maxDigits: Int = 30, scales: IntRange = -5..12): Arb<Decimal> {
    require(maxDigits >= 1 && !scales.isEmpty()) { "Need at least one digit and a non-empty scale range" }
    return arbitrary(listOf(Decimal.ZERO, Decimal.ONE, Decimal.of(-1))) { rs ->
        val digitCount = rs.random.nextInt(1, maxDigits + 1)
        val digits = (1..digitCount).map { DIGITS.random(rs.random) }.joinToString("")
        val sign = if (rs.random.nextBoolean()) "-" else ""
        val scale = rs.random.nextInt(scales.first, scales.last + 1)
        Decimal.parse("$sign${digits}E${-scale.toLong()}")
    }
}

/** Decimals strictly greater than zero; see [Decimal.Companion.arb]. */
fun PositiveDecimal.Companion.arb(maxDigits: Int = 30, scales: IntRange = -5..12): Arb<PositiveDecimal> =
    nonZeroMagnitudes(maxDigits, scales).map { PositiveDecimal.fromTrusted(it) }

/** Decimals strictly less than zero; see [Decimal.Companion.arb]. */
fun NegativeDecimal.Companion.arb(maxDigits: Int = 30, scales: IntRange = -5..12): Arb<NegativeDecimal> =
    nonZeroMagnitudes(maxDigits, scales).map { NegativeDecimal.fromTrusted(-it) }

/** Decimals greater than or equal to zero, including zeros of several scales. */
fun NonNegativeDecimal.Companion.arb(maxDigits: Int = 30, scales: IntRange = -5..12): Arb<NonNegativeDecimal> =
    Decimal.arb(maxDigits, scales).map { NonNegativeDecimal.fromTrusted(it.abs()) }

/** Percentages in 0..100 with up to [maxScale] decimal places, including 0 and 100. */
fun Percentage.Companion.arb(maxScale: Int = 4): Arb<Percentage> {
    require(maxScale in 0..15) { "maxScale must be in 0..15: $maxScale" }
    val edges = listOf(Decimal.ZERO, Decimal.of(100), Decimal.of(10_000, 2)).map { Percentage.fromTrusted(it) }
    return arbitrary(edges) { rs ->
        val scale = rs.random.nextInt(0, maxScale + 1)
        val maximum = 100 * tenTo(scale)
        Percentage.fromTrusted(Decimal.of(rs.random.nextLong(0, maximum + 1), scale))
    }
}

private fun nonZeroMagnitudes(maxDigits: Int, scales: IntRange): Arb<Decimal> =
    Decimal.arb(maxDigits, scales).map { if (it.sign == 0) Decimal.of(1, it.scale) else it.abs() }

private fun tenTo(power: Int): Long {
    var result = 1L
    repeat(power) { result *= 10 }
    return result
}
