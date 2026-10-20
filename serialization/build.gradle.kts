plugins {
  id("impossible.kotlin-library")
  kotlin("plugin.serialization")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":"))
      api(libs.kotlinx.serialization.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlinx.serialization.json)
      implementation(project(":impossible-testing"))
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
