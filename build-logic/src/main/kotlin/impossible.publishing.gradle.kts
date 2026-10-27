// Maven Central metadata, Dokka javadoc jars and signing for every publication of a module.
// impossible.kotlin-library and impossible.jvm-library apply it; each module sets its description.

plugins {
  id("org.jetbrains.dokka")
  `maven-publish`
  signing
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
