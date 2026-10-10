package io.liquidsoftware.safedomain.collections

import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.mapOrAccumulate
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An ordered, possibly empty list with no equal elements.
 *
 * [value] is an unmodifiable snapshot. Elements must retain stable equality and hashing.
 * Equality and hashing use the ordered elements within this type, not ordinary list equality.
 */
class UniqueList<A> private constructor(val value: List<A>) : Iterable<A> {
    sealed interface Error {
        /** Zero-based positions; each repeat refers to the first equal element. */
        data class Duplicate(val firstIndex: Int, val duplicateIndex: Int) : Error
    }

    val size: Int get() = value.size

    override fun iterator(): Iterator<A> = value.iterator()

    operator fun contains(element: A): Boolean = element in value

    /** Mapping may introduce collisions, so all resulting duplicates are validated. */
    context(raise: Raise<NonEmptyList<Error>>)
    fun <B> map(transform: (A) -> B): UniqueList<B> = factory<B>().from(value.map(transform))

    /** A subset preserves uniqueness, including an empty subset. */
    fun filter(predicate: (A) -> Boolean): UniqueList<A> =
        UniqueList(value.filter(predicate).immutableListSnapshot())

    context(raise: Raise<NonEmptyList<Error>>)
    fun append(element: A): UniqueList<A> = factory<A>().from(value + element)

    override fun equals(other: Any?): Boolean = other is UniqueList<*> && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "UniqueList($value)"

    companion object {
        /** Reports every duplicate in encounter order; never silently removes elements. */
        fun <A> factory(): ValueFactory<Iterable<A>, NonEmptyList<Error>, UniqueList<A>> =
            object : ValueFactory<Iterable<A>, NonEmptyList<Error>, UniqueList<A>> {
                context(raise: Raise<NonEmptyList<Error>>)
                override fun from(value: Iterable<A>): UniqueList<A> {
                    val snapshot = value.immutableListSnapshot()
                    val firstIndices = HashMap<A, Int>()
                    raise.mapOrAccumulate(snapshot.withIndex()) { (index, element) ->
                        val firstIndex = firstIndices.putIfAbsent(element, index)
                        if (firstIndex != null) this.raise(Error.Duplicate(firstIndex, index))
                    }
                    return UniqueList(snapshot)
                }
            }
    }
}
