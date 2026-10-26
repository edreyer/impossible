// Toolchain, API checks, group and version for a JVM-only module of the library, such as
// a framework adapter. Shares its values with impossible.kotlin-library through ImpossibleBuild.

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  kotlin("jvm")
}

group = ImpossibleBuild.GROUP
version = ImpossibleBuild.VERSION

repositories {
  mavenCentral()
}

kotlin {
  explicitApi()
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation()
  // Compiling and testing with JDK 17 emits Java 17 bytecode and runs the tests on that floor.
  jvmToolchain(ImpossibleBuild.JVM_TOOLCHAIN)
}

dependencies {
  "testImplementation"(kotlin("test"))
}

tasks.named<Test>("test") {
  useJUnitPlatform()
}

// `./gradlew allTests`, which CI runs, covers this module too, as it does the multiplatform ones.
tasks.register("allTests") {
  group = "verification"
  description = "Runs the tests of this JVM module."
  dependsOn("test")
}
