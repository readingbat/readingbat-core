
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.buildconfig) apply false
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.kotlinter)
  alias(libs.plugins.kover)
  alias(libs.plugins.versions)
  alias(libs.plugins.dokka)
  alias(libs.plugins.maven.publish) apply false
}

val kotlinLib = libs.plugins.kotlin.jvm.get().pluginId
val ktlinterLib = libs.plugins.kotlinter.get().pluginId
val detektLib = libs.plugins.detekt.get().pluginId
val koverLib = libs.plugins.kover.get().pluginId

val coreModule = ":readingbat-core"
val kotestModule = ":readingbat-kotest"
val projectName = "readingbat-core"
val repoUrl = "https://github.com/readingbat/readingbat-core"
val scmPath = "github.com/readingbat/readingbat-core.git"
val secretsEnvKey = "secretsEnv"
val jvmTargetVersion = libs.versions.jvm.get()

// Version resolution: gradle.properties (`version=...`) is the source of truth.
// `-PoverrideVersion=...` on the CLI overrides it on the root project, and the
// `subprojects {}` block below propagates `rootProject.version` to every module.
version = providers.gradleProperty("overrideVersion").getOrElse(version.toString())

dependencies {
  dokka(project(coreModule))
  dokka(project(kotestModule))

  kover(project(coreModule))
  kover(project(kotestModule))
}

dokka {
  moduleName.set("ReadingBat")
  pluginsConfiguration.html {
    homepageLink.set(repoUrl)
    footerMessage.set(projectName)
  }
}

subprojects {
  group = rootProject.group
  version = rootProject.version

  apply(plugin = "java-library")
  apply(plugin = "com.github.ben-manes.versions")
  apply(plugin = koverLib)

  configureKotlin()
  configurePublishing()
  configureTesting()
  configureKotlinter()
  configureDetekt()
  configureSecrets()
  configureVersions()
}

fun Project.configureKotlin() {
  apply {
    plugin(kotlinLib)
  }

  tasks.named("build") {
    mustRunAfter("clean")
  }

  kotlin {
    jvmToolchain(jvmTargetVersion.toInt())

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

    pom {
      name.set(project.name)
      description.set(provider { project.description })
      url.set(repoUrl)
      licenses {
        license {
          name.set("Apache License 2.0")
          url.set("https://www.apache.org/licenses/LICENSE-2.0")
        }
      }
      developers {
        developer {
          id.set("pambrose")
          name.set("Paul Ambrose")
          email.set("pambrose@readingbat.com")
        }
      }
      scm {
        connection.set("scm:git:git://$scmPath")
        developerConnection.set("scm:git:ssh://$scmPath")
        url.set(repoUrl)
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

fun Project.configureDetekt() {
  apply {
    plugin(detektLib)
  }

  extensions.configure<DetektExtension> {
    parallel = true
    buildUponDefaultConfig = true
    autoCorrect = false
    val cfg = rootProject.file("config/detekt/detekt.yml")
    if (cfg.exists()) config.setFrom(cfg)
    val base = rootProject.file("config/detekt/baseline.xml")
    if (base.exists()) baseline = base
  }

  tasks.withType<Detekt>().configureEach {
    jvmTarget = jvmTargetVersion
    reports {
      html.required.set(true)
      checkstyle.required.set(true)
      sarif.required.set(false)
      markdown.required.set(false)
    }
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
      events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED, TestLogEvent.STANDARD_ERROR)
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = false
    }
  }
}

fun Project.configureVersions() {
  // Match preview/unstable qualifiers anchored on a separator so plain substrings like
  // "DEV" inside a real version (e.g. "1.0-developer") don't trigger false rejections.
  val nonStableRegex = Regex("(?i)[-.](RC|BETA|ALPHA|M\\d+|SNAPSHOT|DEV|PREVIEW|EAP|CR)\\b")

  tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
      nonStableRegex.containsMatchIn(candidate.version)
    }
  }
}

abstract class SecretsEnvSource : ValueSource<Map<String, String>, SecretsEnvSource.Params> {
  interface Params : ValueSourceParameters {
    val secretsFile: RegularFileProperty
  }

  override fun obtain(): Map<String, String> {
    val file = parameters.secretsFile.asFile.orNull ?: return emptyMap()
    if (!file.exists()) return emptyMap()
    return file.readLines()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") }
      .mapNotNull { line ->
        val idx = line.indexOf('=')
        if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim().removeSurrounding("\"") else null
      }
      .toMap()
  }
}

fun Project.configureSecrets() {
  val secretsFile = rootProject.layout.projectDirectory.file("secrets/secrets.env")
  val envVarsProvider = providers.of(SecretsEnvSource::class.java) {
    parameters.secretsFile.set(secretsFile)
  }

  tasks.withType<JavaExec>().configureEach {
    inputs.property(secretsEnvKey, envVarsProvider)
    environment(envVarsProvider.get())
  }
  tasks.withType<Test>().configureEach {
    inputs.property(secretsEnvKey, envVarsProvider)
    environment(envVarsProvider.get())
  }
}
