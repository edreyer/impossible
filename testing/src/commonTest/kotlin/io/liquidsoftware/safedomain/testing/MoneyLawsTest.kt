package io.liquidsoftware.safedomain.testing

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.NegativeMoney
import io.liquidsoftware.safedomain.money.NonNegativeMoney
import io.liquidsoftware.safedomain.money.PositiveMoney
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.RoundingMode
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Money arithmetic is exact, closed within a currency, and never loses a minor unit. */
class MoneyLawsTest {
    /** [count] amounts that share one randomly chosen currency. */
    private fun sameCurrency(count: Int, minor: LongRange = -DEFAULT_MAX_MINOR..DEFAULT_MAX_MINOR): Arb<List<Money>> =
        arbitrary {
            val currency = Arb.constant(Currency.arb(monetaryOnly = true).bind())
            List(count) { Money.arb(currency, minor).bind() }
        }

    private val ratios: Arb<NonEmptyList<PositiveInt>> =
        Arb.list(PositiveInt.arb(1..1_000), 1..10).let { lists ->
            arbitrary { lists.bind().toNonEmptyListOrNull()!! }
        }

    @Test
    fun `addition is commutative and associative with zero as identity`() = runTest {
        forAll(sameCurrency(3)) { (a, b, c) ->
            val zero = attempt { Money.zero(a.currency) }.getOrNull()!!
            assertEquals(attempt { a.add(b) }, attempt { b.add(a) })
            assertEquals(attempt { a.add(b).add(c) }, attempt { a.add(b.add(c)) })
            assertEquals(Either.Right(a), attempt<Money.ArithmeticError, Money> { a.add(zero) })
            assertEquals(Either.Right(a), attempt<Money.ArithmeticError, Money> { a.add(b).subtract(b) })
            assertEquals(Either.Right(zero), attempt<Money.ArithmeticError, Money> { a.add(a.negate()) })
        }
    }

    @Test
    fun `addition is exact or reports overflow`() = runTest {
        forAll(sameCurrency(2, Long.MIN_VALUE..Long.MAX_VALUE)) { (a, b) ->
            val exact = Decimal.of(a.minor) + Decimal.of(b.minor)
            val fits = exact >= Decimal.of(Long.MIN_VALUE) && exact <= Decimal.of(Long.MAX_VALUE)
            val sum = attempt { a.add(b) }
            if (fits) {
                assertEquals(0, Decimal.of(sum.getOrNull()!!.minor).compareTo(exact))
            } else {
                assertEquals(Either.Left(Money.ArithmeticError.Overflow), sum)
            }
        }
    }

    @Test
    fun `different currencies never combine`() = runTest {
        val pairs = arbitrary { Money.arb().bind() to Money.arb().bind() }.filter { (a, b) -> a.currency != b.currency }
        forAll(pairs) { (a, b) ->
            val mismatch = Either.Left(Money.ArithmeticError.CurrencyMismatch(a.currency, b.currency))
            assertEquals(mismatch, attempt<Money.ArithmeticError, Money> { a.add(b) })
            assertEquals(mismatch, attempt<Money.ArithmeticError, Money> { a.subtract(b) })
            assertEquals(mismatch, attempt<Money.ArithmeticError, Int> { a.compare(b) })
        }
    }

    @Test
    fun `allocation sums to the whole and each part is within one minor unit of its share`() = runTest {
        forAll(arbitrary { Money.arb().bind() to ratios.bind() }) { (money, ratios) ->
            val parts = money.allocate(ratios)
            assertEquals(ratios.size, parts.size)
            assertEquals(money.minor, parts.sumOf { it.minor })
            assertTrue(parts.all { it.currency == money.currency })
            val ratioSum = ratios.sumOf { it.value.toLong() }
            parts.zip(ratios).forEach { (part, ratio) ->
                // |part × ratioSum − minor × ratio| < ratioSum, so the part is its exact share ± 1.
                val error = part.minor * ratioSum - money.minor * ratio.value
                assertTrue(abs(error) < ratioSum, "part ${part.minor} of ${money.minor} for $ratio/$ratioSum")
            }
        }
    }

    @Test
    fun `splitting gives parts that differ by at most one minor unit`() = runTest {
        forAll(arbitrary { Money.arb().bind() to PositiveInt.arb(1..50).bind() }) { (money, count) ->
            val parts = money.split(count)
            assertEquals(count.value, parts.size)
            assertEquals(money.minor, parts.sumOf { it.minor })
            assertTrue(parts.maxOf { it.minor } - parts.minOf { it.minor } <= 1)
        }
    }

    @Test
    fun `multiplying by a quantity equals repeated addition`() = runTest {
        forAll(arbitrary { Money.arb().bind() to PositiveInt.arb(1..20).bind() }) { (money, quantity) ->
            val repeated = attempt { (2..quantity.value).fold(money) { total, _ -> total.add(money) } }
            assertEquals(repeated, attempt { money.multiply(quantity) })
        }
    }

    @Test
    fun `amounts finer than the minor unit are rejected not rounded`() = runTest {
        forAll(arbitrary { Money.arb().bind() to Arb.int(1..9).bind() }) { (money, digit) ->
            val scale = money.currency.minorUnits!!
            val finer = money.amount + Decimal.of(digit.toLong(), scale + 1)
            assertEquals(
                Either.Left(Money.Error.NotInMinorUnits(finer, scale)),
                attempt<Money.Error, Money> { Money.of(finer, money.currency) },
            )
        }
    }

    @Test
    fun `sign refinements are closed under their operations`() = runTest {
        val positives = arbitrary {
            val currency = Arb.constant(Currency.arb(monetaryOnly = true).bind())
            PositiveMoney.arb(currency).bind() to PositiveMoney.arb(currency).bind()
        }
        forAll(positives) { (a, b) ->
            assertTrue(attempt { a.add(b) }.getOrNull()!!.money.sign > 0)
            assertEquals(Either.Right(a), attempt<Money.ArithmeticError, PositiveMoney> { a.negate().negate() })
        }
        forAll(arbitrary { NonNegativeMoney.arb().bind() to Percentage.arb().bind() }) { (amount, share) ->
            for (mode in listOf(RoundingMode.HALF_EVEN, RoundingMode.UP, RoundingMode.DOWN)) {
                val portion = attempt { amount.multiply(share, mode) }.getOrNull()!!
                assertTrue(portion.money.sign >= 0 && portion.money.minor <= amount.money.minor)
            }
        }
        forAll(arbitrary { NonNegativeMoney.arb().bind() to PositiveInt.arb(1..20).bind() }) { (amount, count) ->
            assertTrue(amount.split(count).all { it.money.sign >= 0 })
        }
        forAll(NegativeMoney.arb()) { negative -> assertEquals(Either.Right(negative), attempt<Money.ArithmeticError, NegativeMoney> { negative.negate().negate() }) }
    }

    @Test
    fun `sum of a list equals pairwise addition`() = runTest {
        forAll(arbitrary { sameCurrency(Arb.int(1..8).bind()).bind() }) { amounts ->
            val list = amounts.toNonEmptyListOrNull()!!
            assertEquals(attempt { list.tail.fold(list.head) { total, next -> total.add(next) } }, attempt { Money.sum(list) })
        }
    }
}
