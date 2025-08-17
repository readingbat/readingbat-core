import java.time.LocalDate
import java.time.format.DateTimeFormatter

description = "readingbat-core"

dependencies {
  implementation(libs.serialization)

  api(libs.common.core.utils)

  implementation(libs.common.ktor.server.utils)
  implementation(libs.common.ktor.client.utils)
  implementation(libs.common.script.utils.common)
  implementation(libs.common.script.utils.python)
  implementation(libs.common.script.utils.java)
  implementation(libs.common.script.utils.kotlin)
  implementation(libs.common.service.utils)
  implementation(libs.common.prometheus.utils)
  implementation(libs.common.exposed.utils)
  implementation(libs.common.email.utils)
  implementation(libs.common.recaptcha.utils)

  implementation(libs.prometheus.proxy)

  implementation(libs.simple.client)

  implementation(libs.java.scripting)
  runtimeOnly(libs.python.scripting)
  runtimeOnly(libs.kotlin.scripting)

  implementation(libs.css)

  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.cio)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)

  implementation(libs.ktor.sessions)
  implementation(libs.ktor.rate.limit)
  implementation(libs.ktor.html)
  implementation(libs.ktor.metrics)
  implementation(libs.ktor.websockets)
  implementation(libs.ktor.compression)
  implementation(libs.ktor.calllogging)
  implementation(libs.ktor.resources)

  implementation(libs.hikari)

  implementation(libs.exposed.core)
  implementation(libs.exposed.jdbc)
  implementation(libs.exposed.jodatime)

  implementation(libs.pgjdbc)
  implementation(libs.socket)

  implementation(libs.gson)

  implementation(libs.resend)

  implementation(libs.commons)
  implementation(libs.flexmark)

  implementation(libs.github)

  runtimeOnly(libs.kotlin.scripting.jsr223)
  runtimeOnly(libs.postgres)

//  testImplementation(libs.kotlin.test)
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
