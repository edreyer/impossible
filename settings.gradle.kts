pluginManagement {
  // Shared build conventions (targets, toolchain, API checks) for every module.
  includeBuild("build-logic")
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "impossible"

// Published property-testing generators (kotest Arbs) for the library's types.
include(":impossible-testing")
project(":impossible-testing").projectDir = file("testing")

// kotlinx.serialization serializers that decode through the validating factories.
include(":impossible-serialization")
project(":impossible-serialization").projectDir = file("serialization")

// Jackson (de)serializers that decode through the validating factories; JVM only.
include(":impossible-jackson")
project(":impossible-jackson").projectDir = file("jackson")
