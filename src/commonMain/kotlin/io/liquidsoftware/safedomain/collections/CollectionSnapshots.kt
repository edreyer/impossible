package io.liquidsoftware.safedomain.collections

/**
 * Copies collection structure into storage no caller can reach; elements are retained as supplied.
 * The returned views are read-only on every platform: they expose no mutable storage,
 * iterators, or sublists, and on the JVM their mutator methods throw.
 */
internal fun <A> Iterable<A>.immutableListSnapshot(): List<A> = ListSnapshot(toList())

internal fun <A> Iterable<A>.immutableSetSnapshot(): Set<A> = SetSnapshot(toCollection(LinkedHashSet()))

private class ListSnapshot<A>(private val items: List<A>) : AbstractList<A>() {
    override val size: Int get() = items.size
    override fun get(index: Int): A = items[index]
}

private class SetSnapshot<A>(private val items: Set<A>) : AbstractSet<A>() {
    override val size: Int get() = items.size
    override fun contains(element: A): Boolean = element in items
    override fun iterator(): Iterator<A> {
        val source = items.iterator()
        return object : Iterator<A> by source {}
    }
}
