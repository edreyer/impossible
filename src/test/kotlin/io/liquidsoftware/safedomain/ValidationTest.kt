package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidationTest {
    @Test
    fun `reports every field failure in declaration order`() {
        val result: Either<NonEmptyList<Order.Error>, Order> = either {
            Order.from(OrderInput("", Money.Input("1", "USD"), listOf(LineInput("A", 1)), 0, 1))
        }
        assertEquals(Either.Left(nonEmptyListOf(Order.Error.InvalidCustomer(NonBlankString.Error.Blank))), result)
    }

    @Test
    fun `flatField maps each nested error into the parent list`() {
        val result: Either<NonEmptyList<Order.Error>, Order> = either {
            Order.from(OrderInput("Alice", Money.Input("x", "ZZZ"), listOf(LineInput("A", 1)), 0, 1))
        }
        assertEquals(Either.Left(nonEmptyListOf(
            Order.Error.InvalidPrice(Money.Error.InvalidAmount("x")),
            Order.Error.InvalidPrice(Money.Error.InvalidCurrency(Currency.Error.UnsupportedCode("ZZZ"))),
        )), result)
    }

    @Test
    fun `eachField reports every invalid element with its index`() {
        val result: Either<NonEmptyList<Order.Error>, Order> = either {
            Order.from(OrderInput("Alice", Money.Input("1", "USD"), listOf(LineInput("", 0), LineInput("B", 1), LineInput("C", -1)), 0, 1))
        }
        assertEquals(Either.Left(nonEmptyListOf(
            Order.Error.InvalidLine(0, nonEmptyListOf(
                Line.Error.InvalidSku(NonBlankString.Error.Blank),
                Line.Error.InvalidQuantity(PositiveInt.Error.NonPositive(0)),
            )),
            Order.Error.InvalidLine(2, nonEmptyListOf(Line.Error.InvalidQuantity(PositiveInt.Error.NonPositive(-1)))),
        )), result)
    }

    @Test
    fun `a dependent field block stops on its own failure while later fields still run`() {
        val result: Either<NonEmptyList<Order.Error>, Order> = either {
            Order.from(OrderInput("", Money.Input("1", "USD"), listOf(LineInput("A", 1)), 5, -1))
        }
        assertEquals(Either.Left(nonEmptyListOf(
            Order.Error.InvalidCustomer(NonBlankString.Error.Blank),
            Order.Error.InvalidMaximum(PositiveInt.Error.NonPositive(-1)),
        )), result)
    }

    @Test
    fun `a cross field check runs once its inputs are valid`() {
        val result: Either<NonEmptyList<Order.Error>, Order> = either {
            Order.from(OrderInput("", Money.Input("1", "USD"), listOf(LineInput("A", 1)), 5, 2))
        }
        assertEquals(Either.Left(nonEmptyListOf(
            Order.Error.InvalidCustomer(NonBlankString.Error.Blank),
            Order.Error.MaximumBelowMinimum(5, 2),
        )), result)
    }

    @Test
    fun `reading a failed field before the block ends raises everything collected so far`() {
        val result: Either<NonEmptyList<String>, Int> = either {
            validate {
                val first by PositiveInt.field(0) { "first" }
                val second by PositiveInt.field(0) { "second" }
                val early = first.value
                val third by PositiveInt.field(0) { "third" }
                early + second.value + third.value
            }
        }
        assertEquals(Either.Left(nonEmptyListOf("first", "second")), result)
    }

    @Test
    fun `builds the composite when every field is valid`() {
        val order = assertIs<Either.Right<Order>>(either {
            Order.from(OrderInput(" Alice ", Money.Input("9.990", "USD"), listOf(LineInput("A", 2), LineInput("B", 1)), 1, 3))
        }).value
        assertEquals(" Alice ", order.customer.value)
        assertEquals("9.990", order.price.amount.toPlainString())
        assertEquals(listOf("A", "B"), order.lines.map { it.sku.value })
        assertEquals(1 to 3, order.minimum.value to order.maximum.value)
    }

    @Test
    fun `has no field count limit`() {
        val inputs = List(12) { it - 1 }
        val result: Either<NonEmptyList<Int>, List<PositiveInt>> = either {
            validate {
                val values = inputs.mapIndexed { index, raw -> PositiveInt.field(raw) { index } }
                values.map { it.value }
            }
        }
        assertEquals(Either.Left(nonEmptyListOf(0, 1)), result)
    }
}

private data class LineInput(val sku: String, val quantity: Int)

private class Line private constructor(val sku: NonBlankString, val quantity: PositiveInt) {
    sealed interface Error {
        data class InvalidSku(val cause: NonBlankString.Error) : Error
        data class InvalidQuantity(val cause: PositiveInt.Error) : Error
    }

    companion object : ValueFactory<LineInput, NonEmptyList<Error>, Line> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: LineInput): Line = validate {
            val sku by NonBlankString.field(value.sku, Error::InvalidSku)
            val quantity by PositiveInt.field(value.quantity, Error::InvalidQuantity)
            Line(sku, quantity)
        }
    }
}

private data class OrderInput(
    val customer: String,
    val price: Money.Input,
    val lines: List<LineInput>,
    val minimum: Int,
    val maximum: Int,
)

private class Order private constructor(
    val customer: NonBlankString,
    val price: Money,
    val lines: List<Line>,
    val minimum: NonNegativeInt,
    val maximum: PositiveInt,
) {
    sealed interface Error {
        data class InvalidCustomer(val cause: NonBlankString.Error) : Error
        data class InvalidPrice(val cause: Money.Error) : Error
        data class InvalidLine(val index: Int, val causes: NonEmptyList<Line.Error>) : Error
        data class InvalidMinimum(val cause: NonNegativeInt.Error) : Error
        data class InvalidMaximum(val cause: PositiveInt.Error) : Error
        data class MaximumBelowMinimum(val minimum: Int, val maximum: Int) : Error
    }

    companion object : ValueFactory<OrderInput, NonEmptyList<Error>, Order> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: OrderInput): Order = validate {
            val customer by NonBlankString.field(value.customer, Error::InvalidCustomer)
            val price by Money.flatField(value.price, Error::InvalidPrice)
            val lines by Line.eachField(value.lines, Error::InvalidLine)
            val limits by field {
                val minimum by NonNegativeInt.field(value.minimum, Error::InvalidMinimum)
                val maximum by PositiveInt.field(value.maximum, Error::InvalidMaximum)
                ensure(maximum.value >= minimum.value) {
                    Error.MaximumBelowMinimum(minimum.value, maximum.value)
                }
                minimum to maximum
            }
            Order(customer, price, lines, limits.first, limits.second)
        }
    }
}
