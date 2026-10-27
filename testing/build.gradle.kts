plugins {
  id("impossible.kotlin-library")
}

// The POM description on Maven Central.
description = "kotest property-test generators for the impossible value types."

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":"))
      api(libs.kotest.property)
    }
    commonTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
