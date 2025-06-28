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

import jdk.tools.jlink.resources.plugins
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  dependencies {
    classpath("org.postgresql:postgresql:${property("postgres_version")}")
  }
}

plugins {
  id("java")
  id("application")
  id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
  id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
  id("org.jmailen.kotlinter") version "5.1.1" apply false
  id("com.github.gmazzo.buildconfig") version "5.6.7" apply false
  id("com.github.ben-manes.versions") version "0.52.0" apply false
}

val libraries by extra {
  mapOf(
    "serialization" to "org.jetbrains.kotlinx:kotlinx-serialization-json:${property("serialization_version")}",
    "core_utils" to "com.github.pambrose.common-utils:core-utils:${property("utils_version")}",
    "ktor_server_utils" to "com.github.pambrose.common-utils:ktor-server-utils:${property("utils_version")}",
    "ktor_client_utils" to "com.github.pambrose.common-utils:ktor-client-utils:${property("utils_version")}",
    "script_utils_common" to "com.github.pambrose.common-utils:script-utils-common:${property("utils_version")}",
    "script_utils_python" to "com.github.pambrose.common-utils:script-utils-python:${property("utils_version")}",
    "script_utils_java" to "com.github.pambrose.common-utils:script-utils-java:${property("utils_version")}",
    "script_utils_kotlin" to "com.github.pambrose.common-utils:script-utils-kotlin:${property("utils_version")}",
    "service_utils" to "com.github.pambrose.common-utils:service-utils:${property("utils_version")}",
    "prometheus_utils" to "com.github.pambrose.common-utils:prometheus-utils:${property("utils_version")}",
    "exposed_utils" to "com.github.pambrose.common-utils:exposed-utils:${property("utils_version")}",
    "prometheus_proxy" to "com.github.pambrose:prometheus-proxy:${property("proxy_version")}",
    "simple_client" to "io.prometheus:simpleclient:${property("prometheus_version")}",
    "script_engine" to "ch.obermuhlner:java-scriptengine:${property("java_script_version")}",
    "css" to "org.jetbrains.kotlin-wrappers:kotlin-css:${property("css_version")}",
    "ktor_client_core" to "io.ktor:ktor-client-core:${property("ktor_version")}",
    "ktor_client_cio" to "io.ktor:ktor-client-cio:${property("ktor_version")}",
    "ktor_server_core" to "io.ktor:ktor-server:${property("ktor_version")}",
    "ktor_server_cio" to "io.ktor:ktor-server-cio:${property("ktor_version")}",
    "ktor_server_auth" to "io.ktor:ktor-server-auth-jvm:${property("ktor_version")}",
    "ktor_sessions" to "io.ktor:ktor-server-sessions:${property("ktor_version")}",
    "ktor_rate_limit" to "io.ktor:ktor-server-rate-limit:${property("ktor_version")}",
    "ktor_html" to "io.ktor:ktor-server-html-builder:${property("ktor_version")}",
    "ktor_resources" to "io.ktor:ktor-server-resources:${property("ktor_version")}",
    "ktor_metrics" to "io.ktor:ktor-server-metrics:${property("ktor_version")}",
    "ktor_websockets" to "io.ktor:ktor-server-websockets:${property("ktor_version")}",
    "ktor_compression" to "io.ktor:ktor-server-compression:${property("ktor_version")}",
    "ktor_calllogging" to "io.ktor:ktor-server-call-logging:${property("ktor_version")}",
    "khealth" to "dev.hayden:khealth:${property("khealth_version")}",
    "hikari" to "com.zaxxer:HikariCP:${property("hikari_version")}",
    "exposed_core" to "org.jetbrains.exposed:exposed-core:${property("exposed_version")}",
    "exposed_jdbc" to "org.jetbrains.exposed:exposed-jdbc:${property("exposed_version")}",
    "exposed_jodatime" to "org.jetbrains.exposed:exposed-jodatime:${property("exposed_version")}",
    "pgjdbc" to "com.impossibl.pgjdbc-ng:pgjdbc-ng-all:${property("pgjdbc_version")}",
    "postgres" to "org.postgresql:postgresql:${property("postgres_version")}",
    "socket" to "com.google.cloud.sql:postgres-socket-factory:${property("cloud_version")}",
    "gson" to "com.google.code.gson:gson:${property("gson_version")}",
    "sendgrid" to "com.sendgrid:sendgrid-java:${property("sendgrid_version")}",
    "commons" to "org.apache.commons:commons-text:${property("commons_version")}",
    "flexmark" to "com.vladsch.flexmark:flexmark:${property("flexmark_version")}",
    "github" to "org.kohsuke:github-api:${property("github_api_version")}",
    "ktor_server_tests" to "org.jetbrains.kotlin:kotlin-test:${property("kotlin_version")}",
    "ktor_server_test_host" to "io.ktor:ktor-server-test-host:${property("ktor_version")}",
    "kotest_runner_junit5" to "io.kotest:kotest-runner-junit5:${property("kotest_version")}",
    "kotest_assertions_core" to "io.kotest:kotest-assertions-core:${property("kotest_version")}",
    "kotest_assertions_ktor" to "io.kotest:kotest-assertions-ktor:${property("kotest_ktor_version")}"
  )
}

