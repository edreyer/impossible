plugins {
  kotlin("multiplatform")
}

group = "io.liquidsoftware"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

kotlin {
  jvmToolchain(26)

  jvm {
    testRuns.named("test") {
      executionTask.configure { useJUnitPlatform() }
    }
  }
  js {
    // Browser tests would need Karma and a local browser; Node runs the same tests.
    browser { testTask { enabled = false } }
    nodejs {
      // The first time zone lookup loads the whole js-joda database, and property tests
      // run many iterations, so allow more than Mocha's 2s default.
      testTask { useMocha { timeout = "60s" } }
    }
  }
  linuxX64()
  macosArm64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(project(":"))
      api("io.kotest:kotest-property:6.2.5")
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    }
  }
}
