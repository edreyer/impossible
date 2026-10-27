plugins {
  id("impossible.jvm-library")
}

// The POM description on Maven Central.
description = "Jackson (de)serializers that decode through the impossible validating factories."

dependencies {
  api(project(":"))
  api(libs.jackson.databind)

  // The cross-check compares this module's JSON with impossible-serialization's, over the
  // generators of impossible-testing.
  testImplementation(project(":impossible-serialization"))
  testImplementation(project(":impossible-testing"))
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.kotlinx.coroutines.test)
  // Only to show Kotlin data classes with value-class properties; the module itself
  // needs no Kotlin module and works for Java callers.
  testImplementation(libs.jackson.module.kotlin)
}
