package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.liquidsoftware.safedomain.identity.TypedId
import kotlin.uuid.Uuid

/** Ids from arbitrary 128-bit UUIDs of any version, with the nil UUID as an edge case. */
fun <Tag> TypedId.Companion.arb(): Arb<TypedId<Tag>> =
    arbitrary(listOf(TypedId.of(Uuid.NIL))) { rs ->
        TypedId.of(Uuid.fromLongs(rs.random.nextLong(), rs.random.nextLong()))
    }
