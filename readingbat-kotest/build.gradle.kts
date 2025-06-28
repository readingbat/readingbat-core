description = "readingbat-kotest"

//mainClassName = 'TestMain' // This prevents a gradle error

dependencies {
  implementation(project(":readingbat-core"))

  implementation(libs.gson)

  implementation(libs.kotlin.test)
  implementation(libs.ktor.server.test.host)

  implementation(libs.kotest.runner.junit5)
  implementation(libs.kotest.assertions.core)
  implementation(libs.kotest.assertions.ktor)
//    implementation(libs.kotest.property)
}
