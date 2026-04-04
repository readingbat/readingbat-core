@file:Suppress("unused")

package website

import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.OwnerType.Organization
import com.readingbat.dsl.ReturnType.BooleanType
import com.readingbat.dsl.ReturnType.IntType
import com.readingbat.dsl.ReturnType.StringType
import com.readingbat.dsl.readingBatContent

// --8<-- [start:java_group]
val javaLanguageGroup =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      srcPath = "src/main/java"

      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple warmup problems"
        includeFiles = "*.java"
      }

      group("String-1") {
        packageName = "string1"
        description = "Basic string problems"
        includeFiles = "*.java"
      }

      group("Array-1") {
        packageName = "array1"
        description = "Basic array problems"
        includeFiles = "*.java"
      }
    }
  }
// --8<-- [end:java_group]

// --8<-- [start:python_group]
val pythonLanguageGroup =
  readingBatContent {
    repo = FileSystemSource("./")

    python {
      srcPath = "python"

      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple warmup problems"
        includeFilesWithType = "*.py" returns BooleanType
      }

      group("String-1") {
        packageName = "string1"
        description = "String manipulation challenges"
        includeFilesWithType = "*.py" returns StringType
      }

      group("List-1") {
        packageName = "list1"
        description = "List manipulation challenges"
        includeFilesWithType = "*.py" returns IntType
      }
    }
  }
// --8<-- [end:python_group]

// --8<-- [start:kotlin_group]
val kotlinLanguageGroup =
  readingBatContent {
    repo = FileSystemSource("./")

    kotlin {
      srcPath = "src/main/kotlin"

      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple warmup problems"
        includeFilesWithType = "*.kt" returns StringType
      }

      group("Functional-1") {
        packageName = "functional1"
        description = "Functional programming challenges"
        includeFilesWithType = "*.kt" returns IntType
      }
    }
  }
// --8<-- [end:kotlin_group]

// --8<-- [start:per_language_repo]
val perLanguageRepoContent =
  readingBatContent {
    java {
      repo = GitHubRepo(Organization, "readingbat", "readingbat-java-content")
      branchName = "main"

      group("Warmup-1") {
        packageName = "warmup1"
        includeFiles = "*.java"
      }
    }

    python {
      repo = GitHubRepo(Organization, "readingbat", "readingbat-python-content")
      branchName = "main"

      group("Warmup-1") {
        packageName = "warmup1"
        includeFilesWithType = "*.py" returns BooleanType
      }
    }
  }
// --8<-- [end:per_language_repo]
