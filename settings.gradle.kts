pluginManagement {
  // Shared build conventions (targets, toolchain, API checks) for every module.
  includeBuild("build-logic")
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
  // Uploads every module's publications to Maven Central as one deployment (see RELEASING.md).
  id("com.gradleup.nmcp.settings") version "1.6.2"
}
rootProject.name = "impossible"

nmcpSettings {
  centralPortal {
    username = providers.gradleProperty("centralPortalUsername")
    password = providers.gradleProperty("centralPortalPassword")
    // Pass -PcentralPortalPublishingType=USER_MANAGED to upload and validate without releasing.
    publishingType = providers.gradleProperty("centralPortalPublishingType").orElse("AUTOMATIC")
  }
}

// Published property-testing generators (kotest Arbs) for the library's types.
include(":impossible-testing")
project(":impossible-testing").projectDir = file("testing")

// kotlinx.serialization serializers that decode through the validating factories.
include(":impossible-serialization")
project(":impossible-serialization").projectDir = file("serialization")
