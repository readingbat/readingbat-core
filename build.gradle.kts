import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.buildconfig) apply false
  alias(libs.plugins.kotlinter)
  alias(libs.plugins.kover)
  alias(libs.plugins.versions)
  alias(libs.plugins.dokka)
  alias(libs.plugins.maven.publish) apply false
}

val kotlinLib = libs.plugins.kotlin.jvm.get().pluginId
val serializationLib = libs.plugins.kotlin.serialization.get().pluginId
val ktlinterLib = libs.plugins.kotlinter.get().pluginId
val koverLib = libs.plugins.kover.get().pluginId

providers.gradleProperty("overrideVersion").orNull?.let { version = it }

allprojects {
  configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
  }
}

subprojects {
  group = rootProject.group
  version = rootProject.version
}

dependencies {
  dokka(project(":readingbat-core"))
  dokka(project(":readingbat-kotest"))

  kover(project(":readingbat-core"))
  kover(project(":readingbat-kotest"))
}

dokka {
  moduleName.set("ReadingBat")
  pluginsConfiguration.html {
    homepageLink.set("https://github.com/readingbat/readingbat-core")
    footerMessage.set("readingbat-core")
  }
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "com.github.ben-manes.versions")

  configureKotlin()
  configurePublishing()
  configureTesting()
  configureKotlinter()
  configureKover()
  configureSecrets()
  configureVersions()
}

project(":readingbat-core") {
  apply(plugin = serializationLib)
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
}

fun Project.configurePublishing() {
  apply {
    plugin("org.jetbrains.dokka")
    plugin("com.vanniktech.maven.publish")
  }

  extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    configure(
      com.vanniktech.maven.publish.KotlinJvm(
        javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
        sourcesJar = SourcesJar.Sources(),
      ),
    )

    val projectDesc = project.description
    pom {
      name.set(project.name)
      description.set(projectDesc)
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
    // Skip signing when no GPG key is provided (e.g., local publishing)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
      signAllPublications()
    }
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

fun Project.configureKover() {
  apply {
    plugin(koverLib)
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

fun Project.configureVersions() {
  fun isNonStable(version: String): Boolean {
    val upper = version.uppercase()
    return listOf("-RC", "-BETA", "-ALPHA", "-M", "SNAPSHOT", "-DEV", "-PREVIEW", "-EAP", "-CR")
      .any { it in upper }
  }

  tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
      isNonStable(candidate.version)
    }
  }
}

fun Project.configureSecrets() {
  val secretsFile = rootProject.layout.projectDirectory.file("secrets/secrets.env").asFile
  val envVarsProvider = providers.provider {
    if (secretsFile.exists()) {
      secretsFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
          val idx = line.indexOf('=')
          if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim().removeSurrounding("\"") else null
        }
        .toMap()
    } else {
      emptyMap()
    }
  }

  tasks.withType<JavaExec>().configureEach { doFirst { environment(envVarsProvider.get()) } }
  tasks.withType<Test>().configureEach { doFirst { environment(envVarsProvider.get()) } }
}
