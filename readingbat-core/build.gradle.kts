import java.time.LocalDate
import java.time.format.DateTimeFormatter

description = "readingbat-core"

dependencies {
  implementation(libs.serialization)

  api(platform(libs.common.utils.bom))
  api(libs.bundles.common.utils)

  implementation(platform(libs.ktor.bom))
  implementation(libs.bundles.ktor.client)
  implementation(libs.bundles.ktor.server)

  implementation(libs.bundles.exposed)

  implementation(libs.prometheus.proxy)

  implementation(libs.simple.client)

  runtimeOnly(libs.kotlin.scripting.jsr223)
  runtimeOnly(libs.python.scripting)
  runtimeOnly(libs.kotlin.scripting)
  implementation(libs.java.scripting)

  implementation(libs.css)

  implementation(libs.hikari)

  runtimeOnly(libs.postgres)
  implementation(libs.pgjdbc)
  implementation(libs.socket)

  implementation(libs.gson)

  implementation(libs.resend)

  implementation(libs.commons)
  implementation(libs.flexmark)

  implementation(libs.github)

  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.kotest.assertions.ktor)

  testImplementation(project(":readingbat-kotest"))
}

val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

buildConfig {
  packageName("com.github.readingbat")

  buildConfigField("String", "CORE_NAME", "\"${project.name}\"")
  buildConfigField("String", "CORE_VERSION", "\"${project.version}\"")
  buildConfigField("String", "CORE_RELEASE_DATE", "\"${LocalDate.now().format(formatter)}\"")
  buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
}

// Include build uberjars in heroku deploy
tasks.register("stage") {
  dependsOn("uberjar", "build", "clean")
}
