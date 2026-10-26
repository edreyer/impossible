// Targets, toolchain, API checks and publishing shared by every module of the library.
// A module applies this plugin, sets its description and adds only its own dependencies.

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  `maven-publish`
  signing
}

group = "io.liquidsoftware"
version = "0.1.0"

repositories {
  mavenCentral()
}

kotlin {
  explicitApi()
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation()
  // JDK 17 is the oldest JDK consumers can run this library on. Compiling and testing
  // with it emits Java 17 bytecode, links only against the JDK 17 API, and runs the JVM
  // tests on that floor.
  jvmToolchain(17)

  jvm {
    testRuns.named("test") {
      executionTask.configure { useJUnitPlatform() }
    }
  }
  js {
    // Browser tests would need Karma and a local browser; Node runs the same tests.
    browser { testTask { enabled = false } }
    nodejs {
      // The first time zone lookup loads the whole js-joda database, and the property and
      // law tests run many iterations, so allow more than Mocha's 2s default.
      testTask { useMocha { timeout = "60s" } }
    }
  }
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    // As for js: Node runs the tests, and the time zone database needs the longer timeout.
    browser { testTask { enabled = false } }
    nodejs {
      testTask { useMocha { timeout = "60s" } }
    }
  }
  linuxX64()
  macosArm64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

// Maven Central requires a javadoc jar beside every published artifact. Each publication
// gets its own copy of the Dokka HTML docs, so the signing tasks never share an output.
val dokkaHtml = tasks.named("dokkaGeneratePublicationHtml")

publishing {
  publications.withType<MavenPublication>().configureEach {
    val publicationName = name
    val javadocJar = tasks.register<Jar>("${publicationName}JavadocJar") {
      archiveClassifier.set("javadoc")
      archiveAppendix.set(publicationName)
      from(dokkaHtml)
    }
    artifact(javadocJar)

    pom {
      name.set(project.name)
      description.set(provider { project.description })
      url.set("https://github.com/edreyer/impossible")
      licenses {
        license {
          name.set("The Apache License, Version 2.0")
          url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
      }
      developers {
        developer {
          name.set("Erik Dreyer")
        }
      }
      scm {
        url.set("https://github.com/edreyer/impossible")
        connection.set("scm:git:git://github.com/edreyer/impossible.git")
        developerConnection.set("scm:git:ssh://github.com/edreyer/impossible.git")
      }
    }
  }
}

signing {
  // Only Maven Central needs signatures, so publishToMavenLocal works without a GPG key.
  setRequired {
    gradle.taskGraph.allTasks.any { it.name.contains("CentralPortal") || it.name.contains("Nmcp") }
  }
  sign(publishing.publications)
}

// Every Kotlin Multiplatform publication shares the metadata artifacts, so a publish task
// reads signatures made by other publications' signing tasks.
tasks.withType<AbstractPublishToMaven>().configureEach {
  mustRunAfter(tasks.withType<Sign>())
}
