package io.liquidsoftware.safedomain.money

import arrow.core.Either
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.Percentage
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SignedMoneyTest {
    private val usd = Currency.fromTrusted("USD")
    private val eur = Currency.fromTrusted("EUR")

    private fun minor(value: Long, currency: Currency = usd): Money =
        either<Money.Error, Money> { Money.ofMinor(value, currency) }.getOrNull()!!

    private fun <A> calc(block: Raise<Money.ArithmeticError>.() -> A): Either<Money.ArithmeticError, A> =
        either { block() }

    @Test
    fun `each refinement accepts exactly its sign`() {
        val (negative, zero, positive) = Triple(minor(-1), minor(0), minor(1))
        assertEquals(Either.Left(PositiveMoney.Error.NonPositive(zero)), either<PositiveMoney.Error, PositiveMoney> { PositiveMoney.from(zero) })
        assertEquals(positive, PositiveMoney.fromTrusted(positive).money)
        assertEquals(Either.Left(NonNegativeMoney.Error.Negative(negative)), either<NonNegativeMoney.Error, NonNegativeMoney> { NonNegativeMoney.from(negative) })
        assertEquals(zero, NonNegativeMoney.fromTrusted(zero).money)
        assertEquals(Either.Left(NegativeMoney.Error.NonNegative(zero)), either<NegativeMoney.Error, NegativeMoney> { NegativeMoney.from(zero) })
        assertEquals(negative, NegativeMoney.fromTrusted(negative).money)
    }

    @Test
    fun `refinements convert between each other without revalidating`() {
        val price = PositiveMoney.fromTrusted(minor(500))
        assertEquals(NonNegativeMoney.fromTrusted(minor(500)), price.toNonNegativeMoney())
        assertEquals(NegativeMoney.fromTrusted(minor(-500)), price.negate())
        assertEquals(Either.Right(price), calc { price.negate().negate() })
        assertEquals(Either.Left(Money.ArithmeticError.Overflow), calc { NegativeMoney.fromTrusted(minor(Long.MIN_VALUE)).negate() })
    }

    @Test
    fun `arithmetic keeps the sign and still checks currency and overflow`() {
        val one = PositiveMoney.fromTrusted(minor(100))
        assertEquals(Either.Right(PositiveMoney.fromTrusted(minor(300))), calc { one.add(one).add(one) })
        assertEquals(Either.Right(PositiveMoney.fromTrusted(minor(300))), calc { one.multiply(PositiveInt.fromTrusted(3)) })
        assertEquals(Either.Left(Money.ArithmeticError.Overflow), calc { PositiveMoney.fromTrusted(minor(Long.MAX_VALUE)).add(one) })
        assertEquals(
            Either.Left(Money.ArithmeticError.CurrencyMismatch(usd, eur)),
            calc { one.add(PositiveMoney.fromTrusted(minor(100, eur))) },
        )
        val refund = NegativeMoney.fromTrusted(minor(-100))
        assertEquals(Either.Right(NegativeMoney.fromTrusted(minor(-200))), calc { refund.add(refund) })
        assertEquals(Either.Left(Money.ArithmeticError.Overflow), calc { NegativeMoney.fromTrusted(minor(Long.MIN_VALUE)).add(refund) })
        assertEquals(Either.Right(-1), calc { refund.compare(NegativeMoney.fromTrusted(minor(-50))) })
    }

    @Test
    fun `non-negative amounts start at zero take shares and allocate`() {
        val zero = either<Money.Error, NonNegativeMoney> { NonNegativeMoney.zero(usd) }.getOrNull()!!
        val total = calc { zero.add(NonNegativeMoney.fromTrusted(minor(1000))) }.getOrNull()!!
        assertEquals(NonNegativeMoney.fromTrusted(minor(1000)), total)
        assertEquals(
            Either.Right(NonNegativeMoney.fromTrusted(minor(83))),
            calc { total.multiply(Percentage.fromTrusted(Decimal.parse("8.25")), RoundingMode.HALF_UP) },
        )
        assertEquals(listOf(334L, 333L, 333L), total.split(PositiveInt.fromTrusted(3)).map { it.money.minor })
        assertEquals(listOf(1L, 0L), NonNegativeMoney.fromTrusted(minor(1)).allocate(nonEmptyListOf(PositiveInt.ONE, PositiveInt.ONE)).map { it.money.minor })
    }
}
