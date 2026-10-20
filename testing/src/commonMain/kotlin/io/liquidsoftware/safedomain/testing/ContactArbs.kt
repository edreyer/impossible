package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.web.DomainName

private const val LOCAL_ATOM_CHARS = ALPHANUMERIC + "!#$%&'*+/=?^_`{|}~-"

/**
 * Dot-atom addresses with one to three local atoms (including punctuation such as `+`
 * and `'`) and a domain from [DomainName.arb], within the type's length limits.
 */
public fun Email.Companion.arb(): Arb<Email> = arbitrary { rs ->
    val atoms = (1..rs.random.nextInt(1, 4)).map { textOf(LOCAL_ATOM_CHARS, 1..15).bind() }
    Email.fromTrusted(atoms.joinToString(".") + "@" + DomainName.arb().bind().value)
}

/** E.164-shaped numbers: `+`, a nonzero digit, and up to fourteen more digits. */
public fun Phone.Companion.arb(): Arb<Phone> = arbitrary { rs ->
    Phone.fromTrusted("+" + "123456789".random(rs.random) + textOf(DIGITS, 0..14).bind())
}
