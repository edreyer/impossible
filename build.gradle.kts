plugins {
  kotlin("multiplatform") version "2.4.20"
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
    nodejs()
  }
  linuxX64()
  macosArm64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api("io.arrow-kt:arrow-core:2.2.3")
      api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
      implementation("com.ionspin.kotlin:bignum:0.3.10")
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
