package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.text.Secret
import io.liquidsoftware.safedomain.text.Slug

/**
 * Non-blank text of up to [maxLength] code points, mixing scripts, surrogate pairs, and
 * leading or trailing whitespace, which the type preserves.
 */
public fun NonBlankString.Companion.arb(maxLength: Int = 60): Arb<NonBlankString> {
    val factory = NonBlankString.bounded(PositiveInt.fromTrusted(maxLength))
    return Arb.untrustedText(1..maxLength).filter { it.isNotBlank() }.map { factory.fromTrusted(it) }
}

/** Slugs of one to five hyphen-joined groups of lowercase letters and digits. */
public fun Slug.Companion.arb(): Arb<Slug> =
    Arb.list(textOf(LOWER + DIGITS, 1..10), 1..5).map { Slug.fromTrusted(it.joinToString("-")) }

/** Non-blank secrets of up to [maxLength] characters. */
public fun Secret.Companion.arb(maxLength: Int = 60): Arb<Secret> =
    Arb.untrustedText(1..maxLength).filter { it.isNotBlank() }.map { Secret.fromTrusted(it) }
