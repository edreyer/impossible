package io.liquidsoftware.safedomain.time

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ErrorMessage
import io.liquidsoftware.safedomain.ErrorMessages
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline
import kotlin.time.Duration

/**
 * A finite [Duration] strictly greater than zero, such as a timeout, a slot length,
 * or a retry interval. [Duration.INFINITE] is rejected as [Error.Infinite].
 */
@JvmInline
public value class PositiveDuration private constructor(public val value: Duration) : Comparable<PositiveDuration> {
    public sealed interface Error : ValidationError {
        override fun describe(): ErrorMessage = ErrorMessages.describe(this)

        public data class NonPositive(val value: Duration) : Error
        public data object Infinite : Error
    }

    override fun compareTo(other: PositiveDuration): Int = value.compareTo(other.value)

    public companion object : ValueFactory<Duration, Error, PositiveDuration> {
        context(raise: Raise<Error>)
        override fun from(value: Duration): PositiveDuration {
            raise.ensure(value.isPositive()) { Error.NonPositive(value) }
            raise.ensure(value.isFinite()) { Error.Infinite }
            return PositiveDuration(value)
        }
    }
}
