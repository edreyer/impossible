package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.map
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.locale.CountryCode
import io.liquidsoftware.safedomain.locale.LanguageTag

private val countryCodes: List<String> by lazy { uppercaseCodes(2) { accepts { CountryCode.from(it) } } }

/** Every country code the library accepts, chosen uniformly. */
fun CountryCode.Companion.arb(): Arb<CountryCode> = Arb.element(countryCodes).map { CountryCode.fromTrusted(it) }

/**
 * Tags in the supported BCP 47 subset: a language, then an optional script, region
 * (letters or a UN M.49 number), and up to two variants.
 */
fun LanguageTag.Companion.arb(): Arb<LanguageTag> = arbitrary { rs ->
    val random = rs.random
    buildString {
        append(textOf(LOWER, 2..3).bind())
        if (random.nextBoolean()) append('-').append(UPPER.random(random)).append(textOf(LOWER, 3..3).bind())
        when (random.nextInt(3)) {
            0 -> append('-').append(textOf(UPPER, 2..2).bind())
            1 -> append('-').append(textOf(DIGITS, 3..3).bind())
        }
        repeat(random.nextInt(3)) {
            append('-')
            if (random.nextBoolean()) {
                append(textOf(LOWER + DIGITS, 5..8).bind())
            } else {
                append(DIGITS.random(random)).append(textOf(LOWER + DIGITS, 3..3).bind())
            }
        }
    }.let { LanguageTag.fromTrusted(it) }
}
