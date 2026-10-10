package io.liquidsoftware.safedomain.numeric

import arrow.core.Either
import arrow.core.raise.either
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class DecimalTypesTest {
    @Test
    fun `positive decimals reject zero and every negative amount`() {
        for (raw in listOf("0", "0.000", "-0.01", "-1E100")) {
            val input = BigDecimal(raw)
            val result: Either<PositiveDecimal.Error, PositiveDecimal> = either { PositiveDecimal.from(input) }
            assertEquals(Either.Left(PositiveDecimal.Error.NonPositive(input)), result)
        }
    }

    @Test
    fun `nonnegative decimals accept zero and reject negative amounts`() {
        for (raw in listOf("-0.01", "-1E100")) {
            val input = BigDecimal(raw)
            val result: Either<NonNegativeDecimal.Error, NonNegativeDecimal> = either { NonNegativeDecimal.from(input) }
            assertEquals(Either.Left(NonNegativeDecimal.Error.Negative(input)), result)
        }
        val zero = assertIs<Either.Right<NonNegativeDecimal>>(either { NonNegativeDecimal.from(BigDecimal("0.000")) }).value
        assertEquals(BigDecimal("0.000"), zero.value)
    }

    @Test
    fun `accepted decimals preserve precision and support the full BigDecimal scale range`() {
        for (input in listOf(BigDecimal("0.00100"), BigDecimal("1E100"),
            BigDecimal(BigInteger.ONE, Int.MAX_VALUE), BigDecimal(BigInteger.ONE, Int.MIN_VALUE))) {
            assertEquals(input, assertIs<Either.Right<PositiveDecimal>>(either { PositiveDecimal.from(input) }).value.value)
            assertEquals(input, assertIs<Either.Right<NonNegativeDecimal>>(either { NonNegativeDecimal.from(input) }).value.value)
        }
    }

    @Test
    fun `decimal equality hashing and ordering use numeric values`() {
        val positive = either {
            val first = PositiveDecimal.from(BigDecimal("12.30"))
            val second = PositiveDecimal.from(BigDecimal("12.3000"))
            assertEquals(first, second)
            assertEquals(first.hashCode(), second.hashCode())
            assertEquals("value", mapOf(first to "value")[second])
            assertEquals(0, first.compareTo(second))
            assertEquals(listOf(first, PositiveDecimal.from(BigDecimal("13"))),
                listOf(PositiveDecimal.from(BigDecimal("13")), first).sorted())
        }
        assertEquals(Either.Right(Unit), positive)
        val nonnegative = either {
            val first = NonNegativeDecimal.from(BigDecimal("12.30"))
            val second = NonNegativeDecimal.from(BigDecimal("12.3000"))
            assertEquals(first, second)
            assertEquals(first.hashCode(), second.hashCode())
            assertEquals(1, setOf(first, second).size)
            assertEquals(0, first.compareTo(second))
            assertEquals(-1, NonNegativeDecimal.from(BigDecimal.ZERO).compareTo(first))
            assertEquals(NonNegativeDecimal.from(BigDecimal("0.00")).hashCode(),
                NonNegativeDecimal.from(BigDecimal.ZERO).hashCode())
        }
        assertEquals(Either.Right(Unit), nonnegative)
    }

    @Test
    fun `hashing never normalizes decimal scales beyond BigDecimal limits`() {
        val first = BigDecimal(BigInteger.TEN, Int.MIN_VALUE)
        val second = BigDecimal(BigInteger.valueOf(100), Int.MIN_VALUE + 1)
        val positive = either {
            val a = PositiveDecimal.from(first)
            val b = PositiveDecimal.from(second)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }
        assertEquals(Either.Right(Unit), positive)
        val nonnegative = either {
            val a = NonNegativeDecimal.from(first)
            val b = NonNegativeDecimal.from(second)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }
        assertEquals(Either.Right(Unit), nonnegative)
    }

    @Test
    fun `percentages include zero and one hundred and reject values outside those bounds`() {
        for (raw in listOf("-0.001", "100.001", "1E100")) {
            val input = BigDecimal(raw)
            val result: Either<Percentage.Error, Percentage> = either { Percentage.from(input) }
            assertEquals(Either.Left(Percentage.Error.OutOfRange(input)), result)
        }
        for (raw in listOf("0.000", "0.001", "12.500", "100.000")) {
            val input = BigDecimal(raw)
            assertEquals(input, assertIs<Either.Right<Percentage>>(either { Percentage.from(input) }).value.value)
        }
    }

    @Test
    fun `percentages use whole percentage units and numeric value semantics`() {
        val result = either {
            val first = Percentage.from(BigDecimal("12.50"))
            val second = Percentage.from(BigDecimal("12.5000"))
            assertEquals(first, second)
            assertEquals(first.hashCode(), second.hashCode())
            assertEquals(1, setOf(first, second).size)
            assertEquals(0, first.compareTo(second))
            assertEquals(-1, first.compareTo(Percentage.from(BigDecimal("100"))))
            assertNotEquals(first as Any, PositiveDecimal.from(BigDecimal("12.50")) as Any)
            assertEquals(BigDecimal("12.50"), first.value)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `decimal errors map into a consuming domain without enumerating their branches`() {
        data class InvalidQuantity(val cause: PositiveDecimal.Error)
        val result: Either<InvalidQuantity, PositiveDecimal> = either {
            PositiveDecimal.from(BigDecimal.ZERO, onError = ::InvalidQuantity)
        }
        assertEquals(Either.Left(InvalidQuantity(PositiveDecimal.Error.NonPositive(BigDecimal.ZERO))), result)
    }
}
