package io.liquidsoftware.safedomain

import java.math.BigDecimal

/** A scale-independent hash matching numeric comparison, including extreme decimal scales. */
internal fun BigDecimal.numericHashCode(): Int {
    if (signum() == 0) return 0
    // stripTrailingZeros can overflow the Int scale; the normalized hash uses a Long scale.
    val digits = unscaledValue().toString()
    val significant = digits.trimEnd('0')
    val normalizedScale = scale().toLong() - (digits.length - significant.length)
    return 31 * significant.hashCode() + normalizedScale.hashCode()
}
