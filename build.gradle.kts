plugins {
  kotlin("multiplatform") version "2.4.21"
  kotlin("plugin.serialization") version "2.4.21" apply false
  id("dev.detekt") version "2.0.0-alpha.6"
  id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

// Lint and coverage apply to every module, so a new module is checked without extra setup.
allprojects {
  apply(plugin = "dev.detekt")
  apply(plugin = "org.jetbrains.kotlinx.kover")

  detekt {
    // Start from detekt's defaults; config/detekt/detekt.yml records only this repository's changes.
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel = true
    // `./gradlew detekt -PdetektAutoCorrect` applies ktlint's formatting fixes.
    autoCorrect = providers.gradleProperty("detektAutoCorrect").isPresent
  }

  // In a multiplatform module the plain detekt task has no sources of its own; make it
  // analyse every source set, so `./gradlew detekt` checks everything.
  tasks.named("detekt") {
    dependsOn(tasks.withType<dev.detekt.gradle.Detekt>().matching { it.name.endsWith("SourceSet") })
  }

  dependencies {
    // ktlint's formatting rules, run by detekt.
    "detektPlugins"("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.6")
  }
}

// One coverage report for the whole repository, measured by the JVM tests.
dependencies {
  subprojects.forEach { kover(it) }
}

kover {
  reports {
    verify {
      // `./gradlew koverVerify` fails when line coverage across all modules drops below this.
      rule {
        minBound(90)
      }
    }
  }
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
      // The first time zone lookup loads the whole js-joda database, which can exceed Mocha's 2s default.
      testTask { useMocha { timeout = "10s" } }
    }
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
    jsMain.dependencies {
      // Time zone data for TimeZoneId; 2.23.0 is the newest release compatible with
      // the @js-joda/core version kotlinx-datetime ships.
      implementation(npm("@js-joda/timezone", "2.23.0"))
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
