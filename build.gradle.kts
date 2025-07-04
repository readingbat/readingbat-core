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

  extra["versionStr"] = "2.1.0"
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
    jvmToolchain(11)
  }

  tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
      freeCompilerArgs.addAll(
        listOf(
          "-opt-in=kotlin.ExperimentalStdlibApi",
          "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
          "-opt-in=kotlin.contracts.ExperimentalContracts",
          "-opt-in=kotlin.time.ExperimentalTime",
          "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi",
          "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
          "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
          "-opt-in=kotlinx.coroutines.ObsoleteCoroutinesApi",
        )
      )
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
