package io.liquidsoftware.safedomain.readme

import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import io.liquidsoftware.safedomain.FieldError
import io.liquidsoftware.safedomain.ProblemDetails
import io.liquidsoftware.safedomain.ValidationError
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.fieldAt
import io.liquidsoftware.safedomain.flatFieldAt
import io.liquidsoftware.safedomain.fromEither
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.money.PositiveMoney
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.required
import io.liquidsoftware.safedomain.toProblemDetails
import io.liquidsoftware.safedomain.validate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Code for the opening of README.md. Keep the two in step: TutorialSnippetsTest checks
// that every README snippet marked <!-- checked --> appears here.

object Before {
    data class Order(val email: String, val quantity: Int, val total: BigDecimal) {
        init {
            require("@" in email) { "Invalid email: $email" }
            require(quantity > 0) { "Quantity must be positive" }
            require(total.signum() > 0) { "Total must be positive" }
        }
    }
}

data class OrderInput(val email: String?, val quantity: Int, val total: Money.Text)

data class Order(val email: Email, val quantity: PositiveInt, val total: PositiveMoney) {
    companion object : ValueFactory<OrderInput, NonEmptyList<FieldError<ValidationError>>, Order> {
        context(raise: Raise<NonEmptyList<FieldError<ValidationError>>>)
        override fun from(value: OrderInput): Order = validate {
            val email by Email.required().fieldAt(value.email)
            val quantity by PositiveInt.fieldAt(value.quantity)
            val amount by Money.parser.flatFieldAt(value.total, name = "total")
            val total by fieldAt { PositiveMoney.from(amount) }
            Order(email, quantity, total)
        }
    }
}

fun accept(input: OrderInput): String = Order.fromEither(input).fold(
    ifLeft = { failures -> render(failures.toProblemDetails()) }, // 422 application/problem+json
    ifRight = { order -> "accepted ${order.quantity.value}" },
)

private fun render(problem: ProblemDetails): String = problem.errors.joinToString { "${it.pointer} ${it.code}" }

class ReadmeLandingTest {
    @Test
    fun `the ad hoc version stops at the first problem and throws`() {
        val failure = assertFailsWith<IllegalArgumentException> { Before.Order("nope", 0, BigDecimal.ZERO) }
        assertEquals("Invalid email: nope", failure.message)
    }

    @Test
    fun `the impossible version reports every problem against its field`() {
        assertEquals(
            "/email email.invalid_format, /quantity positive_int.non_positive, /total positive_money.non_positive",
            accept(OrderInput("nope", 0, Money.Text("0.00", "EUR"))),
        )
        assertEquals("accepted 2", accept(OrderInput("ann@example.com", 2, Money.Text("12.50", "EUR"))))
    }
}
