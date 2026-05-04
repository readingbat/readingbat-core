description = "Kotest test utilities and helpers for ReadingBat integration testing"

dependencies {
  implementation(project(":readingbat-core"))

  implementation(libs.serialization)

  implementation(libs.ktor.server.test.host)

  implementation(libs.bundles.kotest)

  implementation(libs.bundles.exposed)
  implementation(libs.postgres)
  implementation(libs.flyway.core)
  implementation(libs.flyway.postgres)

  implementation(libs.testcontainers.postgresql)
}
