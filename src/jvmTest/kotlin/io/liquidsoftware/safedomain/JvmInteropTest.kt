package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.raise.either
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.toJavaCurrencyOrNull
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.RoundingMode
import io.liquidsoftware.safedomain.numeric.toDecimal
import io.liquidsoftware.safedomain.numeric.toDecimalRoundingMode
import io.liquidsoftware.safedomain.numeric.toJavaBigDecimal
import io.liquidsoftware.safedomain.numeric.toJavaRoundingMode
import io.liquidsoftware.safedomain.web.HttpUrl
import io.liquidsoftware.safedomain.web.toJavaUri
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JvmInteropTest {
    @Test
    fun `decimals convert to and from BigDecimal without losing scale`() {
        for (raw in listOf("0", "0.00", "-12.30", "1E+4", "123456789012345678901234567890.5")) {
            assertEquals(BigDecimal(raw), Decimal.parse(raw).toJavaBigDecimal())
            assertEquals(Decimal.parse(raw), BigDecimal(raw).toDecimal())
        }
        val extreme = BigDecimal(BigInteger.TEN, Int.MIN_VALUE)
        assertEquals(extreme, extreme.toDecimal().toJavaBigDecimal())
        for (mode in RoundingMode.entries) assertEquals(mode, mode.toJavaRoundingMode().toDecimalRoundingMode())
    }

    @Test
    fun `currencies and urls convert to their JDK types`() {
        val usd = assertIs<Either.Right<Currency>>(either { Currency.from("USD") }).value
        assertEquals(java.util.Currency.getInstance("USD"), usd.toJavaCurrencyOrNull())
        val url = assertIs<Either.Right<HttpUrl>>(either { HttpUrl.from("https://example.com/a?b#c") }).value
        assertEquals(URI("https://example.com/a?b#c"), url.toJavaUri())
    }
}
