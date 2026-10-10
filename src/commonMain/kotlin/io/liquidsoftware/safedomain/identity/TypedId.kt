package io.liquidsoftware.safedomain.identity

import arrow.core.raise.Raise
import arrow.core.raise.ensureNotNull
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * A Kotlin [Uuid] tagged at compile time with a phantom domain [Tag], so a
 * `TypedId<User>` cannot be passed where a `TypedId<Order>` is expected. The tag is
 * erased at runtime: equality and hashing use the UUID only, so ids with different
 * tags but the same UUID are equal if compared as [Any]. This does not establish
 * entity existence. UUID text uses 8-4-4-4-12 hexadecimal groups; case is accepted
 * and [Uuid.toString] renders canonical lowercase. Nil UUIDs and all versions are accepted.
 */
@JvmInline
value class TypedId<Tag> private constructor(val value: Uuid) {
    sealed interface Error {
        data class InvalidFormat(val value: String) : Error
    }

    override fun toString(): String = "TypedId(value=$value)"

    companion object {
        private val parser = object : ValueFactory<String, Error, TypedId<Nothing>> {
            context(raise: Raise<Error>)
            override fun from(value: String): TypedId<Nothing> {
                val uuid = raise.ensureNotNull(Uuid.parseHexDashOrNull(value)) { Error.InvalidFormat(value) }
                return TypedId(uuid)
            }
        }

        /** Returns a reusable factory that parses UUID text. */
        @Suppress("UNCHECKED_CAST")
        fun <Tag> factory(): ValueFactory<String, Error, TypedId<Tag>> =
            parser as ValueFactory<String, Error, TypedId<Tag>>

        /** Tags an already parsed UUID. */
        fun <Tag> of(value: Uuid): TypedId<Tag> = TypedId(value)

        /** A new random (version 4) id. */
        fun <Tag> random(): TypedId<Tag> = TypedId(Uuid.random())
    }
}
