description = "readingbat-kotest"

dependencies {
  implementation(project(":readingbat-core"))

  implementation(libs.gson)

  // implementation(libs.kotlin.test)
  implementation(platform(libs.ktor.bom))
  implementation(libs.ktor.server.test.host)

  implementation(libs.kotest.assertions.core)
  implementation(libs.kotest.assertions.ktor)
  implementation(libs.kotest.runner.junit5)

  implementation(libs.bundles.exposed)
  implementation(libs.postgres)
  implementation(libs.flyway.core)
  implementation(libs.flyway.postgres)

  implementation(libs.testcontainers.postgresql)
}
