package io.liquidsoftware.safedomain.time

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import kotlinx.datetime.LocalDate

/**
 * A date strictly before [asOf]. Equality includes the value and its explicit
 * reference date. No clock or default time zone is consulted.
 */
class PastDate private constructor(val value: LocalDate, val asOf: LocalDate) {
    data class Input(val value: LocalDate, val asOf: LocalDate)

    sealed interface Error {
        data class NotPast(val value: LocalDate, val asOf: LocalDate) : Error
    }

    override fun equals(other: Any?): Boolean =
        other is PastDate && value == other.value && asOf == other.asOf

    override fun hashCode(): Int = 31 * value.hashCode() + asOf.hashCode()

    override fun toString(): String = "PastDate(value=$value, asOf=$asOf)"

    companion object : ValueFactory<Input, Error, PastDate> {
        context(raise: Raise<Error>)
        override fun from(value: Input): PastDate {
            raise.ensure(value.value < value.asOf) { Error.NotPast(value.value, value.asOf) }
            return PastDate(value.value, value.asOf)
        }
    }
}
