// Build conventions shared by every module of the library, as precompiled script plugins.
dependencyResolutionManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
  versionCatalogs {
    // The same catalog the library uses, so plugin versions stay in one place.
    create("libs") { from(files("../gradle/libs.versions.toml")) }
  }
}

rootProject.name = "build-logic"
