package io.liquidsoftware.safedomain.testing

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.asString

/**
 * Runs a construction that a generator built to be valid. Every generator goes through
 * the library's real factories, so a generator that produces invalid input fails loudly
 * here instead of yielding a value that bypassed validation.
 */
internal inline fun <Error, A> valid(block: Raise<Error>.() -> A): A =
    either(block).getOrElse { throw IllegalStateException("Generator produced invalid input: $it") }

internal const val LOWER = "abcdefghijklmnopqrstuvwxyz"
internal const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
internal const val DIGITS = "0123456789"
internal const val ALPHANUMERIC = LOWER + UPPER + DIGITS

/** [length] characters drawn from [alphabet]. */
internal fun textOf(alphabet: String, length: IntRange): Arb<String> = arbitrary { rs ->
    val size = rs.random.nextInt(length.first, length.last + 1)
    (1..size).map { alphabet.random(rs.random) }.joinToString("")
}

/**
 * A DNS label (RFC 1123): letters, digits, and inner hyphens, starting and ending with a
 * letter or digit. With [startWithLetter] the label is never all digits.
 */
internal fun dnsLabel(maxLength: Int, startWithLetter: Boolean = false): Arb<String> = arbitrary { rs ->
    val length = rs.random.nextInt(1, maxLength + 1)
    val first = (if (startWithLetter) LOWER + UPPER else ALPHANUMERIC).random(rs.random)
    if (length == 1) return@arbitrary first.toString()
    val middle = (1..length - 2).map { (ALPHANUMERIC + "-").random(rs.random) }.joinToString("")
    first + middle + ALPHANUMERIC.random(rs.random)
}

/** Every [length]-letter uppercase code that [accepts] allows. */
internal fun uppercaseCodes(length: Int, accepts: (String) -> Boolean): List<String> {
    var candidates = listOf("")
    repeat(length) { candidates = candidates.flatMap { prefix -> UPPER.map { prefix + it } } }
    return candidates.filter(accepts)
}

/** Whether [construct] succeeds for [value]. */
internal inline fun <Error> accepts(construct: Raise<Error>.() -> Unit): Boolean = either(construct).isRight()

/**
 * Arbitrary text for exercising parsers: mostly printable ASCII, mixed with whitespace
 * (including non-ASCII spaces), Cyrillic, Japanese kana, and characters outside the
 * Basic Multilingual Plane (surrogate pairs).
 */
fun Arb.Companion.untrustedText(length: IntRange = 0..60): Arb<String> = arbitrary { rs ->
    val size = rs.random.nextInt(length.first, length.last + 1)
    buildString {
        repeat(size) {
            val code = when (rs.random.nextInt(10)) {
                in 0..5 -> rs.random.nextInt(0x20, 0x7F)
                6 -> listOf(0x20, 0x09, 0x0A, 0xA0, 0x2003).random(rs.random)
                7 -> rs.random.nextInt(0x0400, 0x0500)
                8 -> rs.random.nextInt(0x3041, 0x30FF)
                else -> rs.random.nextInt(0x1F600, 0x1F650)
            }
            append(Codepoint(code).asString())
        }
    }
}

/**
 * Text one small edit away from a value of [valid]: a character inserted, deleted, or
 * replaced, or the case of one character flipped. Near misses probe validation
 * boundaries far better than random text, which almost never comes close to valid.
 */
fun Arb.Companion.nearMiss(valid: Arb<String>): Arb<String> = arbitrary { rs ->
    val text = valid.bind()
    val noise = "-._@:/+%#?= \t0aZéа"
    val index = if (text.isEmpty()) 0 else rs.random.nextInt(text.length)
    when (if (text.isEmpty()) 0 else rs.random.nextInt(4)) {
        0 -> text.substring(0, index) + noise.random(rs.random) + text.substring(index)
        1 -> text.removeRange(index, index + 1)
        2 -> text.replaceRange(index, index + 1, noise.random(rs.random).toString())
        else -> {
            val char = text[index]
            val flipped = if (char.isUpperCase()) char.lowercaseChar() else char.uppercaseChar()
            text.replaceRange(index, index + 1, flipped.toString())
        }
    }
}
