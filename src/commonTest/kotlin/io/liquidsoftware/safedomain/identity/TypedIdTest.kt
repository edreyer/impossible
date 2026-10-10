package io.liquidsoftware.safedomain.identity

import arrow.core.Either
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

class TypedIdTest {
    private interface CustomerTag
    private interface OrderTag
    private val raw = "550e8400-e29b-41d4-a716-446655440000"
    private val customers = TypedId.factory<CustomerTag>()
    private val orders = TypedId.factory<OrderTag>()

    @Test
    fun `typed ID accepts canonical UUID shape and normalizes hexadecimal case`() {
        for (input in listOf(raw, raw.uppercase(), "00000000-0000-0000-0000-000000000000")) {
            val id = assertIs<Either.Right<TypedId<CustomerTag>>>(either { customers.from(input) }).value
            assertEquals(Uuid.parse(input), id.value)
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
            assertEquals(first, TypedId.of<CustomerTag>(Uuid.parse(raw)))
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `tags are compile-time only, so the same UUID under different tags is equal at runtime`() {
        val result = either {
            val customer = customers.from(raw)
            val order = orders.from(raw)
            assertEquals(customer as Any, order as Any)
            assertEquals(customer.value, order.value)
        }
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun `random ids are distinct version 4 UUIDs`() {
        val first = TypedId.random<CustomerTag>()
        val second = TypedId.random<CustomerTag>()
        assertNotEquals(first, second)
        assertEquals('4', first.value.toString()[14])
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
