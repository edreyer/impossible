package io.liquidsoftware.safedomain

import arrow.core.NonEmptySet
import arrow.core.PotentiallyUnsafeNonEmptyOperation
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.wrapAsNonEmptySetOrThrow

/** Typed construction of Arrow sets. Equal elements collapse, retaining first-occurrence order. */
object NonEmptySets {
    sealed interface Error {
        data object Empty : Error
    }

    /**
     * Returns a factory that copies input into an unmodifiable snapshot.
     * Elements are not copied. Subsequent Arrow operations follow Arrow's own storage contracts.
     */
    fun <A> factory(): ValueFactory<Iterable<A>, Error, NonEmptySet<A>> =
        object : ValueFactory<Iterable<A>, Error, NonEmptySet<A>> {
            context(raise: Raise<Error>)
            @OptIn(PotentiallyUnsafeNonEmptyOperation::class)
            override fun from(value: Iterable<A>): NonEmptySet<A> {
                val snapshot = value.immutableSetSnapshot()
                raise.ensure(snapshot.isNotEmpty()) { Error.Empty }
                // The snapshot cannot change; validation proves the wrapping precondition.
                return snapshot.wrapAsNonEmptySetOrThrow()
            }
        }
}
