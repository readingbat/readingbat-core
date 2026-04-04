@file:Suppress("unused")

package website

import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.OwnerType.Organization
import com.readingbat.dsl.ReturnType.BooleanType
import com.readingbat.dsl.ReturnType.StringType
import com.readingbat.dsl.readingBatContent

// --8<-- [start:unary_plus_language]
val unaryPlusLanguageExample =
  readingBatContent {
    // Define Python content in a separate variable
    val externalPython =
      readingBatContent {
        repo = FileSystemSource("./")
        python {
          srcPath = "python"
          group("External-Warmup") {
            packageName = "warmup1"
            includeFilesWithType = "*.py" returns BooleanType
          }
        }
      }

    repo = FileSystemSource("./")

    java {
      group("Warmup-1") {
        packageName = "warmup1"
        includeFiles = "*.java"
      }
    }

    // Merge external Python content using unary plus
    +externalPython.python
  }
// --8<-- [end:unary_plus_language]

// --8<-- [start:include_with_prefix]
val includeWithPrefixExample =
  readingBatContent {
    val community =
      readingBatContent {
        repo = GitHubRepo(Organization, "readingbat", "readingbat-java-content")

        java {
          group("Warmup-1") {
            packageName = "warmup1"
            includeFiles = "*.java"
          }
        }
      }

    repo = FileSystemSource("./")

    java {
      group("My-Warmup") {
        packageName = "warmup1"
        includeFiles = "*.java"
      }
    }

    // Include with a name prefix to avoid group name collisions
    include(community.java, namePrefix = "Community-")
  }
// --8<-- [end:include_with_prefix]

// --8<-- [start:conditional_content]
val conditionalContent =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      group("Warmup-1") {
        packageName = "warmup1"
        includeFiles = "*.java"
      }

      // Only include advanced challenges in production
      if (System.getenv("IS_PRODUCTION")?.toBoolean() == true) {
        group("Advanced-1") {
          packageName = "advanced1"
          includeFiles = "*.java"
        }
      }
    }
  }
// --8<-- [end:conditional_content]

// --8<-- [start:challenge_file_name]
val customFileNameExample =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      group("Warmup-1") {
        packageName = "warmup1"

        // Default: fileName is derived from challenge name + language suffix
        challenge("SleepIn") // looks for SleepIn.java

        // Override the file name explicitly
        challenge("MyChallenge") {
          fileName = "CustomFileName.java"
        }
      }
    }

    kotlin {
      group("Warmup-1") {
        packageName = "warmup1"

        challenge("sleepIn") { returnType = BooleanType } // looks for sleepIn.kt

        challenge("customName") {
          returnType = StringType
          fileName = "MyCustomChallenge.kt"
        }
      }
    }
  }
// --8<-- [end:challenge_file_name]

// --8<-- [start:coding_bat_equiv]
val codingBatEquivExample =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      group("Warmup-1") {
        packageName = "warmup1"

        challenge("SleepIn") {
          codingBatEquiv = "p187868"
          description = "Return true when you can sleep in"
        }

        challenge("Diff21") {
          codingBatEquiv = "p116624"
          description = "Return the absolute difference from 21"
        }
      }
    }
  }
// --8<-- [end:coding_bat_equiv]
