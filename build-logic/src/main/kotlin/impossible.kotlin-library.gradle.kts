// Targets, toolchain, API checks and publishing shared by every multiplatform module of the
// library. A module applies this plugin, sets its description and adds only its own dependencies.

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  kotlin("multiplatform")
  id("impossible.publishing")
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
  // JDK 17 is the oldest JDK consumers can run this library on. Compiling and testing
  // with it emits Java 17 bytecode, links only against the JDK 17 API, and runs the JVM
  // tests on that floor.
  jvmToolchain(ImpossibleBuild.JVM_TOOLCHAIN)

  jvm {
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
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    // As for js: Node runs the tests, and the time zone database needs the longer timeout.
    browser { testTask { enabled = false } }
    nodejs {
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
