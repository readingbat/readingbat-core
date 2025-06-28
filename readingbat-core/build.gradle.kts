description = "readingbat-core"

// These are for the uber target
val mainName = "TestMain"
val appName = "server"

// This is for ./gradlew run
application {
  mainClass.set(mainName)
}

dependencies {
  implementation(libs.serialization)

  implementation(libs.core.utils)

  implementation(libs.ktor.server.utils)
  implementation(libs.ktor.client.utils)

  implementation(libs.script.utils.common)
  implementation(libs.script.utils.python)
  implementation(libs.script.utils.java)
  implementation(libs.script.utils.kotlin)

  implementation(libs.service.utils)
  implementation(libs.prometheus.utils)
  implementation(libs.exposed.utils)

  implementation(libs.prometheus.proxy)

  implementation(libs.simple.client)

  implementation(libs.script.engine)

  implementation(libs.css)

  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.cio)

  implementation(libs.ktor.sessions)
  implementation(libs.ktor.rate.limit)
  implementation(libs.ktor.html)
  implementation(libs.ktor.metrics)
  implementation(libs.ktor.websockets)
  implementation(libs.ktor.compression)
  implementation(libs.ktor.calllogging)
  implementation(libs.ktor.resources)

//    implementation(libs.khealth)

  implementation(libs.hikari)

  implementation(libs.exposed.core)
  implementation(libs.exposed.jdbc)
  implementation(libs.exposed.jodatime)

  implementation(libs.pgjdbc)
  implementation(libs.socket)

  runtimeOnly(libs.postgres)

  implementation(libs.gson)

  implementation(libs.sendgrid)

  implementation(libs.commons)
  implementation(libs.flexmark)

  implementation(libs.github)

  implementation(libs.logging)
  implementation(libs.logback)

  runtimeOnly(libs.kotlin.scripting.jsr223)

  testImplementation(libs.kotlin.test)
  testImplementation(libs.ktor.server.test.host)

  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.kotest.assertions.ktor)

  testImplementation(project(":readingbat-kotest"))
}

buildConfig {
  packageName("com.github.readingbat")

  buildConfigField("String", "CORE_NAME", "\"${project.name}\"")
  buildConfigField("String", "CORE_VERSION", "\"${project.version}\"")
  buildConfigField("String", "CORE_RELEASE_DATE", "\"6/28/25\"")
  buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
}

// Include build uberjars in heroku deploy
tasks.register("stage") {
  dependsOn("uberjar", "build", "clean")
}

tasks.named("build") {
  mustRunAfter("clean")
}
