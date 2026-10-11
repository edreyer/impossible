package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.NegativeMoney
import io.liquidsoftware.safedomain.money.NonNegativeMoney
import io.liquidsoftware.safedomain.money.PositiveMoney

private val currencies: List<Currency> by lazy {
    uppercaseCodes(3) { accepts { Currency.from(it) } }.map { Currency.fromTrusted(it) }
}

private val monetaryCurrencies: List<Currency> by lazy { currencies.filter { it.minorUnits != null } }

/**
 * Every currency code the library accepts, chosen uniformly. With [monetaryOnly], only
 * currencies that have a minor unit and so can be [Money]; XAU and XXX are left out.
 */
public fun Currency.Companion.arb(monetaryOnly: Boolean = false): Arb<Currency> =
    Arb.element(if (monetaryOnly) monetaryCurrencies else currencies)

/**
 * The default bound on generated amounts, in minor units: about a billion major units.
 * Sums of a few such amounts never overflow, so business-rule tests are not dominated by
 * [Money.ArithmeticError.Overflow]. Pass a wider range to test overflow handling.
 */
public const val DEFAULT_MAX_MINOR: Long = 100_000_000_000L

/**
 * Amounts whose minor-unit count is in [minor], in a currency from [currency]. Zero and
 * both edges of [minor] occur as edge cases. Use `Arb.constant(usd)` for one currency.
 */
public fun Money.Companion.arb(
    currency: Arb<Currency> = Currency.arb(monetaryOnly = true),
    minor: LongRange = -DEFAULT_MAX_MINOR..DEFAULT_MAX_MINOR,
): Arb<Money> {
    val minorUnits = Arb.long(minor)
    return arbitrary {
        val amount = minorUnits.bind()
        val code = currency.bind()
        valid { Money.ofMinor(amount, code) }
    }
}

/** Amounts of at least one minor unit and at most [maxMinor]. */
public fun PositiveMoney.Companion.arb(
    currency: Arb<Currency> = Currency.arb(monetaryOnly = true),
    maxMinor: Long = DEFAULT_MAX_MINOR,
): Arb<PositiveMoney> = Money.arb(currency, 1..maxMinor).map { PositiveMoney.fromTrusted(it) }

/** Amounts from zero to [maxMinor] minor units. */
public fun NonNegativeMoney.Companion.arb(
    currency: Arb<Currency> = Currency.arb(monetaryOnly = true),
    maxMinor: Long = DEFAULT_MAX_MINOR,
): Arb<NonNegativeMoney> = Money.arb(currency, 0..maxMinor).map { NonNegativeMoney.fromTrusted(it) }

/** Amounts from -[maxMinor] to one minor unit below zero. */
public fun NegativeMoney.Companion.arb(
    currency: Arb<Currency> = Currency.arb(monetaryOnly = true),
    maxMinor: Long = DEFAULT_MAX_MINOR,
): Arb<NegativeMoney> = Money.arb(currency, -maxMinor..-1).map { NegativeMoney.fromTrusted(it) }
