package io.liquidsoftware.safedomain

import arrow.core.NonEmptyList
import arrow.core.PotentiallyUnsafeNonEmptyOperation
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.wrapAsNonEmptyListOrThrow

/** Typed construction of Arrow lists, preserving order, duplicates, and element values. */
object NonEmptyLists {
    sealed interface Error {
        data object Empty : Error
    }

    /**
     * Returns a factory that copies input into an unmodifiable snapshot.
     * Elements are not copied. Subsequent Arrow operations follow Arrow's own storage contracts.
     */
    fun <A> factory(): ValueFactory<Iterable<A>, Error, NonEmptyList<A>> =
        object : ValueFactory<Iterable<A>, Error, NonEmptyList<A>> {
            context(raise: Raise<Error>)
            @OptIn(PotentiallyUnsafeNonEmptyOperation::class)
            override fun from(value: Iterable<A>): NonEmptyList<A> {
                val snapshot = value.immutableListSnapshot()
                raise.ensure(snapshot.isNotEmpty()) { Error.Empty }
                // The snapshot cannot change; validation proves the wrapping precondition.
                return snapshot.wrapAsNonEmptyListOrThrow()
            }
        }
}
