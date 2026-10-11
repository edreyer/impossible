package io.liquidsoftware.safedomain.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.double
import io.liquidsoftware.safedomain.fromTrusted
import io.liquidsoftware.safedomain.geo.GeoPoint

/** Points anywhere on the globe, with the poles, the antimeridian, and -0.0 as edge cases. */
public fun GeoPoint.Companion.arb(): Arb<GeoPoint> {
    val latitudes = Arb.double(-90.0..90.0, includeNaNs = false)
    val longitudes = Arb.double(-180.0..180.0, includeNaNs = false)
    return arbitrary { GeoPoint.fromTrusted(GeoPoint.Input(latitudes.bind(), longitudes.bind())) }
}
