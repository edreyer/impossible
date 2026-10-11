package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.set
import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.collections.UniqueList
import io.liquidsoftware.safedomain.fromTrusted

/** Lists of [element] values whose size is within [bounds], including both edge sizes. */
public fun <A> BoundedList.Companion.arb(element: Arb<A>, bounds: IntRange): Arb<BoundedList<A>> {
    val factory = BoundedList.factory<A>(bounds)
    return Arb.list(element, bounds).map { factory.fromTrusted(it) }
}

/**
 * Lists of distinct [element] values with a size in [sizes]. [element] must be able to
 * produce enough distinct values for the largest size.
 */
public fun <A> UniqueList.Companion.arb(element: Arb<A>, sizes: IntRange = 0..10): Arb<UniqueList<A>> {
    val factory = UniqueList.factory<A>()
    return Arb.set(element, sizes).map { factory.fromTrusted(it.toList()) }
}
