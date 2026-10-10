package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.withError
import io.liquidsoftware.safedomain.contact.Email
import io.liquidsoftware.safedomain.contact.Phone
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.time.DateRange
import io.liquidsoftware.safedomain.time.FutureDate
import io.liquidsoftware.safedomain.time.PastDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class BusinessValueCompositionTest {
    private val today = LocalDate(2026, 10, 5)
    private val valid = BookingInput(
        FutureDate.Input(today.plus(DatePeriod(days = 1)), today),
        PastDate.Input(today.minus(DatePeriod(days = 1)), today),
        DateRange.Input(today, today.plus(DatePeriod(days = 2))),
        Money.Input("12.3400", "EUR"),
        "Alice@example.com",
        "+34612345678",
    )

    @Test
    fun `composite collects all field failures including a complete nested money batch`() {
        val result: Either<NonEmptyList<Booking.Error>, Booking> = either {
            Booking.from(valid.copy(
                arrival = FutureDate.Input(today, today),
                ordered = PastDate.Input(today, today),
                period = DateRange.Input(today, today.minus(DatePeriod(days = 1))),
                price = Money.Input("oops", "ZZZ"),
                email = "invalid",
                phone = "123",
            ))
        }
        assertEquals(Either.Left(nonEmptyListOf(
            Booking.Error.InvalidArrival(FutureDate.Error.NotFuture(today, today)),
            Booking.Error.InvalidOrderDate(PastDate.Error.NotPast(today, today)),
            Booking.Error.InvalidPeriod(DateRange.Error.EndBeforeStart(today, today.minus(DatePeriod(days = 1)))),
            Booking.Error.InvalidPrice(nonEmptyListOf(
                Money.Error.InvalidAmount("oops"),
                Money.Error.InvalidCurrency(Currency.Error.UnsupportedCode("ZZZ")),
            )),
            Booking.Error.InvalidEmail(Email.Error.InvalidFormat("invalid")),
            Booking.Error.InvalidPhone(Phone.Error.InvalidFormat("123")),
        )), result)
    }

    @Test
    fun `cross field range failure accumulates alongside an unrelated contact failure`() {
        val result: Either<NonEmptyList<Booking.Error>, Booking> = either {
            Booking.from(valid.copy(period = DateRange.Input(today, today.minus(DatePeriod(days = 1))), email = "invalid"))
        }
        assertEquals(Either.Left(nonEmptyListOf(
            Booking.Error.InvalidPeriod(DateRange.Error.EndBeforeStart(today, today.minus(DatePeriod(days = 1)))),
            Booking.Error.InvalidEmail(Email.Error.InvalidFormat("invalid")),
        )), result)
    }

    @Test
    fun `valid composite stores the typed values without losing precision or spelling`() {
        val booking = assertIs<Either.Right<Booking>>(either { Booking.from(valid) }).value
        assertEquals(today.plus(DatePeriod(days = 1)), booking.arrival.value)
        assertEquals(today.minus(DatePeriod(days = 1)), booking.ordered.value)
        assertEquals(today, booking.period.start)
        assertEquals(Decimal.parse("12.3400"), booking.price.amount)
        assertEquals("EUR", booking.price.currency.code)
        assertEquals("Alice@example.com", booking.email.value)
        assertEquals("+34612345678", booking.phone.value)
    }

    @Test
    fun `a consuming domain maps money construction and arithmetic to its own closed outcomes`() {
        context(raise: Raise<ChargeError>)
        fun charge(input: Money.Input, other: Money.Input): Money {
            val amount = Money.from(input, onError = ChargeError::InvalidInput)
            val additional = Money.from(other, onError = ChargeError::InvalidInput)
            return raise.withError(ChargeError::CalculationRejected) { amount.add(additional) }
        }

        val result: Either<ChargeError, Money> = either { charge(Money.Input("1", "USD"), Money.Input("2", "EUR")) }
        val failure = assertIs<Either.Left<ChargeError>>(result).value
        val calculation = assertIs<ChargeError.CalculationRejected>(failure)
        val mismatch = assertIs<Money.ArithmeticError.CurrencyMismatch>(calculation.cause)
        assertEquals("USD", mismatch.expected.code)
        assertEquals("EUR", mismatch.actual.code)
    }

    private sealed interface ChargeError {
        data class InvalidInput(val causes: NonEmptyList<Money.Error>) : ChargeError
        data class CalculationRejected(val cause: Money.ArithmeticError) : ChargeError
    }
}

private data class BookingInput(
    val arrival: FutureDate.Input,
    val ordered: PastDate.Input,
    val period: DateRange.Input,
    val price: Money.Input,
    val email: String,
    val phone: String,
)

private class Booking private constructor(
    val arrival: FutureDate,
    val ordered: PastDate,
    val period: DateRange,
    val price: Money,
    val email: Email,
    val phone: Phone,
) {
    sealed interface Error {
        data class InvalidArrival(val cause: FutureDate.Error) : Error
        data class InvalidOrderDate(val cause: PastDate.Error) : Error
        data class InvalidPeriod(val cause: DateRange.Error) : Error
        data class InvalidPrice(val causes: NonEmptyList<Money.Error>) : Error
        data class InvalidEmail(val cause: Email.Error) : Error
        data class InvalidPhone(val cause: Phone.Error) : Error
    }

    companion object : ValueFactory<BookingInput, NonEmptyList<Error>, Booking> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: BookingInput): Booking = validate {
            val arrival by FutureDate.field(value.arrival, Error::InvalidArrival)
            val ordered by PastDate.field(value.ordered, Error::InvalidOrderDate)
            val period by DateRange.field(value.period, Error::InvalidPeriod)
            val price by Money.field(value.price, Error::InvalidPrice)
            val email by Email.field(value.email, Error::InvalidEmail)
            val phone by Phone.field(value.phone, Error::InvalidPhone)
            Booking(arrival, ordered, period, price, email, phone)
        }
    }
}
