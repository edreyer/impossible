package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/** An integer in the inclusive range 0..[Int.MAX_VALUE]. */
@JvmInline
value class NonNegativeInt private constructor(val value: Int) {
    sealed interface Error {
        data class Negative(val value: Int) : Error
    }

    companion object : ValueFactory<Int, Error, NonNegativeInt> {
        /** Returns the wrapped [value], or raises [Error.Negative] if it is negative. */
        context(raise: Raise<Error>)
        override fun from(value: Int): NonNegativeInt {
            raise.ensure(value >= 0) { Error.Negative(value) }
            return NonNegativeInt(value)
        }
    }
}
