package io.liquidsoftware.safedomain.collections

import java.util.Collections

/** Copies collection structure; elements are retained as supplied. */
internal fun <A> Iterable<A>.immutableListSnapshot(): List<A> =
    Collections.unmodifiableList(toMutableList())

internal fun <A> Iterable<A>.immutableSetSnapshot(): Set<A> =
    Collections.unmodifiableSet(toCollection(LinkedHashSet()))
