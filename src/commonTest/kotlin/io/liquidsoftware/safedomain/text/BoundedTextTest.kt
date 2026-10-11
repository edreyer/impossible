package io.liquidsoftware.safedomain.text

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.numeric.PositiveInt
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundedTextTest {
    private val five = PositiveInt.fromTrusted(5)

    @Test
    fun `bounded text limits length in code points and preserves input by default`() {
        val name = NonBlankString.bounded(five)
        assertEquals(" abc ", name.fromTrusted(" abc ").value)
        assertEquals(
            Either.Left(NonBlankString.Error.TooLong(5, 6)),
            either<NonBlankString.Error, NonBlankString> {
                name.from("abcdef")
            },
        )
        assertEquals(
            Either.Left(NonBlankString.Error.Blank),
            either<NonBlankString.Error, NonBlankString> {
                name.from("   ")
            },
        )
        // Five emoji are ten UTF-16 units but five code points.
        assertEquals(10, "😀😀😀😀😀".length)
        assertEquals("😀😀😀😀😀", name.fromTrusted("😀😀😀😀😀").value)
    }

    @Test
    fun `trimming is explicit and applies before both checks`() {
        val name = NonBlankString.bounded(five, trim = true)
        assertEquals("abc", name.fromTrusted("  abc  ").value)
        assertEquals("abcde", name.fromTrusted(" abcde ").value)
        assertEquals(
            Either.Left(NonBlankString.Error.Blank),
            either<NonBlankString.Error, NonBlankString> {
                name.from("  ")
            },
        )
    }

    @Test
    fun `slugs are lowercase hyphenated groups and are never repaired`() {
        for (raw in listOf("a", "summer-sale-2026", "x1-y2")) {
            assertEquals(raw, Slug.fromTrusted(raw).value)
        }
        for (raw in listOf("", "-a", "a-", "a--b", "Summer", "a_b", "a b", "é")) {
            assertEquals(Either.Left(Slug.Error.InvalidFormat(raw)), either<Slug.Error, Slug> { Slug.from(raw) })
        }
    }
}
