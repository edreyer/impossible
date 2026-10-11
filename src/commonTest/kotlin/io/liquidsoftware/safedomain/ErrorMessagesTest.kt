package io.liquidsoftware.safedomain

import io.liquidsoftware.safedomain.collections.BoundedList
import io.liquidsoftware.safedomain.money.Currency
import io.liquidsoftware.safedomain.money.Money
import io.liquidsoftware.safedomain.numeric.BoundedInt
import io.liquidsoftware.safedomain.numeric.Decimal
import io.liquidsoftware.safedomain.numeric.PositiveInt
import io.liquidsoftware.safedomain.text.NonBlankString
import io.liquidsoftware.safedomain.web.HttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorMessagesTest {
    @Test
    fun `messages carry a stable code and English text and the values a translation needs`() {
        assertEquals(
            ErrorMessage(
                "non_blank_string.too_long",
                "Use at most 5 characters; this has 7.",
                mapOf("maximum" to "5", "actual" to "7"),
            ),
            ErrorMessages.describe(NonBlankString.Error.TooLong(5, 7)),
        )
        assertEquals(
            ErrorMessage("bounded_int.out_of_range", "Enter a whole number from 1 to 5.", mapOf("minimum" to "1", "maximum" to "5")),
            ErrorMessages.describe(BoundedInt.Error.OutOfRange(9, 1..5)),
        )
        assertEquals(
            "Add at least 1 item; there are 0.",
            ErrorMessages.describe(BoundedList.Error.TooSmall(1, 0)).text,
        )
    }

    @Test
    fun `money messages follow the currency's minor unit`() {
        val usd = Currency.fromTrusted("USD")
        val jpy = Currency.fromTrusted("JPY")
        assertEquals(
            "Enter an amount with at most 2 decimal places.",
            ErrorMessages.describe(Money.Error.NotInMinorUnits(Decimal.parse("1.234"), 2)).text,
        )
        assertEquals(
            "Enter a whole amount, without decimal places.",
            ErrorMessages.describe(Money.Error.NotInMinorUnits(Decimal.parse("1.5"), 0)).text,
        )
        assertEquals(
            ErrorMessages.describe(Currency.Error.UnsupportedCode("ZZZ")),
            ErrorMessages.describe(Money.Error.InvalidCurrency(Currency.Error.UnsupportedCode("ZZZ"))),
        )
        assertEquals(
            mapOf("expected" to "USD", "actual" to "JPY"),
            ErrorMessages.describe(Money.ArithmeticError.CurrencyMismatch(usd, jpy)).arguments,
        )
    }

    @Test
    fun `required fields describe absence and delegate invalid values`() {
        val describe = { error: PositiveInt.Error -> ErrorMessages.describe(error) }
        assertEquals("required.missing", ErrorMessages.describe(RequiredError.Missing, describe).code)
        assertEquals(
            "positive_int.non_positive",
            ErrorMessages.describe(RequiredError.Invalid(PositiveInt.Error.NonPositive(0)), describe).code,
        )
    }

    @Test
    fun `messages never quote free text input`() {
        val scheme = "javascript"
        val message = ErrorMessages.describe(HttpUrl.Error.UnsupportedScheme(scheme))
        assertEquals(false, scheme in message.text || scheme in message.arguments.values)
    }
}
