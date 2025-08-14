import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  `java`
  `application`
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

allprojects {

  apply(plugin = "application")
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")
  apply(plugin = "org.jmailen.kotlinter")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
  apply(plugin = "com.github.gmazzo.buildconfig")
  apply(plugin = "com.github.ben-manes.versions")

  extra["versionStr"] = "2.1.3"
  description = "ReadingBat Core"
  group = "com.github.readingbat"
  version = versionStr

  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}

subprojects {
  configureKotlin()
  configurePublishing()
  configureTesting()
  configureKotlinter()
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

  configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
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

    testLogging {
      events("passed", "skipped", "failed", "standardOut", "standardError")
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = true
    }
  }
}
