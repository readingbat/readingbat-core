@file:Suppress("unused")

package website

import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.OwnerType.Organization
import com.pambrose.common.util.OwnerType.User
import com.readingbat.dsl.ReturnType.BooleanType
import com.readingbat.dsl.ReturnType.IntType
import com.readingbat.dsl.ReturnType.StringType
import com.readingbat.dsl.readingBatContent

// --8<-- [start:basic_content]
val basicContent =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple warmup problems to get started"
        includeFiles = "*.java"
      }
    }
  }
// --8<-- [end:basic_content]

// --8<-- [start:multi_language]
val multiLanguageContent =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      srcPath = "src/main/java"
      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple Java warmup problems"
        includeFiles = "*.java"
      }
      group("String-1") {
        packageName = "string1"
        description = "Basic string problems"
        includeFiles = "*.java"
      }
    }

    python {
      srcPath = "python"
      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple Python warmup problems"
        includeFilesWithType = "*.py" returns BooleanType
      }
    }

    kotlin {
      srcPath = "src/main/kotlin"
      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple Kotlin warmup problems"
        includeFilesWithType = "*.kt" returns StringType
      }
    }
  }
// --8<-- [end:multi_language]

// --8<-- [start:github_content]
val gitHubContent =
  readingBatContent {
    repo = GitHubRepo(Organization, "readingbat", "readingbat-java-content")
    branchName = "master"

    java {
      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple warmup problems"
        includeFiles = "*.java"
      }
    }
  }
// --8<-- [end:github_content]

// --8<-- [start:github_user_content]
val userRepoContent =
  readingBatContent {
    repo = GitHubRepo(User, "my-username", "my-challenges")
    branchName = "main"

    kotlin {
      srcPath = "src/main/kotlin"
      group("Basics") {
        packageName = "basics"
        includeFilesWithType = "*.kt" returns IntType
      }
    }
  }
// --8<-- [end:github_user_content]

// --8<-- [start:mixed_sources]
val mixedSourcesContent =
  readingBatContent {
    // Default repo for all languages
    repo = FileSystemSource("./")

    java {
      // Java uses the default local repo
      group("Warmup-1") {
        packageName = "warmup1"
        includeFiles = "*.java"
      }
    }

    python {
      // Python overrides with its own GitHub repo
      repo = GitHubRepo(Organization, "readingbat", "readingbat-python-content")
      group("Warmup-1") {
        packageName = "warmup1"
        includeFilesWithType = "*.py" returns BooleanType
      }
    }
  }
// --8<-- [end:mixed_sources]
