package io.liquidsoftware.safedomain.testing

import arrow.core.Either
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.Arb
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.NegativeInt
import io.liquidsoftware.safedomain.numeric.NegativeLong
import io.liquidsoftware.safedomain.numeric.PositiveDecimal
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.PositiveLong
import io.liquidsoftware.safedomain.numeric.RoundingMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sign-refined arithmetic stays in its type or fails with a typed overflow, never wraps. */
class NumericLawsTest {
    @Test
    fun `adding positive ints is exact or overflows`() = runTest {
        forAll(arbitrary { PositiveInt.arb().bind() to PositiveInt.arb().bind() }) { (left, right) ->
            val exact = left.value.toLong() + right.value
            val sum = attempt { left + right }
            if (exact > Int.MAX_VALUE) {
                assertEquals(Either.Left(PositiveInt.Overflow), sum)
            } else {
                assertEquals(exact, sum.getOrNull()?.value?.toLong())
            }
        }
    }

    @Test
    fun `adding negative ints is exact or overflows`() = runTest {
        forAll(arbitrary { NegativeInt.arb().bind() to NegativeInt.arb().bind() }) { (left, right) ->
            val exact = left.value.toLong() + right.value
            val sum = attempt { left + right }
            if (exact < Int.MIN_VALUE) {
                assertEquals(Either.Left(NegativeInt.Overflow), sum)
            } else {
                assertEquals(exact, sum.getOrNull()?.value?.toLong())
            }
        }
    }

    @Test
    fun `adding positive longs is exact or overflows`() = runTest {
        forAll(arbitrary { PositiveLong.arb().bind() to PositiveLong.arb().bind() }) { (left, right) ->
            val exact = Decimal.of(left.value) + Decimal.of(right.value)
            val sum = attempt { left + right }
            if (exact > Decimal.of(Long.MAX_VALUE)) {
                assertTrue(sum.isLeft())
            } else {
                assertEquals(0, Decimal.of(sum.getOrNull()!!.value).compareTo(exact))
            }
        }
    }

    @Test
    fun `negation round trips between sign pairs`() = runTest {
        forAll(PositiveInt.arb()) { positive -> assertEquals(Either.Right(positive), attempt<NegativeInt.Overflow, PositiveInt> { -(-positive) }) }
        forAll(NegativeInt.arb()) { negative ->
            val magnitude = attempt { -negative }
            if (negative.value == Int.MIN_VALUE) {
                assertEquals(Either.Left(NegativeInt.Overflow), magnitude)
            } else {
                assertEquals(negative, -magnitude.getOrNull()!!)
            }
        }
        forAll(NegativeLong.arb()) { negative ->
            assertEquals(negative.value != Long.MIN_VALUE, attempt { -negative }.isRight())
        }
        forAll(PositiveDecimal.arb()) { positive -> assertEquals(positive, -(-positive)) }
    }

    @Test
    fun `positive decimals add exactly`() = runTest {
        forAll(arbitrary { PositiveDecimal.arb().bind() to PositiveDecimal.arb().bind() }) { (left, right) ->
            assertEquals(0, (left + right).value.compareTo(left.value + right.value))
            assertTrue(left + right > left)
        }
    }

    @Test
    fun `decimal addition and multiplication are commutative and associative`() = runTest {
        val small = Decimal.arb(maxDigits = 12, scales = -3..6)
        forAll(arbitrary { Triple(small.bind(), small.bind(), small.bind()) }) { (a, b, c) ->
            assertEquals(a + b, b + a)
            assertEquals((a + b) + c, a + (b + c))
            assertEquals(a * b, b * a)
            assertEquals((a * b) * c, a * (b * c))
            assertEquals(0, (a + b - b).compareTo(a))
        }
    }

    @Test
    fun `rounding brackets the exact value`() = runTest {
        val cases = arbitrary { Decimal.arb().bind() to Arb.int(-3..8).bind() }
        forAll(cases) { (value, scale) ->
            val floor = value.setScale(scale, RoundingMode.FLOOR)
            val ceiling = value.setScale(scale, RoundingMode.CEILING)
            assertTrue(floor <= value && value <= ceiling, "$floor <= $value <= $ceiling")
            assertTrue((ceiling - floor).compareTo(Decimal.of(1, scale)) <= 0)
            for (mode in RoundingMode.entries - RoundingMode.UNNECESSARY) {
                val rounded = value.setScale(scale, mode)
                assertEquals(scale, rounded.scale)
                assertTrue(rounded.compareTo(floor) == 0 || rounded.compareTo(ceiling) == 0, "$mode rounded $value to $rounded")
            }
            val down = value.setScale(scale, RoundingMode.DOWN)
            assertTrue(down.abs() <= value.abs())
            assertTrue(value.setScale(scale, RoundingMode.UP).abs() >= value.abs())
        }
    }

    @Test
    fun `half modes pick the nearer neighbour`() = runTest {
        val cases = arbitrary { Decimal.arb(maxDigits = 10, scales = 0..6).bind() to Arb.element(0, 1, 2, 3).bind() }
        forAll(cases) { (value, scale) ->
            val floor = value.setScale(scale, RoundingMode.FLOOR)
            val ceiling = value.setScale(scale, RoundingMode.CEILING)
            val towardFloor = (value - floor).compareTo(ceiling - value)
            for (mode in listOf(RoundingMode.HALF_UP, RoundingMode.HALF_DOWN, RoundingMode.HALF_EVEN)) {
                val rounded = value.setScale(scale, mode)
                when {
                    towardFloor < 0 -> assertEquals(0, rounded.compareTo(floor), "$mode $value")
                    towardFloor > 0 -> assertEquals(0, rounded.compareTo(ceiling), "$mode $value")
                }
            }
        }
    }
}
