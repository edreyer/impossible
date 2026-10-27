plugins {
  `kotlin-dsl`
}

dependencies {
  // The plugins the convention scripts apply, at the catalog's versions.
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.kotlin.serialization.gradle.plugin)
  implementation(libs.dokka.gradle.plugin)
}
