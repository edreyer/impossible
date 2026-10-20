// Targets, toolchain and API checks shared by every module of the library. A module
// applies this plugin and adds only its own dependencies.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  kotlin("multiplatform")
}

group = "io.liquidsoftware"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

kotlin {
  explicitApi()
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation()
  jvmToolchain(26)

  jvm {
    compilerOptions {
      // Build with a current JDK, but emit bytecode and link against the API of JDK 17,
      // the oldest JDK consumers can run this library on.
      jvmTarget.set(JvmTarget.JVM_17)
      freeCompilerArgs.add("-Xjdk-release=17")
    }
    testRuns.named("test") {
      executionTask.configure { useJUnitPlatform() }
    }
  }
  js {
    // Browser tests would need Karma and a local browser; Node runs the same tests.
    browser { testTask { enabled = false } }
    nodejs {
      // The first time zone lookup loads the whole js-joda database, and the property and
      // law tests run many iterations, so allow more than Mocha's 2s default.
      testTask { useMocha { timeout = "60s" } }
    }
  }
  linuxX64()
  macosArm64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
