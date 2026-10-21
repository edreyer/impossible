plugins {
  id("impossible.kotlin-library")
  alias(libs.plugins.detekt)
  alias(libs.plugins.kover)
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
    "detektPlugins"(rootProject.libs.detekt.rules.ktlint.wrapper)
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

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(libs.arrow.core)
      api(libs.kotlinx.datetime)
      implementation(libs.bignum)
    }
    jsMain.dependencies {
      // Time zone data for TimeZoneId; 2.23.0 is the newest release compatible with
      // the @js-joda/core version kotlinx-datetime ships.
      implementation(npm("@js-joda/timezone", "2.23.0"))
    }
    wasmJsMain.dependencies {
      // The same time zone data for TimeZoneId on Wasm.
      implementation(npm("@js-joda/timezone", "2.23.0"))
    }
  }
}
