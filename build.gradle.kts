import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  java
  application
  `java-library`
  `maven-publish`

  alias(libs.plugins.kotlin.jvm) apply true
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlinter) apply true
  alias(libs.plugins.versions) apply true
  alias(libs.plugins.buildconfig) apply false
  // id("org.jetbrains.kotlinx.kover") version "0.5.0"
}

val versionStr: String by extra
val kotlinLib = libs.plugins.kotlin.jvm.get().toString().split(":").first()
val serializationLib = libs.plugins.kotlin.serialization.get().toString().split(":").first()
val ktlinterLib = libs.plugins.kotlinter.get().toString().split(":").first()

// These are for the uber target
val mainName = "TestMain"

application {
  mainClass = mainName
}

allprojects {
  apply(plugin = "application")
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")
  apply(plugin = "org.jmailen.kotlinter")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
  apply(plugin = "com.github.gmazzo.buildconfig")
  apply(plugin = "com.github.ben-manes.versions")

  extra["versionStr"] = "3.0.2"
  description = "ReadingBat Core"
  group = "com.github.readingbat"
  version = versionStr

  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }

  configureVersions()
}

subprojects {
  configureKotlin()
  configurePublishing()
  configureTesting()
  configureKotlinter()
  configureSecrets()
  configureVersions()
}

fun Project.configureKotlin() {
  apply {
    plugin(kotlinLib)
  }

  kotlin {
    jvmToolchain(17)

    sourceSets.all {
      listOf(
        "kotlin.ExperimentalStdlibApi",
        "kotlin.concurrent.atomics.ExperimentalAtomicApi",
        "kotlin.contracts.ExperimentalContracts",
        "kotlin.time.ExperimentalTime",
        "kotlinx.coroutines.DelicateCoroutinesApi",
        "kotlinx.coroutines.ExperimentalCoroutinesApi",
        "kotlinx.coroutines.InternalCoroutinesApi",
        "kotlinx.coroutines.ObsoleteCoroutinesApi",
      ).forEach {
        languageSettings.optIn(it)
      }
    }
  }

  tasks.named("build") {
    mustRunAfter("clean")
  }

  tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }
}

fun Project.configurePublishing() {
  apply {
    plugin("java-library")
    plugin("maven-publish")
  }

  publishing {
    val versionStr: String by extra
    publications {
      create<MavenPublication>("maven") {
        groupId = group.toString()
        artifactId = project.name
        version = versionStr
        from(components["java"])
      }
    }
  }

  java {
    withSourcesJar()
  }
}

fun Project.configureKotlinter() {
  apply {
    plugin(ktlinterLib)
  }

  kotlinter {
    ignoreFormatFailures = false
    ignoreLintFailures = false
    reporters = arrayOf("checkstyle", "plain")
  }
}

fun Project.configureTesting() {
  tasks.test {
    useJUnitPlatform()

    // Docker Desktop 4.x+ requires API version >= 1.44, but docker-java defaults to 1.32
    val dockerApiVersion = "1.44"
    environment("DOCKER_API_VERSION", dockerApiVersion)
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    jvmArgs("-Dapi.version=$dockerApiVersion")

    testLogging {
      events("passed", "skipped", "failed", "standardOut", "standardError")
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = true
    }
  }
}

configurations.all {
  resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

fun Project.configureVersions() {
  fun isNonStable(version: String): Boolean =
    listOf("-RC", "-BETA", "-ALPHA", "-M").any { version.uppercase().contains(it) }

  tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
      isNonStable(candidate.version)
    }
  }
}

fun Project.configureSecrets() {
  val secretsFile = file("secrets/secrets.env")
  if (secretsFile.exists()) {
    val envVars =
      secretsFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
          val idx = line.indexOf('=')
          if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim().removeSurrounding("\"") else null
        }
        .toMap()

    tasks.withType<JavaExec>().configureEach { environment(envVars) }
    tasks.withType<Test>().configureEach { environment(envVars) }
  }
}
