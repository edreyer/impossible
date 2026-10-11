plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "safe-domain"

// Published property-testing generators (kotest Arbs) for the library's types.
include(":safe-domain-testing")
project(":safe-domain-testing").projectDir = file("testing")
