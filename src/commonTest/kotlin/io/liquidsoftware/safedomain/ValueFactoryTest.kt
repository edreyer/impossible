package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.numeric.PositiveLong
import io.liquidsoftware.safedomain.text.NonBlankString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ValueFactoryTest {
    @Test
    fun `maps each base type failure into the callers precise error model`() {
        val name: Either<Order.Error, NonBlankString> = either {
            NonBlankString.from("", onError = Order.Error::InvalidCustomer)
        }
        val quantity: Either<Order.Error, PositiveInt> = either {
            PositiveInt.from(0, onError = Order.Error::InvalidQuantity)
        }
        val count: Either<CountError, PositiveLong> = either {
            PositiveLong.from(-1, onError = ::CountError)
        }

        assertEquals(Either.Left(Order.Error.InvalidCustomer(NonBlankString.Error.Blank)), name)
        assertEquals(Either.Left(Order.Error.InvalidQuantity(PositiveInt.Error.NonPositive(0))), quantity)
        assertEquals(Either.Left(CountError(PositiveLong.Error.NonPositive(-1))), count)
    }

    @Test
    fun `successful construction preserves values without invoking error mappers`() {
        val result: Either<Order.Error, Order> = either {
            val customer = NonBlankString.from(" Alice ") { error("Mapper called on success") }
            val quantity = PositiveInt.from(2) { error("Mapper called on success") }
            val count = PositiveLong.from(1) { error("Mapper called on success") }

            assertEquals(" Alice ", customer.value)
            assertEquals(2, quantity.value)
            assertEquals(1L, count.value)
            Order.from(OrderInput(customer.value, quantity.value))
        }

        assertIs<Either.Right<Order>>(result)
    }

    @Test
    fun `the same mapping overload works across two domain boundaries`() {
        val result: Either<CheckoutError, Order> = either {
            Order.from(OrderInput("Alice", 0), onError = CheckoutError::InvalidOrder)
        }

        assertEquals(
            Either.Left(CheckoutError.InvalidOrder(Order.Error.InvalidQuantity(PositiveInt.Error.NonPositive(0)))),
            result,
        )
    }

    @Test
    fun `construction stops at the first mapped failure`() {
        val result: Either<CheckoutError, Order> = either {
            Order.from(OrderInput("", 0), onError = CheckoutError::InvalidOrder)
        }

        assertEquals(
            Either.Left(CheckoutError.InvalidOrder(Order.Error.InvalidCustomer(NonBlankString.Error.Blank))),
            result,
        )
    }

    @Test
    fun `unexpected factory exceptions propagate without invoking the mapper`() {
        val failure = IllegalStateException("Unexpected factory failure")
        val factory = object : ValueFactory<Int, PositiveInt.Error, PositiveInt> {
            context(raise: Raise<PositiveInt.Error>)
            override fun from(value: Int): PositiveInt = throw failure
        }

        val caught = assertFailsWith<IllegalStateException> {
            either<Order.Error, PositiveInt> {
                factory.from(1) { error("Mapper called for an unexpected exception") }
            }
        }

        assertSame(failure, caught)
    }

    @Test
    fun `exceptions thrown by an error mapper propagate`() {
        val failure = IllegalStateException("Unexpected mapper failure")

        val caught = assertFailsWith<IllegalStateException> {
            either<Order.Error, PositiveInt> {
                PositiveInt.from(0) { throw failure }
            }
        }

        assertSame(failure, caught)
    }

    private data class CountError(val cause: PositiveLong.Error)

    private sealed interface CheckoutError {
        data class InvalidOrder(val cause: Order.Error) : CheckoutError
    }

    private data class OrderInput(val customer: String, val quantity: Int)

    private class Order private constructor(val customer: NonBlankString, val quantity: PositiveInt) {
        sealed interface Error {
            data class InvalidCustomer(val cause: NonBlankString.Error) : Error
            data class InvalidQuantity(val cause: PositiveInt.Error) : Error
        }

        companion object : ValueFactory<OrderInput, Error, Order> {
            context(raise: Raise<Error>)
            override fun from(value: OrderInput): Order = Order(
                customer = NonBlankString.from(value.customer, onError = Error::InvalidCustomer),
                quantity = PositiveInt.from(value.quantity, onError = Error::InvalidQuantity),
            )
        }
    }
}
