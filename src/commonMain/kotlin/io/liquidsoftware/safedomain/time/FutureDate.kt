package io.liquidsoftware.safedomain.time

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import java.time.LocalDate

/**
 * A date strictly after [asOf]. This is a stable comparison with a recorded reference
 * date, not a promise that [value] remains in the future as the clock advances.
 * Equality includes both dates. No clock or default time zone is consulted.
 */
class FutureDate private constructor(val value: LocalDate, val asOf: LocalDate) {
    data class Input(val value: LocalDate, val asOf: LocalDate)

    sealed interface Error {
        data class NotFuture(val value: LocalDate, val asOf: LocalDate) : Error
    }

    override fun equals(other: Any?): Boolean =
        other is FutureDate && value == other.value && asOf == other.asOf

    override fun hashCode(): Int = 31 * value.hashCode() + asOf.hashCode()

    override fun toString(): String = "FutureDate(value=$value, asOf=$asOf)"

    companion object : ValueFactory<Input, Error, FutureDate> {
        context(raise: Raise<Error>)
        override fun from(value: Input): FutureDate {
            raise.ensure(value.value > value.asOf) { Error.NotFuture(value.value, value.asOf) }
            return FutureDate(value.value, value.asOf)
        }
    }
}
