package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AccumulatingValueFactoryTest {
    @Test
    fun `reports every invalid order field in a precise nonempty error list`() {
        val result: Either<NonEmptyList<DemoOrder.Error>, DemoOrder> = either {
            DemoOrder.from(DemoOrderInput(reference = "", quantity = 0, revision = -1))
        }

        assertEquals(
            Either.Left(nonEmptyListOf(
                DemoOrder.Error.InvalidReference(NonBlankString.Error.Blank),
                DemoOrder.Error.InvalidQuantity(PositiveInt.Error.NonPositive(0)),
                DemoOrder.Error.InvalidRevision(NonNegativeInt.Error.Negative(-1)),
            )),
            result,
        )
    }

    @Test
    fun `reports only the invalid field when the other fields are valid`() {
        val cases = listOf(
            DemoOrderInput("", 1, 0) to DemoOrder.Error.InvalidReference(NonBlankString.Error.Blank),
            DemoOrderInput("ORD-1", 0, 0) to DemoOrder.Error.InvalidQuantity(PositiveInt.Error.NonPositive(0)),
            DemoOrderInput("ORD-1", 1, -1) to DemoOrder.Error.InvalidRevision(NonNegativeInt.Error.Negative(-1)),
        )

        for ((input, expectedError) in cases) {
            val result: Either<NonEmptyList<DemoOrder.Error>, DemoOrder> = either {
                DemoOrder.from(input)
            }

            assertEquals(Either.Left(nonEmptyListOf(expectedError)), result, "Input: $input")
        }
    }

    @Test
    fun `constructs an order from all valid fields and preserves their values`() {
        val result: Either<NonEmptyList<DemoOrder.Error>, DemoOrder> = either {
            DemoOrder.from(DemoOrderInput(reference = " ORD-1 ", quantity = 1, revision = 0))
        }
        val order = assertIs<Either.Right<DemoOrder>>(result).value

        assertEquals(" ORD-1 ", order.reference.value)
        assertEquals(1, order.quantity.value)
        assertEquals(0, order.revision.value)
    }

    @Test
    fun `checkout collects its own failure and preserves every nested order failure`() {
        val result: Either<NonEmptyList<DemoCheckout.Error>, DemoCheckout> = either {
            DemoCheckout.from(DemoCheckoutInput(
                order = DemoOrderInput(reference = "", quantity = 0, revision = -1),
                customer = "",
            ))
        }

        assertEquals(
            Either.Left(nonEmptyListOf(
                DemoCheckout.Error.InvalidOrder(nonEmptyListOf(
                    DemoOrder.Error.InvalidReference(NonBlankString.Error.Blank),
                    DemoOrder.Error.InvalidQuantity(PositiveInt.Error.NonPositive(0)),
                    DemoOrder.Error.InvalidRevision(NonNegativeInt.Error.Negative(-1)),
                )),
                DemoCheckout.Error.InvalidCustomer(NonBlankString.Error.Blank),
            )),
            result,
        )
    }

    @Test
    fun `maps a complete order error batch into one stable checkout outcome`() {
        val result: Either<DemoCheckout.Error, DemoOrder> = either {
            DemoOrder.from(
                DemoOrderInput(reference = "", quantity = 0, revision = -1),
                onError = DemoCheckout.Error::InvalidOrder,
            )
        }

        assertEquals(
            Either.Left(DemoCheckout.Error.InvalidOrder(nonEmptyListOf(
                DemoOrder.Error.InvalidReference(NonBlankString.Error.Blank),
                DemoOrder.Error.InvalidQuantity(PositiveInt.Error.NonPositive(0)),
                DemoOrder.Error.InvalidRevision(NonNegativeInt.Error.Negative(-1)),
            ))),
            result,
        )
    }

    @Test
    fun `constructs a checkout when both the nested order and customer are valid`() {
        val result: Either<NonEmptyList<DemoCheckout.Error>, DemoCheckout> = either {
            DemoCheckout.from(DemoCheckoutInput(
                order = DemoOrderInput(reference = "ORD-2", quantity = 2, revision = 3),
                customer = " Alice ",
            ))
        }
        val checkout = assertIs<Either.Right<DemoCheckout>>(result).value

        assertEquals("ORD-2", checkout.order.reference.value)
        assertEquals(2, checkout.order.quantity.value)
        assertEquals(3, checkout.order.revision.value)
        assertEquals(" Alice ", checkout.customer.value)
    }
}

private data class DemoOrderInput(val reference: String, val quantity: Int, val revision: Int)

/** A consuming domain owns its composite type, precise errors, and accumulated factory. */
private class DemoOrder private constructor(
    val reference: NonBlankString,
    val quantity: PositiveInt,
    val revision: NonNegativeInt,
) {
    sealed interface Error {
        data class InvalidReference(val cause: NonBlankString.Error) : Error
        data class InvalidQuantity(val cause: PositiveInt.Error) : Error
        data class InvalidRevision(val cause: NonNegativeInt.Error) : Error
    }

    companion object : ValueFactory<DemoOrderInput, NonEmptyList<Error>, DemoOrder> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: DemoOrderInput): DemoOrder = raise.zipOrAccumulate(
            { NonBlankString.from(value.reference, onError = Error::InvalidReference) },
            { PositiveInt.from(value.quantity, onError = Error::InvalidQuantity) },
            { NonNegativeInt.from(value.revision, onError = Error::InvalidRevision) },
        ) { reference, quantity, revision -> DemoOrder(reference, quantity, revision) }
    }
}

private data class DemoCheckoutInput(val order: DemoOrderInput, val customer: String)

/** The higher layer handles stable outcomes while retaining the nested error details. */
private class DemoCheckout private constructor(val order: DemoOrder, val customer: NonBlankString) {
    sealed interface Error {
        data class InvalidOrder(val causes: NonEmptyList<DemoOrder.Error>) : Error
        data class InvalidCustomer(val cause: NonBlankString.Error) : Error
    }

    companion object : ValueFactory<DemoCheckoutInput, NonEmptyList<Error>, DemoCheckout> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: DemoCheckoutInput): DemoCheckout = raise.zipOrAccumulate(
            { DemoOrder.from(value.order, onError = Error::InvalidOrder) },
            { NonBlankString.from(value.customer, onError = Error::InvalidCustomer) },
        ) { order, customer -> DemoCheckout(order, customer) }
    }
}
