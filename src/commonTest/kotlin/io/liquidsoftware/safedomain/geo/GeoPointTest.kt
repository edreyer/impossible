package io.liquidsoftware.safedomain.geo

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoPointTest {
    private fun point(latitude: Double, longitude: Double) =
        either<NonEmptyList<GeoPoint.Error>, GeoPoint> { GeoPoint.from(GeoPoint.Input(latitude, longitude)) }

    @Test
    fun `coordinates are inclusive ranges`() {
        for ((latitude, longitude) in listOf(0.0 to 0.0, 90.0 to 180.0, -90.0 to -180.0, 52.52 to 13.405)) {
            assertTrue(point(latitude, longitude).isRight())
        }
        assertEquals(52.52, point(52.52, 13.405).getOrNull()!!.latitude)
    }

    @Test
    fun `both coordinates are checked and reported together`() {
        assertEquals(
            Either.Left(nonEmptyListOf(GeoPoint.Error.LatitudeOutOfRange(90.5), GeoPoint.Error.LongitudeOutOfRange(-181.0))),
            point(90.5, -181.0),
        )
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertTrue(point(bad, 0.0).isLeft())
            assertTrue(point(0.0, bad).isLeft())
        }
    }

    @Test
    fun `negative zero equals zero`() {
        assertEquals(point(0.0, 0.0), point(-0.0, -0.0))
        assertEquals(point(0.0, 0.0).getOrNull().hashCode(), point(-0.0, -0.0).getOrNull().hashCode())
    }
}
