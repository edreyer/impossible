package io.liquidsoftware.safedomain.identity

import arrow.core.raise.Raise
import arrow.core.raise.ensureNotNull
import io.liquidsoftware.safedomain.ValueFactory
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.uuid.Uuid

/**
 * A Kotlin [Uuid] with an invariant domain [Tag]. Equality and hashing include the complete
 * runtime tag, including generic arguments. Distinct marker types therefore remain
 * distinct in heterogeneous collections. This does not establish entity existence.
 * UUID text uses 8-4-4-4-12 hexadecimal groups; case is accepted and Uuid.toString()
 * renders canonical lowercase. Nil UUIDs and all versions are accepted.
 */
class TypedId<Tag : Any> private constructor(val value: Uuid, private val tag: KType) {
    sealed interface Error {
        data class InvalidFormat(val value: String) : Error
    }

    override fun equals(other: Any?): Boolean =
        other is TypedId<*> && tag == other.tag && value == other.value

    override fun hashCode(): Int = 31 * tag.hashCode() + value.hashCode()

    override fun toString(): String = "TypedId(tag=$tag, value=$value)"

    companion object {
        /** Returns a reusable validated factory with the usual mapped from overload. */
        inline fun <reified Tag : Any> factory(): ValueFactory<String, Error, TypedId<Tag>> =
            factory<Tag>(typeOf<Tag>())

        /** Wraps an already typed UUID without parsing or changing its domain tag. */
        inline fun <reified Tag : Any> create(value: Uuid): TypedId<Tag> = create(value, typeOf<Tag>())

        @PublishedApi
        @JvmSynthetic
        internal fun <Tag : Any> factory(tag: KType): ValueFactory<String, Error, TypedId<Tag>> =
            object : ValueFactory<String, Error, TypedId<Tag>> {
                context(raise: Raise<Error>)
                override fun from(value: String): TypedId<Tag> {
                    val uuid = raise.ensureNotNull(Uuid.parseHexDashOrNull(value)) { Error.InvalidFormat(value) }
                    return create(uuid, tag)
                }
            }

        @PublishedApi
        @JvmSynthetic
        internal fun <Tag : Any> create(value: Uuid, tag: KType): TypedId<Tag> = TypedId(value, tag)
    }
}
