package io.liquidsoftware.safedomain.numeric

import java.math.BigDecimal
import java.math.BigInteger

/** The same value and scale as a `java.math.BigDecimal`. */
public fun Decimal.toJavaBigDecimal(): BigDecimal = BigDecimal(BigInteger(unscaled.toString()), scale)

/** The same value and scale as a [Decimal]. */
public fun BigDecimal.toDecimal(): Decimal = Decimal.ofUnscaled(unscaledValue().toString(), scale())

public fun RoundingMode.toJavaRoundingMode(): java.math.RoundingMode = java.math.RoundingMode.valueOf(name)

public fun java.math.RoundingMode.toDecimalRoundingMode(): RoundingMode = RoundingMode.valueOf(name)
