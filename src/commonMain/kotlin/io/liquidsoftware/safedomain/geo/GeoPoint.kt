package io.liquidsoftware.safedomain.geo

import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.liquidsoftware.safedomain.ValueFactory
import io.liquidsoftware.safedomain.field
import io.liquidsoftware.safedomain.validate

/**
 * A WGS 84 position in decimal degrees: [latitude] in -90..90 and [longitude] in
 * -180..180, both inclusive and finite. Values are not wrapped or clamped, so -180 and
 * 180 are kept as given even though they name the same meridian. Equality uses numeric
 * value, so 0.0 equals -0.0.
 */
public class GeoPoint private constructor(public val latitude: Double, public val longitude: Double) {
    public data class Input(val latitude: Double, val longitude: Double)

    public sealed interface Error {
        public data class LatitudeOutOfRange(val value: Double) : Error
        public data class LongitudeOutOfRange(val value: Double) : Error
    }

    override fun equals(other: Any?): Boolean =
        other is GeoPoint && latitude == other.latitude && longitude == other.longitude

    // Adding 0.0 turns -0.0 into 0.0, matching equals.
    override fun hashCode(): Int = 31 * (latitude + 0.0).hashCode() + (longitude + 0.0).hashCode()

    override fun toString(): String = "GeoPoint(latitude=$latitude, longitude=$longitude)"

    /** Checks both coordinates independently and reports every failure. */
    public companion object : ValueFactory<Input, NonEmptyList<Error>, GeoPoint> {
        context(raise: Raise<NonEmptyList<Error>>)
        override fun from(value: Input): GeoPoint = validate {
            val latitude by field {
                ensure(value.latitude in -90.0..90.0) { Error.LatitudeOutOfRange(value.latitude) }
                value.latitude
            }
            val longitude by field {
                ensure(value.longitude in -180.0..180.0) { Error.LongitudeOutOfRange(value.longitude) }
                value.longitude
            }
            GeoPoint(latitude, longitude)
        }
    }
}
