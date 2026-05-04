import org.jetbrains.dokka.gradle.DokkaExtension
import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
  application
  alias(libs.plugins.buildconfig)
}

description = "Ktor web server, DSL engine, and database layer for ReadingBat programming challenges"

application {
  mainClass = "TestMain"
}

dependencies {
  implementation(libs.serialization)

  api(libs.bundles.common.utils)
  implementation(libs.prometheus.proxy)

  implementation(libs.bundles.ktor.client)
  implementation(libs.bundles.ktor.server)

  implementation(libs.bundles.exposed)

  implementation(libs.simple.client)

  runtimeOnly(libs.python.scripting)
  runtimeOnly(libs.kotlin.scripting)
  implementation(libs.java.scripting)

  implementation(libs.hikari)

  runtimeOnly(libs.postgres)
  implementation(libs.pgjdbc)
  implementation(libs.socket)

  implementation(libs.resend)

  implementation(libs.commons)
  implementation(libs.flexmark)

  implementation(libs.github)

  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.bundles.kotest)

  testImplementation(libs.playwright)

  testImplementation(project(":readingbat-kotest"))
}

val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
val releaseDate: String =
  (findProperty("releaseDate") as? String)?.takeIf { it.isNotBlank() } ?: LocalDate.now().format(formatter)
val buildTime: Long = providers.gradleProperty("buildTime").orNull?.toLong() ?: System.currentTimeMillis()

buildConfig {
  packageName("com.readingbat")

  buildConfigField("String", "CORE_NAME", "\"${project.name}\"")
  buildConfigField("String", "CORE_VERSION", "\"${project.version}\"")
  buildConfigField("String", "CORE_RELEASE_DATE", "\"$releaseDate\"")
  buildConfigField("long", "BUILD_TIME", "${buildTime}L")
}

// Exclude top-level entry points from KDocs
extensions.configure<DokkaExtension> {
  dokkaSourceSets.configureEach {
    suppressedFiles.from(
      "src/main/kotlin/Content.kt",
      "src/main/kotlin/TestMain.kt",
    )
  }
}

// Tailwind CSS v4 build via standalone CLI
// Usage: ./gradlew :readingbat-core:tailwindBuild
tasks.register<Exec>("tailwindBuild") {
  group = "frontend"
  description = "Build Tailwind CSS output from Kotlin source files"
  workingDir = rootProject.projectDir
  commandLine(
    "./bin/tailwindcss-v4",
    "-i", "readingbat-core/src/main/resources/css/tailwind-input.css",
    "-o", "readingbat-core/src/main/resources/static/tailwind.css",
    "--minify",
  )
}

tasks.register<Exec>("tailwindBuildFull") {
  group = "frontend"
  description = "Build Tailwind CSS output from Kotlin source files (unminified)"
  workingDir = rootProject.projectDir
  commandLine(
    "./bin/tailwindcss-v4",
    "-i", "readingbat-core/src/main/resources/css/tailwind-input.css",
    "-o", "readingbat-core/src/main/resources/static/tailwind.css",
  )
}

// On macOS, regenerate Tailwind CSS before processing resources.
// On Linux (e.g., jitpack.io), the checked-in tailwind.css is used as-is.
val isMac = providers.systemProperty("os.name").map { it.lowercase().contains("mac") }.getOrElse(false)
if (isMac) {
  tasks.named("processResources") {
    dependsOn("tailwindBuild")
  }
}

// Include build uberjars in heroku deploy
tasks.register("stage") {
  dependsOn(tasks.named("build"))
}