allprojects {
  description = "ReadingBat Core"
  group = "com.github.readingbat"
  version = "2.0.5"

  apply(plugin = "application")
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")
  apply(plugin = "org.jmailen.kotlinter")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
  apply(plugin = "com.github.gmazzo.buildconfig")
  apply(plugin = "com.github.ben-manes.versions")

  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }

//    sourceCompatibility = JavaVersion.VERSION_17
//    targetCompatibility = JavaVersion.VERSION_17

//    publishing {
//        publications {
//            create<MavenPublication>("mavenJava") {
//                from(components["java"])
//                versionMapping {
//                    usage("java-api") {
//                        fromResolutionOf("runtimeClasspath")
//                    }
//                    usage("java-runtime") {
//                        fromResolutionResult()
//                    }
//                }
//            }
//        }
//    }
}

subprojects {
  dependencies {
    // These are required for the annotation args below
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutines_version")}")
    //implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${property("serialization_version")}")

    implementation("io.github.oshai:kotlin-logging-jvm:${property("logging_version")}")
    implementation("ch.qos.logback:logback-classic:${property("logback_version")}")

    runtimeOnly("org.jetbrains.kotlin:kotlin-scripting-jsr223:${property("kotlin_version")}")
  }

  tasks.register<Jar>("sourcesJar") {
    dependsOn(tasks.classes)
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
  }

  tasks.register<Jar>("javadocJar") {
    dependsOn(tasks.javadoc)
    from(tasks.javadoc.get().destinationDir)
    archiveClassifier.set("javadoc")
  }

  artifacts {
    archives(tasks.named("sourcesJar"))
    //archives(tasks.named("javadocJar"))
  }

  java {
    withSourcesJar()
  }

  kotlin {
    jvmToolchain(17)
  }

  tasks.withType<KotlinCompile>().configureEach {
    //'-Xlint:-options',
    kotlinOptions {
      freeCompilerArgs += listOf(
        // "-Xbackend-threads=8",
        "-opt-in=kotlin.time.ExperimentalTime",
        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        "-opt-in=kotlin.contracts.ExperimentalContracts",
        "-opt-in=kotlinx.coroutines.ObsoleteCoroutinesApi",
        "-opt-in=kotlin.ExperimentalStdlibApi",
        "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
        "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi",
        "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
        // "-Xno-optimized-callable-references"
      )
    }
  }

  tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
      events("passed", "skipped", "failed", "standardOut", "standardError")
      exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
      showStandardStreams = true
    }
  }

  configure<org.jmailen.gradle.kotlinter.KotlinterExtension> {
    reporters = arrayOf("checkstyle", "plain")
  }
}
