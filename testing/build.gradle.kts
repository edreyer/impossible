plugins {
  id("impossible.kotlin-library")
}

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
