package io.liquidsoftware.safedomain.numeric

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * An immutable, arbitrary-precision decimal: an unscaled integer and a [scale], so the
 * value is `unscaled × 10^-scale`. It behaves like `java.math.BigDecimal` on every
 * platform: [equals] and [hashCode] include scale (1.0 is not 1.00), while [compareTo]
 * is numeric. Operations never round implicitly; [divide] and [setScale] take an
 * explicit [RoundingMode].
 *
 * Operations throw [ArithmeticException] when a result scale leaves the [Int] range,
 * when [RoundingMode.UNNECESSARY] would have to round, or when aligning scales would
 * need more than [MAX_EXPANSION_DIGITS] extra digits.
 */
class Decimal private constructor(
    internal val unscaled: BigInteger,
    val scale: Int,
) : Comparable<Decimal> {
    /** -1, 0 or 1 for negative, zero or positive values. */
    val sign: Int get() = unscaled.signum()

    /** The number of digits in the unscaled value; 1 for zero. */
    val precision: Int get() = unscaledDigits.length

    private val unscaledDigits: String by lazy { unscaled.abs().toString() }

    /** The exponent of the leading digit in scientific notation. */
    private val adjustedExponent: Long get() = precision.toLong() - 1 - scale

    operator fun plus(other: Decimal): Decimal = when {
        scale == other.scale -> Decimal(unscaled + other.unscaled, scale)
        scale < other.scale -> Decimal(upscale(other.scale) + other.unscaled, other.scale)
        else -> Decimal(unscaled + other.upscale(scale), scale)
    }

    operator fun minus(other: Decimal): Decimal = this + (-other)

    operator fun times(other: Decimal): Decimal =
        Decimal(unscaled * other.unscaled, checkScale(scale.toLong() + other.scale, isZero = sign == 0 || other.sign == 0))

    operator fun unaryMinus(): Decimal = Decimal(unscaled.negate(), scale)

    fun abs(): Decimal = if (sign < 0) -this else this

    /** Divides and rounds the quotient to [scale] digits after the point. */
    fun divide(divisor: Decimal, scale: Int, roundingMode: RoundingMode): Decimal {
        if (divisor.sign == 0) throw ArithmeticException("Division by zero")
        if (sign == 0) return Decimal(BigInteger.ZERO, scale)
        // quotient = unscaled * 10^shift / divisor.unscaled, rounded to an integer.
        val shift = scale.toLong() - this.scale + divisor.scale
        return if (shift >= 0) {
            Decimal(roundedQuotient(unscaled * powerOfTen(shift), divisor.unscaled, roundingMode), scale)
        } else {
            val digitsAvailable = precision.toLong() - divisor.precision
            if (-shift > digitsAvailable + 1) {
                Decimal(roundTinyRemainder(sign * divisor.sign, roundingMode), scale)
            } else {
                Decimal(roundedQuotient(unscaled, divisor.unscaled * powerOfTen(-shift), roundingMode), scale)
            }
        }
    }

    /** Returns this value with [scale] digits after the point, rounding only as [roundingMode] allows. */
    fun setScale(scale: Int, roundingMode: RoundingMode): Decimal = when {
        scale == this.scale -> this
        scale > this.scale -> Decimal(upscale(scale), scale)
        sign == 0 -> Decimal(BigInteger.ZERO, scale)
        else -> {
            val drop = this.scale.toLong() - scale
            if (drop > precision) {
                Decimal(roundTinyRemainder(sign, roundingMode), scale)
            } else {
                Decimal(roundedQuotient(unscaled, powerOfTen(drop), roundingMode), scale)
            }
        }
    }

    override fun compareTo(other: Decimal): Int {
        if (sign != other.sign) return sign.compareTo(other.sign)
        if (sign == 0) return 0
        val magnitude = adjustedExponent.compareTo(other.adjustedExponent).takeIf { it != 0 }
            ?: run {
                // Equal leading exponents bound the scale difference by the digit counts.
                val left = if (scale < other.scale) upscale(other.scale) else unscaled
                val right = if (other.scale < scale) other.upscale(scale) else other.unscaled
                left.abs().compareTo(right.abs())
            }
        return sign * magnitude
    }

    override fun equals(other: Any?): Boolean =
        other is Decimal && scale == other.scale && unscaled == other.unscaled

    override fun hashCode(): Int = 31 * unscaled.toString().hashCode() + scale

    /** The same text as `java.math.BigDecimal.toString()`, using scientific notation for extreme scales. */
    override fun toString(): String {
        val adjusted = adjustedExponent
        if (scale >= 0 && adjusted >= -6) return toPlainString()
        val digits = unscaledDigits
        return buildString {
            if (sign < 0) append('-')
            append(digits[0])
            if (digits.length > 1) append('.').append(digits, 1, digits.length)
            append('E')
            if (adjusted > 0) append('+')
            append(adjusted)
        }
    }

    /** The value without an exponent, like `java.math.BigDecimal.toPlainString()`. */
    fun toPlainString(): String {
        if (scale < 0) {
            if (sign == 0) return "0"
            return unscaled.toString() + "0".repeat(-scale)
        }
        if (scale == 0) return unscaled.toString()
        val digits = unscaledDigits.padStart(scale + 1, '0')
        val point = digits.length - scale
        return buildString {
            if (sign < 0) append('-')
            append(digits, 0, point).append('.').append(digits, point, digits.length)
        }
    }

    /** A scale-independent hash matching numeric comparison, including extreme scales. */
    internal fun numericHashCode(): Int {
        if (sign == 0) return 0
        val digits = unscaled.toString()
        val significant = digits.trimEnd('0')
        val normalizedScale = scale.toLong() - (digits.length - significant.length)
        return 31 * significant.hashCode() + normalizedScale.hashCode()
    }

    private fun upscale(newScale: Int): BigInteger =
        if (sign == 0) unscaled else unscaled * powerOfTen(newScale.toLong() - scale)

    companion object {
        /** The most digits an operation may add when aligning or extending scales. */
        const val MAX_EXPANSION_DIGITS: Int = 10_000

        val ZERO: Decimal = Decimal(BigInteger.ZERO, 0)
        val ONE: Decimal = Decimal(BigInteger.ONE, 0)

        /** [value] with scale 0. */
        fun of(value: Long): Decimal = Decimal(BigInteger.fromLong(value), 0)

        /** `unscaled × 10^-scale`, so `of(1234, 2)` is 12.34. */
        fun of(unscaled: Long, scale: Int): Decimal = Decimal(BigInteger.fromLong(unscaled), scale)

        /**
         * Parses `java.math.BigDecimal` syntax: an optional sign, digits with an optional
         * point, and an optional exponent (`-12.30`, `1E+4`, `.5`). Digits must be ASCII and
         * the resulting scale must fit in an [Int]. Whitespace, `NaN` and `Infinity` are rejected.
         */
        fun parseOrNull(text: String): Decimal? {
            var index = 0
            val negative = text.startsWith('-')
            if (negative || text.startsWith('+')) index++
            val digits = StringBuilder()
            var fractionDigits = 0
            var seenPoint = false
            while (index < text.length) {
                val char = text[index]
                when {
                    char in '0'..'9' -> {
                        digits.append(char)
                        if (seenPoint) fractionDigits++
                    }
                    char == '.' && !seenPoint -> seenPoint = true
                    else -> break
                }
                index++
            }
            if (digits.isEmpty()) return null
            var exponent = 0L
            if (index < text.length) {
                if (text[index] != 'e' && text[index] != 'E') return null
                exponent = parseExponent(text, index + 1) ?: return null
            }
            val scale = fractionDigits - exponent
            if (scale !in Int.MIN_VALUE..Int.MAX_VALUE) return null
            return ofUnscaled((if (negative) "-" else "") + digits, scale.toInt())
        }

        /** Like [parseOrNull], but throws [NumberFormatException] for invalid text. */
        fun parse(text: String): Decimal =
            parseOrNull(text) ?: throw NumberFormatException("Invalid decimal: \"$text\"")

        /** Builds a value from a base-10 unscaled integer such as "-1234". */
        internal fun ofUnscaled(digits: String, scale: Int): Decimal {
            val negative = digits.startsWith('-')
            val magnitude = digits.trimStart('-', '+').trimStart('0')
            val unscaled = when {
                magnitude.isEmpty() -> BigInteger.ZERO
                negative -> BigInteger.parseString("-$magnitude", 10)
                else -> BigInteger.parseString(magnitude, 10)
            }
            return Decimal(unscaled, scale)
        }

        private fun parseExponent(text: String, start: Int): Long? {
            var index = start
            val negative = index < text.length && text[index] == '-'
            if (index < text.length && (text[index] == '-' || text[index] == '+')) index++
            if (index == text.length) return null
            var value = 0L
            while (index < text.length) {
                val char = text[index++]
                if (char !in '0'..'9') return null
                value = value * 10 + (char - '0')
                // Anything past this already overflows every Int scale.
                if (value > 10L * Int.MAX_VALUE) return null
            }
            return if (negative) -value else value
        }

        private fun powerOfTen(exponent: Long): BigInteger {
            if (exponent > MAX_EXPANSION_DIGITS) {
                throw ArithmeticException("Result needs more than $MAX_EXPANSION_DIGITS extra digits")
            }
            return BigInteger.TEN.pow(exponent)
        }

        private fun checkScale(scale: Long, isZero: Boolean): Int = when {
            scale in Int.MIN_VALUE..Int.MAX_VALUE -> scale.toInt()
            isZero -> if (scale > 0) Int.MAX_VALUE else Int.MIN_VALUE
            else -> throw ArithmeticException(if (scale > 0) "Scale underflow" else "Scale overflow")
        }

        private val two = BigInteger.fromInt(2)

        /** Integer division rounded by [mode]. */
        private fun roundedQuotient(numerator: BigInteger, divisor: BigInteger, mode: RoundingMode): BigInteger {
            val (quotient, remainder) = numerator.divideAndRemainder(divisor)
            if (remainder.isZero()) return quotient
            val direction = numerator.signum() * divisor.signum()
            val half = (remainder.abs() * two).compareTo(divisor.abs())
            return if (roundsAway(mode, direction, half, quotientIsOdd = !quotient.remainder(two).isZero())) {
                quotient + BigInteger.fromInt(direction)
            } else {
                quotient
            }
        }

        /** Rounds a zero quotient whose nonzero remainder is less than half the divisor. */
        private fun roundTinyRemainder(direction: Int, mode: RoundingMode): BigInteger =
            if (roundsAway(mode, direction, half = -1, quotientIsOdd = false)) BigInteger.fromInt(direction) else BigInteger.ZERO

        /**
         * Whether an inexact quotient moves one unit away from zero. [direction] is the sign of
         * the exact result and [half] compares twice the remainder with the divisor.
         */
        private fun roundsAway(mode: RoundingMode, direction: Int, half: Int, quotientIsOdd: Boolean): Boolean =
            when (mode) {
                RoundingMode.UP -> true
                RoundingMode.DOWN -> false
                RoundingMode.CEILING -> direction > 0
                RoundingMode.FLOOR -> direction < 0
                RoundingMode.HALF_UP -> half >= 0
                RoundingMode.HALF_DOWN -> half > 0
                RoundingMode.HALF_EVEN -> half > 0 || (half == 0 && quotientIsOdd)
                RoundingMode.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
            }
    }
}

/** How [Decimal.divide] and [Decimal.setScale] discard digits, with `java.math.RoundingMode` semantics. */
enum class RoundingMode { UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY }
