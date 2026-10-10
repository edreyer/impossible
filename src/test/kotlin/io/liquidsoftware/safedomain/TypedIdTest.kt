package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.raise.either
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class TypedIdTest {
    private interface CustomerTag
    private interface OrderTag
    private interface ItemTag<T>
    private val raw = "550e8400-e29b-41d4-a716-446655440000"
    private val customers = TypedId.factory<CustomerTag>()
    private val orders = TypedId.factory<OrderTag>()

    @Test
    fun `typed ID accepts canonical UUID shape and normalizes hexadecimal case`() {
        for (input in listOf(raw, raw.uppercase(), "00000000-0000-0000-0000-000000000000")) {
            val id = assertIs<Either.Right<TypedId<CustomerTag>>>(either { customers.from(input) }).value
            assertEquals(UUID.fromString(input), id.value)
        }
        val id = assertIs<Either.Right<TypedId<CustomerTag>>>(either { customers.from(raw.uppercase()) }).value
        assertEquals(raw, id.value.toString())
    }

    @Test
    fun `typed ID rejects shortened UUID forms invalid hex and whitespace`() {
        for (input in listOf("", "1-1-1-1-1", raw.replace("-", ""), "{$raw}", " $raw", "$raw ", "g" + raw.drop(1))) {
            val result: Either<TypedId.Error, TypedId<CustomerTag>> = either { customers.from(input) }
            assertEquals(Either.Left(TypedId.Error.InvalidFormat(input)), result)
        }
    }

    @Test
    fun `same UUID and same tag give the same value across factory instances`() {
        val result = either {
            val first = customers.from(raw)
            val second = TypedId.factory<CustomerTag>().from(raw.uppercase())
            assertEquals(first, second)
            assertEquals(first.hashCode(), second.hashCode())
            assertEquals(1, setOf(first, second).size)
            assertEquals("customer", mapOf(first to "customer")[second])
            assertEquals(first, TypedId.create<CustomerTag>(UUID.fromString(raw)))
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `different tags remain unequal even after identifiers are placed in a heterogeneous collection`() {
        val result = either {
            val customer = customers.from(raw)
            val order = orders.from(raw)
            assertNotEquals(customer as Any, order as Any)
            assertEquals(2, setOf<Any>(customer, order).size)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `generic marker type arguments also participate in runtime identity`() {
        val result = either {
            val text = TypedId.factory<ItemTag<String>>().from(raw)
            val number = TypedId.factory<ItemTag<Int>>().from(raw)
            assertNotEquals(text as Any, number as Any)
            assertEquals(2, setOf<Any>(text, number).size)
            assertEquals(text, TypedId.create<ItemTag<String>>(UUID.fromString(raw)))
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `typed ID factory inherits error mapping into its consuming domain`() {
        data class InvalidCustomerId(val cause: TypedId.Error)
        val result: Either<InvalidCustomerId, TypedId<CustomerTag>> = either {
            customers.from("invalid", onError = ::InvalidCustomerId)
        }
        assertEquals(Either.Left(InvalidCustomerId(TypedId.Error.InvalidFormat("invalid"))), result)
    }
}
