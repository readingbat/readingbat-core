@file:Suppress("unused")

package website

import com.pambrose.common.util.FileSystemSource
import com.readingbat.dsl.ReturnType.BooleanType
import com.readingbat.dsl.ReturnType.IntType
import com.readingbat.dsl.ReturnType.StringArrayType
import com.readingbat.dsl.readingBatContent
import com.readingbat.kotest.TestSupport.forEachChallenge
import com.readingbat.kotest.TestSupport.forEachGroup
import com.readingbat.kotest.TestSupport.forEachLanguage
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.kotest.TestSupport.javaChallenge
import com.readingbat.kotest.TestSupport.javaGroup
import com.readingbat.kotest.TestSupport.kotlinChallenge
import com.readingbat.kotest.TestSupport.pythonChallenge

// --8<-- [start:test_content]
val testContent =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      srcPath = "src/test/java"
      group("Test Cases") {
        packageName = "com.readingbat.testcontent"
        challenge("StringArrayTest1")
      }
    }

    python {
      repo = FileSystemSource("../")
      srcPath = "python"
      group("Test Cases") {
        packageName = "testcontent"
        challenge("boolean_array_test") { returnType = BooleanType }
        challenge("int_array_test") { returnType = IntType }
      }
    }

    kotlin {
      srcPath = "src/test/kotlin"
      group("Test Cases") {
        packageName = "com.readingbat.testcontent"
        challenge("StringArrayKtTest1") { returnType = StringArrayType }
      }
    }
  }
// --8<-- [end:test_content]

// --8<-- [start:init_properties]
fun initExample() {
  // Initialize test properties before running tests
  initTestProperties()
}
// --8<-- [end:init_properties]

// --8<-- [start:iterate_content]
fun iterateContentExample() {
  initTestProperties()
  val content = testContent

  // Iterate over all languages, groups, and challenges
  content.forEachLanguage {
    println("Language: $languageName")
    forEachGroup {
      println("  Group: $groupName")
      forEachChallenge {
        println("    Challenge: $challengeName")
        val info = functionInfo()
        println("    Questions: ${info.questionCount}")
        println("    Correct answers: ${info.correctAnswers}")
      }
    }
  }
}
// --8<-- [end:iterate_content]

// --8<-- [start:access_challenges]
fun accessChallengesExample() {
  initTestProperties()
  val content = testContent

  // Access a specific Java group
  val javaGroup = content.javaGroup("Test Cases")
  println("Java group has ${javaGroup.challenges.size} challenges")

  // Access a specific challenge and its function info
  content.javaChallenge("Test Cases", "StringArrayTest1") {
    println("Invocations: $invocations")
    println("Return type: $returnType")
    println("Correct answers: $correctAnswers")
  }

  // Access Python challenge
  content.pythonChallenge("Test Cases", "boolean_array_test") {
    println("Question count: $questionCount")
  }

  // Access Kotlin challenge
  content.kotlinChallenge("Test Cases", "StringArrayKtTest1") {
    println("Question count: $questionCount")
  }
}
// --8<-- [end:access_challenges]
