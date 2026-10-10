package io.liquidsoftware.safedomain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DecimalAndIdentityCompositionTest {
    @Test
    fun `parent accumulates precise failures from decimals percentages identifiers and URLs`() {
        val result: Either<NonEmptyList<Offer.Error>, Offer> = either {
            Offer.from(OfferInput(BigDecimal.ZERO, BigDecimal("-1"), BigDecimal("101"), "invalid", "relative"))
        }
        assertEquals(Either.Left(nonEmptyListOf(
            Offer.Error.InvalidQuantity(PositiveDecimal.Error.NonPositive(BigDecimal.ZERO)),
            Offer.Error.InvalidInventory(NonNegativeDecimal.Error.Negative(BigDecimal("-1"))),
            Offer.Error.InvalidDiscount(Percentage.Error.OutOfRange(BigDecimal("101"))),
            Offer.Error.InvalidCustomer(TypedId.Error.InvalidFormat("invalid")),
            Offer.Error.InvalidEndpoint(HttpUrl.Error.NotAbsolute),
        )), result)
    }

    @Test
    fun `parent constructs all typed fields without dropping decimal precision or endpoint spelling`() {
        val rawId = "550e8400-e29b-41d4-a716-446655440000"
        val result = either {
            Offer.from(OfferInput(BigDecimal("2.500"), BigDecimal("10.00"), BigDecimal("12.50"), rawId, "HTTPS://Example.com/offers"))
        }
        val offer = assertIs<Either.Right<Offer>>(result).value
        assertEquals(BigDecimal("2.500"), offer.quantity.value)
        assertEquals(BigDecimal("10.00"), offer.inventory.value)
        assertEquals(BigDecimal("12.50"), offer.discount.value)
        assertEquals(rawId, offer.customer.value.toString())
        assertEquals("HTTPS://Example.com/offers", offer.endpoint.value.toString())
    }
}

private interface CustomerTag

private data class OfferInput(
    val quantity: BigDecimal,
    val inventory: BigDecimal,
    val discount: BigDecimal,
    val customer: String,
    val endpoint: String,
)

private class Offer private constructor(
    val quantity: PositiveDecimal,
    val inventory: NonNegativeDecimal,
    val discount: Percentage,
    val customer: TypedId<CustomerTag>,
    val endpoint: HttpUrl,
) {
    sealed interface Error {
        data class InvalidQuantity(val cause: PositiveDecimal.Error) : Error
        data class InvalidInventory(val cause: NonNegativeDecimal.Error) : Error
        data class InvalidDiscount(val cause: Percentage.Error) : Error
        data class InvalidCustomer(val cause: TypedId.Error) : Error
        data class InvalidEndpoint(val cause: HttpUrl.Error) : Error
    }

    companion object : ValueFactory<OfferInput, NonEmptyList<Error>, Offer> {
        private val customerIds = TypedId.factory<CustomerTag>()

        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: OfferInput): Offer = raise.zipOrAccumulate(
            { PositiveDecimal.from(value.quantity, onError = Error::InvalidQuantity) },
            { NonNegativeDecimal.from(value.inventory, onError = Error::InvalidInventory) },
            { Percentage.from(value.discount, onError = Error::InvalidDiscount) },
            { customerIds.from(value.customer, onError = Error::InvalidCustomer) },
            { HttpUrl.from(value.endpoint, onError = Error::InvalidEndpoint) },
        ) { quantity, inventory, discount, customer, endpoint -> Offer(quantity, inventory, discount, customer, endpoint) }
    }
}
