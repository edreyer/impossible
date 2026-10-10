package io.liquidsoftware.safedomain.money

/**
 * The JDK currency for this code, or null when the running JDK's currency data does
 * not include it. The bundled ISO 4217 list and the JDK's data can differ by version.
 */
fun Currency.toJavaCurrencyOrNull(): java.util.Currency? =
    try {
        java.util.Currency.getInstance(code)
    } catch (_: IllegalArgumentException) {
        null
    }
