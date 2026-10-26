package io.liquidsoftware.safedomain.collections

import arrow.core.NonEmptyList
import arrow.core.NonEmptySet
import arrow.core.PotentiallyUnsafeNonEmptyOperation
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.mapOrAccumulate
import arrow.core.wrapAsNonEmptyListOrThrow
import arrow.core.wrapAsNonEmptySetOrThrow
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory

/** The input had no elements. */
public sealed interface NonEmptyError : ValidationError {
    public data object Empty : NonEmptyError
}

/** Why input could not become a non-empty set without silently dropping elements. */
public sealed interface DistinctError : ValidationError {
    public data object Empty : DistinctError

    /** Zero-based positions; each repeat refers to the first equal element. */
    public data class Duplicate(val firstIndex: Int, val duplicateIndex: Int) : DistinctError
}

/**
 * Returns a factory for Arrow lists that copies input into an unmodifiable snapshot,
 * preserving order, duplicates, and element values. Elements are not copied.
 */
@OptIn(PotentiallyUnsafeNonEmptyOperation::class)
public fun <A> NonEmptyList.Companion.factory(): ValueFactory<Iterable<A>, NonEmptyError, NonEmptyList<A>> =
    object : ValueFactory<Iterable<A>, NonEmptyError, NonEmptyList<A>> {
        context(raise: Raise<NonEmptyError>)
        override fun from(value: Iterable<A>): NonEmptyList<A> {
            val snapshot = value.immutableListSnapshot()
            raise.ensure(snapshot.isNotEmpty()) { NonEmptyError.Empty }
            // The snapshot cannot change; validation proves the wrapping precondition.
            return snapshot.wrapAsNonEmptyListOrThrow()
        }
    }

/**
 * Returns a factory for Arrow sets. Equal elements collapse, retaining first-occurrence
 * order. Use [distinctFactory] when a repeat is an input error.
 */
@OptIn(PotentiallyUnsafeNonEmptyOperation::class)
public fun <A> NonEmptySet.Companion.factory(): ValueFactory<Iterable<A>, NonEmptyError, NonEmptySet<A>> =
    object : ValueFactory<Iterable<A>, NonEmptyError, NonEmptySet<A>> {
        context(raise: Raise<NonEmptyError>)
        override fun from(value: Iterable<A>): NonEmptySet<A> {
            val snapshot = value.immutableSetSnapshot()
            raise.ensure(snapshot.isNotEmpty()) { NonEmptyError.Empty }
            // The snapshot cannot change; validation proves the wrapping precondition.
            return snapshot.wrapAsNonEmptySetOrThrow()
        }
    }

/**
 * Returns a factory for Arrow sets that rejects repeats instead of collapsing them:
 * "at least one, no duplicates". Reports every duplicate in encounter order.
 * Elements must retain stable equality and hashing.
 */
@OptIn(PotentiallyUnsafeNonEmptyOperation::class)
public fun <A> NonEmptySet.Companion.distinctFactory(): ValueFactory<Iterable<A>, NonEmptyList<DistinctError>, NonEmptySet<A>> =
    object : ValueFactory<Iterable<A>, NonEmptyList<DistinctError>, NonEmptySet<A>> {
        context(raise: Raise<NonEmptyList<DistinctError>>)
        override fun from(value: Iterable<A>): NonEmptySet<A> {
            val snapshot = value.immutableListSnapshot()
            raise.ensure(snapshot.isNotEmpty()) { nonEmptyListOf(DistinctError.Empty) }
            val firstIndices = HashMap<A, Int>()
            raise.mapOrAccumulate(snapshot.withIndex()) { (index, element) ->
                val firstIndex = firstIndices.getOrPut(element) { index }
                if (firstIndex != index) this.raise(DistinctError.Duplicate(firstIndex, index))
            }
            return snapshot.immutableSetSnapshot().wrapAsNonEmptySetOrThrow()
        }
    }
