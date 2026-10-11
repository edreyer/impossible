package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.fromTrusted

private const val LOCAL_ATOM_CHARS = ALPHANUMERIC + "!#$%&'*+/=?^_`{|}~-"

/**
 * Dot-atom addresses with one to three local atoms (including punctuation such as `+`
 * and `'`) and one to four domain labels in mixed case, within the type's length limits.
 */
fun Email.Companion.arb(): Arb<Email> = arbitrary { rs ->
    val atoms = (1..rs.random.nextInt(1, 4)).map { textOf(LOCAL_ATOM_CHARS, 1..15).bind() }
    val labels = (1..rs.random.nextInt(1, 5)).map { dnsLabel(maxLength = 20).bind() }
    Email.fromTrusted(atoms.joinToString(".") + "@" + labels.joinToString("."))
}

/** E.164-shaped numbers: `+`, a nonzero digit, and up to fourteen more digits. */
fun Phone.Companion.arb(): Arb<Phone> = arbitrary { rs ->
    Phone.fromTrusted("+" + "123456789".random(rs.random) + textOf(DIGITS, 0..14).bind())
}
