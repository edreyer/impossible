plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "impossible"

// Published property-testing generators (kotest Arbs) for the library's types.
include(":impossible-testing")
project(":impossible-testing").projectDir = file("testing")
