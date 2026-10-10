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
import io.liquidsoftware.safedomain.time.DateChecks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class BusinessValueCompositionTest {
    private val today = LocalDate(2026, 10, 5)
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-05T12:00:00Z")
    }
    private val valid = BookingInput(
        today.plus(DatePeriod(days = 1)),
        today.minus(DatePeriod(days = 1)),
        DateRange.Input(today, today.plus(DatePeriod(days = 2))),
        Money.Text("12.3400", "EUR"),
        "Alice@example.com",
        "+34612345678",
    )

    @Test
    fun `composite collects all field failures including a complete nested money batch`() {
        val result: Either<NonEmptyList<Booking.Error>, Booking> = book(valid.copy(
            arrival = today,
            ordered = today,
            period = DateRange.Input(today, today.minus(DatePeriod(days = 1))),
            price = Money.Text("oops", "ZZZ"),
            email = "invalid",
            phone = "123",
        ))
        assertEquals(Either.Left(nonEmptyListOf(
            Booking.Error.InvalidArrival(DateChecks.Error.NotFuture(today, today)),
            Booking.Error.InvalidOrderDate(DateChecks.Error.NotPast(today, today)),
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
        val result: Either<NonEmptyList<Booking.Error>, Booking> = book(valid.copy(period = DateRange.Input(today, today.minus(DatePeriod(days = 1))), email = "invalid"))
        assertEquals(Either.Left(nonEmptyListOf(
            Booking.Error.InvalidPeriod(DateRange.Error.EndBeforeStart(today, today.minus(DatePeriod(days = 1)))),
            Booking.Error.InvalidEmail(Email.Error.InvalidFormat("invalid")),
        )), result)
    }

    @Test
    fun `valid composite stores the typed values without losing precision or spelling`() {
        val booking = assertIs<Either.Right<Booking>>(book(valid)).value
        assertEquals(today.plus(DatePeriod(days = 1)), booking.arrival)
        assertEquals(today.minus(DatePeriod(days = 1)), booking.ordered)
        assertEquals(today, booking.period.start)
        assertEquals(Decimal.parse("12.3400"), booking.price.amount)
        assertEquals("EUR", booking.price.currency.code)
        assertEquals("Alice@example.com", booking.email.value)
        assertEquals("+34612345678", booking.phone.value)
    }

    @Test
    fun `a consuming domain maps money construction and arithmetic to its own closed outcomes`() {
        context(raise: Raise<ChargeError>)
        fun charge(input: Money.Text, other: Money.Text): Money {
            val amount = Money.parser.from(input, onError = ChargeError::InvalidInput)
            val additional = Money.parser.from(other, onError = ChargeError::InvalidInput)
            return raise.withError(ChargeError::CalculationRejected) { amount.add(additional) }
        }

        val result: Either<ChargeError, Money> = either { charge(Money.Text("1", "USD"), Money.Text("2", "EUR")) }
        val failure = assertIs<Either.Left<ChargeError>>(result).value
        val calculation = assertIs<ChargeError.CalculationRejected>(failure)
        val mismatch = assertIs<Money.ArithmeticError.CurrencyMismatch>(calculation.cause)
        assertEquals("USD", mismatch.expected.code)
        assertEquals("EUR", mismatch.actual.code)
    }

    private fun book(input: BookingInput): Either<NonEmptyList<Booking.Error>, Booking> =
        with(clock) { with(TimeZone.UTC) { either { Booking.from(input) } } }

    private sealed interface ChargeError {
        data class InvalidInput(val causes: NonEmptyList<Money.Error>) : ChargeError
        data class CalculationRejected(val cause: Money.ArithmeticError) : ChargeError
    }
}

private data class BookingInput(
    val arrival: LocalDate,
    val ordered: LocalDate,
    val period: DateRange.Input,
    val price: Money.Text,
    val email: String,
    val phone: String,
)

private class Booking private constructor(
    val arrival: LocalDate,
    val ordered: LocalDate,
    val period: DateRange,
    val price: Money,
    val email: Email,
    val phone: Phone,
) {
    sealed interface Error {
        data class InvalidArrival(val cause: DateChecks.Error.NotFuture) : Error
        data class InvalidOrderDate(val cause: DateChecks.Error.NotPast) : Error
        data class InvalidPeriod(val cause: DateRange.Error) : Error
        data class InvalidPrice(val causes: NonEmptyList<Money.Error>) : Error
        data class InvalidEmail(val cause: Email.Error) : Error
        data class InvalidPhone(val cause: Phone.Error) : Error
    }

    companion object {
        /** Arrival and order date are checked against today from the context clock. */
        context(clock: Clock, timeZone: TimeZone, raise: Raise<NonEmptyList<Error>>)
        fun from(value: BookingInput): Booking = validate {
            val arrival by DateChecks.future().field(value.arrival, Error::InvalidArrival)
            val ordered by DateChecks.past().field(value.ordered, Error::InvalidOrderDate)
            val period by DateRange.field(value.period, Error::InvalidPeriod)
            val price by Money.parser.field(value.price, Error::InvalidPrice)
            val email by Email.field(value.email, Error::InvalidEmail)
            val phone by Phone.field(value.phone, Error::InvalidPhone)
            Booking(arrival, ordered, period, price, email, phone)
        }
    }
}
