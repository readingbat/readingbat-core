import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  application
  `java-library`

  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.buildconfig) apply false
  alias(libs.plugins.kotlinter)
  alias(libs.plugins.versions)
  alias(libs.plugins.dokka)
  alias(libs.plugins.maven.publish) apply false
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
  apply(plugin = "org.jmailen.kotlinter")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
  apply(plugin = "com.github.gmazzo.buildconfig")
  apply(plugin = "com.github.ben-manes.versions")

  extra["versionStr"] = findProperty("overrideVersion")?.toString() ?: "3.1.3"
  group = "com.readingbat"
  description = "ReadingBat Core"
  version = versionStr

  repositories {
    // mavenLocal()
    google()
    mavenCentral()
  }

  configureVersions()
}

dependencies {
  dokka(project(":readingbat-core"))
  dokka(project(":readingbat-kotest"))
}

dokka {
  moduleName.set("ReadingBat")
  pluginsConfiguration.html {
    homepageLink.set("https://github.com/readingbat/readingbat-core")
    footerMessage.set("readingbat-core")
  }
}

subprojects {
  apply(plugin = "application")
  apply(plugin = "java-library")

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
    plugin("org.jetbrains.dokka")
    plugin("com.vanniktech.maven.publish")
  }

  extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
    moduleName.set(project.name)
    pluginsConfiguration.named<org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters>("html") {
      homepageLink.set("https://github.com/readingbat/readingbat-core")
      footerMessage.set(project.name)
    }
  }

  extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    configure(
      com.vanniktech.maven.publish.KotlinJvm(
        javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
        sourcesJar = SourcesJar.Sources(),
      ),
    )
    coordinates("com.readingbat", project.name, version.toString())

    pom {
      name.set(project.name)
      description.set(provider { project.description })
      url.set("https://github.com/readingbat/readingbat-core")
      licenses {
        license {
          name.set("Apache License 2.0")
          url.set("https://www.apache.org/licenses/LICENSE-2.0")
        }
      }
      developers {
        developer {
          id.set("readingbat")
          name.set("Paul Ambrose")
          email.set("pambrose@readingbat.com")
        }
      }
      scm {
        connection.set("scm:git:git://github.com/readingbat/readingbat-core.git")
        developerConnection.set("scm:git:ssh://github.com/readingbat/readingbat-core.git")
        url.set("https://github.com/readingbat/readingbat-core")
      }
    }

    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
  }

// Skip signing when no GPG key is provided (e.g., local publishing)
  tasks.withType<Sign>().configureEach {
    isEnabled = project.findProperty("signingInMemoryKey") != null
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
      events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = false
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
