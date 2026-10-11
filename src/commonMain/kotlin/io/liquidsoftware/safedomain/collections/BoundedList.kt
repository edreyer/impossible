package io.liquidsoftware.safedomain.collections

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory

/**
 * An ordered list whose size is within the inclusive size [bounds]. Duplicates are allowed.
 *
 * [value] is an unmodifiable snapshot; elements are retained as supplied.
 * Equality and hashing include the ordered elements and bounds within this type.
 */
public class BoundedList<A> private constructor(public val value: List<A>, public val bounds: IntRange) : Iterable<A> {
    public sealed interface Error {
        public data class TooSmall(val minimum: Int, val actual: Int) : Error
        public data class TooLarge(val maximum: Int, val actual: Int) : Error
    }

    public val size: Int get() = value.size

    override fun iterator(): Iterator<A> = value.iterator()

    public operator fun contains(element: A): Boolean = element in value

    /** Mapping retains size and therefore the bounds, even when values become equal. */
    public fun <B> map(transform: (A) -> B): BoundedList<B> =
        BoundedList(value.map(transform).immutableListSnapshot(), bounds)

    /** Filtering can fall below the minimum; failures retain the resulting size. */
    context(raise: Raise<Error>)
    public fun filter(predicate: (A) -> Boolean): BoundedList<A> = factory<A>(bounds).from(value.filter(predicate))

    context(raise: Raise<Error>)
    public fun append(element: A): BoundedList<A> = factory<A>(bounds).from(value + element)

    override fun equals(other: Any?): Boolean =
        other is BoundedList<*> && value == other.value && bounds == other.bounds

    override fun hashCode(): Int = 31 * value.hashCode() + bounds.hashCode()

    override fun toString(): String = "BoundedList(value=$value, bounds=$bounds)"

    public companion object {
        /**
         * Returns a factory accepting sizes within [bounds], such as `1..50`. Bounds are
         * programmer configuration, so an empty range or a negative minimum throws
         * [IllegalArgumentException].
         */
        public fun <A> factory(bounds: IntRange): ValueFactory<Iterable<A>, Error, BoundedList<A>> {
            require(!bounds.isEmpty() && bounds.first >= 0) {
                "Size bounds must be a non-empty range of non-negative sizes: $bounds"
            }
            return object : ValueFactory<Iterable<A>, Error, BoundedList<A>> {
                context(raise: Raise<Error>)
                override fun from(value: Iterable<A>): BoundedList<A> {
                    val snapshot = value.immutableListSnapshot()
                    raise.ensure(snapshot.size >= bounds.first) { Error.TooSmall(bounds.first, snapshot.size) }
                    raise.ensure(snapshot.size <= bounds.last) { Error.TooLarge(bounds.last, snapshot.size) }
                    return BoundedList(snapshot, bounds)
                }
            }
        }
    }
}
