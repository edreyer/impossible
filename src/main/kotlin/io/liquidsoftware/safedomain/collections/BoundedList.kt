package io.liquidsoftware.safedomain.collections

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An ordered list whose size is within the inclusive [bounds]. Duplicates are allowed.
 *
 * [value] is an unmodifiable snapshot; elements are retained as supplied.
 * Equality and hashing include the ordered elements and bounds within this type.
 */
class BoundedList<A> private constructor(val value: List<A>, val bounds: Bounds) : Iterable<A> {
    /** Validated inclusive size bounds: 0 <= [minimum] <= [maximum]. */
    class Bounds private constructor(val minimum: Int, val maximum: Int) {
        data class Input(val minimum: Int, val maximum: Int)

        sealed interface Error {
            data class NegativeMinimum(val minimum: Int) : Error
            data class MaximumBelowMinimum(val minimum: Int, val maximum: Int) : Error
        }

        override fun equals(other: Any?): Boolean =
            other is Bounds && minimum == other.minimum && maximum == other.maximum

        override fun hashCode(): Int = 31 * minimum + maximum

        override fun toString(): String = "Bounds(minimum=$minimum, maximum=$maximum)"

        companion object : ValueFactory<Input, Error, Bounds> {
            context(raise: Raise<Error>)
            override fun from(value: Input): Bounds {
                raise.ensure(value.minimum >= 0) { Error.NegativeMinimum(value.minimum) }
                raise.ensure(value.maximum >= value.minimum) {
                    Error.MaximumBelowMinimum(value.minimum, value.maximum)
                }
                return Bounds(value.minimum, value.maximum)
            }
        }
    }

    sealed interface Error {
        data class TooSmall(val minimum: Int, val actual: Int) : Error
        data class TooLarge(val maximum: Int, val actual: Int) : Error
    }

    val size: Int get() = value.size

    override fun iterator(): Iterator<A> = value.iterator()

    operator fun contains(element: A): Boolean = element in value

    /** Mapping retains size and therefore the bounds, even when values become equal. */
    fun <B> map(transform: (A) -> B): BoundedList<B> =
        BoundedList(value.map(transform).immutableListSnapshot(), bounds)

    /** Filtering can fall below the minimum; failures retain the resulting size. */
    context(raise: Raise<Error>)
    fun filter(predicate: (A) -> Boolean): BoundedList<A> = factory<A>(bounds).from(value.filter(predicate))

    context(raise: Raise<Error>)
    fun append(element: A): BoundedList<A> = factory<A>(bounds).from(value + element)

    override fun equals(other: Any?): Boolean =
        other is BoundedList<*> && value == other.value && bounds == other.bounds

    override fun hashCode(): Int = 31 * value.hashCode() + bounds.hashCode()

    override fun toString(): String = "BoundedList(value=$value, bounds=$bounds)"

    companion object {
        fun <A> factory(bounds: Bounds): ValueFactory<Iterable<A>, Error, BoundedList<A>> =
            object : ValueFactory<Iterable<A>, Error, BoundedList<A>> {
                context(raise: Raise<Error>)
                override fun from(value: Iterable<A>): BoundedList<A> {
                    val snapshot = value.immutableListSnapshot()
                    raise.ensure(snapshot.size >= bounds.minimum) { Error.TooSmall(bounds.minimum, snapshot.size) }
                    raise.ensure(snapshot.size <= bounds.maximum) { Error.TooLarge(bounds.maximum, snapshot.size) }
                    return BoundedList(snapshot, bounds)
                }
            }
    }
}
