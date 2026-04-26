pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    if (providers.gradleProperty("useMavenLocal").orNull == "true") {
      mavenLocal()
    }
  }
}

rootProject.name = "readingbat"

include(":readingbat-core")
include(":readingbat-kotest")
