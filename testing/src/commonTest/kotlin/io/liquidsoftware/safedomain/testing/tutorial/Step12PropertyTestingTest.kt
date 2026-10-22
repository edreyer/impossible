package io.liquidsoftware.safedomain.testing.tutorial

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.andThen
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.map
import io.liquidsoftware.safedomain.mapError
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.NonNegativeMoney
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.testing.arb
import io.liquidsoftware.safedomain.testing.errorArb
import io.liquidsoftware.safedomain.testing.nearMiss
import io.liquidsoftware.safedomain.testing.untrustedText
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Slug
import kotlinx.coroutines.test.runTest
import kotlin.jvm.JvmInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Code for docs/tutorial/12-property-testing.md. Keep the two in step: TutorialSnippetsTest
// checks that every snippet in the chapter appears here.

/** The shelf type from step 3: tests in this module cannot see the library module's tests. */
@JvmInline
value class Shelf private constructor(val slug: Slug) {
    sealed interface Error {
        data class InvalidText(val cause: NonBlankString.Error) : Error
        data class NotASlug(val cause: Slug.Error) : Error
    }

    companion object : ValueFactory<String, Error, Shelf> by (
        NonBlankString.bounded(PositiveInt.fromTrusted(24))
            .mapError(Error::InvalidText)
            .map { it.value }
            .andThen(Slug) { Error.NotASlug(it) }
            .map { Shelf(it) }
        )
}

// A fixed seed keeps every run reproducible. PropTestConfig's constructor exposes
// experimental parameters, hence the opt-in; none of them are used.
@OptIn(ExperimentalKotest::class)
val seeded: PropTestConfig = PropTestConfig(seed = 20_261_021L, iterations = 300)

data class BasketLine(val title: NonBlankString, val copies: PositiveInt, val unitPrice: NonNegativeMoney)

val usd: Currency = Currency.fromTrusted("USD")

val basketLines: Arb<BasketLine> = arbitrary {
    BasketLine(
        title = NonBlankString.arb(maxLength = 80).bind(),
        copies = PositiveInt.arb(1..20).bind(),
        unitPrice = NonNegativeMoney.arb(Arb.constant(usd)).bind(),
    )
}

context(raise: Raise<Money.ArithmeticError>)
fun BasketLine.total(): NonNegativeMoney = unitPrice.multiply(copies)

class Step12PropertyTestingTest {
    @Test
    fun `generated values compose into your own types`() = runTest {
        checkAll(seeded, basketLines) { line ->
            val total: Either<Money.ArithmeticError, NonNegativeMoney> = either { line.total() }
            assertEquals(line.unitPrice.money.minor * line.copies.value, total.getOrNull()?.money?.minor)
            assertEquals(usd, total.getOrNull()?.money?.currency)
        }
    }

    @Test
    fun `your parser returns a value or a typed error for any text`() = runTest {
        checkAll(seeded, Arb.untrustedText()) { text ->
            val shelf: Either<Shelf.Error, Shelf> = either { Shelf.from(text) }
            shelf.onRight { assertTrue(it.slug.value.length <= 24) }
        }
    }

    @Test
    fun `near misses probe the edge of what your parser accepts`() = runTest {
        checkAll(seeded, Arb.nearMiss(Slug.arb().map { it.value })) { text ->
            val shelf: Either<Shelf.Error, Shelf> = either { Shelf.from(text) }
            val slug: Either<Slug.Error, Slug> = either { Slug.from(text) }
            if (text.length <= 24) assertEquals(slug.isRight(), shelf.isRight())
        }
    }

    @Test
    fun `every error the library can raise has a message in your catalog`() = runTest {
        val catalog = setOf("email.invalid_format")
        checkAll(seeded, Email.errorArb()) { error ->
            assertTrue(ErrorMessages.describe(error).code in catalog)
        }
    }
}
