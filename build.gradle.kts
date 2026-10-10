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

  sourceSets {
    commonMain.dependencies {
      api("io.arrow-kt:arrow-core:2.2.3")
      api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
