package io.liquidsoftware.safedomain.numeric

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline

/** An integer in the inclusive range 1..[Int.MAX_VALUE]. */
@JvmInline
value class PositiveInt private constructor(val value: Int) {
    sealed interface Error {
        data class NonPositive(val value: Int) : Error
    }

    companion object : ValueFactory<Int, Error, PositiveInt> {
        /** Returns the wrapped [value], or raises [Error.NonPositive] if it is zero or negative. */
        context(raise: Raise<Error>)
        override fun from(value: Int): PositiveInt {
            raise.ensure(value > 0) { Error.NonPositive(value) }
            return PositiveInt(value)
        }
    }
}
