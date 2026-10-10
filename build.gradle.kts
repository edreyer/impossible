plugins {
  `java-library`
  kotlin("jvm") version "2.4.20"
}

group = "io.liquidsoftware"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  api("io.arrow-kt:arrow-core:2.2.3")
  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(26)
}

tasks.test {
  useJUnitPlatform()
}
