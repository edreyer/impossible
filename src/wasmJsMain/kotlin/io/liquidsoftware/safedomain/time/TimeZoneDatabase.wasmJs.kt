package io.liquidsoftware.safedomain.time

@JsModule("@js-joda/timezone")
private external object JsJodaTimeZoneModule

// Importing the module registers its zones with the js-joda core that kotlinx-datetime uses.
internal actual fun loadTimeZoneDatabase() {
    @Suppress("UNUSED_EXPRESSION")
    JsJodaTimeZoneModule
}
