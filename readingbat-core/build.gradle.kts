/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

description = "readingbat-core"

// These are for the uber target
val mainName = "TestMain"
val appName = "server"

// This is for ./gradlew run
application {
  mainClass.set(mainName)
}

dependencies {
  implementation(libraries["serialization"]!!)

  implementation(libraries["core_utils"]!!)

  implementation(libraries["ktor_server_utils"]!!)
  implementation(libraries["ktor_client_utils"]!!)

  implementation(libraries["script_utils_common"]!!)
  implementation(libraries["script_utils_python"]!!)
  implementation(libraries["script_utils_java"]!!)
  implementation(libraries["script_utils_kotlin"]!!)

  implementation(libraries["service_utils"]!!)
  implementation(libraries["prometheus_utils"]!!)
  implementation(libraries["exposed_utils"]!!)

  implementation(libraries["prometheus_proxy"]!!)

  implementation(libraries["simple_client"]!!)

  implementation(libraries["script_engine"]!!)

  implementation(libraries["css"]!!)

  implementation(libraries["ktor_server_core"]!!)
  implementation(libraries["ktor_server_cio"]!!)
  implementation(libraries["ktor_server_auth"]!!)
  implementation(libraries["ktor_client_core"]!!)
  implementation(libraries["ktor_client_cio"]!!)

  implementation(libraries["ktor_sessions"]!!)
  implementation(libraries["ktor_rate_limit"]!!)
  implementation(libraries["ktor_html"]!!)
  implementation(libraries["ktor_metrics"]!!)
  implementation(libraries["ktor_websockets"]!!)
  implementation(libraries["ktor_compression"]!!)
  implementation(libraries["ktor_calllogging"]!!)
  implementation(libraries["ktor_resources"]!!)

//    implementation(libraries["khealth"]!!)

  implementation(libraries["hikari"]!!)

  implementation(libraries["exposed_core"]!!)
  implementation(libraries["exposed_jdbc"]!!)
  implementation(libraries["exposed_jodatime"]!!)

  implementation(libraries["pgjdbc"]!!)
  implementation(libraries["socket"]!!)

  runtimeOnly(libraries["postgres"]!!)

  implementation(libraries["gson"]!!)

  implementation(libraries["sendgrid"]!!)

  implementation(libraries["commons"]!!)
  implementation(libraries["flexmark"]!!)

  implementation(libraries["github"]!!)

  testImplementation(libraries["ktor_server_tests"]!!)
  testImplementation(libraries["ktor_server_test_host"]!!)

  testImplementation(libraries["kotest_runner_junit5"]!!)
  testImplementation(libraries["kotest_assertions_core"]!!)
  testImplementation(libraries["kotest_assertions_ktor"]!!)

  testImplementation(project(":readingbat-kotest"))
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      groupId = "com.github.readingbat"
      artifactId = project.name
      version = project.version.toString()

      from(components["java"])
    }
  }
}

//publishing {
//    publications {
//        create<MavenPublication>("mavenJava") {
//            from(components["java"])
//            versionMapping {
//                usage("java-api") {
//                    fromResolutionOf("runtimeClasspath")
//                }
//                usage("java-runtime") {
//                    fromResolutionResult()
//                }
//            }
//        }
//    }
//}

buildConfig {
  packageName("com.github.readingbat")

  buildConfigField("String", "CORE_NAME", "\"${project.name}\"")
  buildConfigField("String", "CORE_VERSION", "\"${project.version}\"")
  buildConfigField("String", "CORE_RELEASE_DATE", "\"4/14/25\"")
  buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
}

// Include build uberjars in heroku deploy
tasks.register("stage") {
  dependsOn("uberjar", "build", "clean")
}
tasks.named("build") {
  mustRunAfter("clean")
}

//tasks.register<Jar>("uberjar") {
//    dependsOn("shadowJar")
//    zip64 = true
//    archiveFileName.set("server.jar")
//    manifest {
//        attributes(
//            "Implementation-Title" to appName,
//            "Implementation-Version" to version,
//            "Built-Date" to java.util.Date(),
//            "Built-JDK" to System.getProperty("java.version"),
//            "Main-Class" to mainName
//        )
//    }
//    from(zipTree(tasks.named("shadowJar").get().outputs.files.singleFile))
//}
