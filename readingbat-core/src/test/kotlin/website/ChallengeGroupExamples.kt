@file:Suppress("unused")

package website

import com.pambrose.common.util.FileSystemSource
import com.readingbat.dsl.ReturnType.BooleanType
import com.readingbat.dsl.ReturnType.IntArrayType
import com.readingbat.dsl.ReturnType.IntType
import com.readingbat.dsl.ReturnType.StringArrayType
import com.readingbat.dsl.ReturnType.StringType
import com.readingbat.dsl.readingBatContent

// --8<-- [start:include_files_java]
val javaIncludeFiles =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple warmup problems"

        // Include all Java files from the warmup1 package
        includeFiles = "*.java"
      }
    }
  }
// --8<-- [end:include_files_java]

// --8<-- [start:include_files_python]
val pythonIncludeFiles =
  readingBatContent {
    repo = FileSystemSource("./")

    python {
      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple Python warmup problems"

        // Python requires an explicit return type
        includeFilesWithType = "*.py" returns BooleanType
      }
    }
  }
// --8<-- [end:include_files_python]

// --8<-- [start:include_files_kotlin]
val kotlinIncludeFiles =
  readingBatContent {
    repo = FileSystemSource("./")

    kotlin {
      group("Warmup-1") {
        packageName = "warmup1"
        description = "Simple Kotlin warmup problems"

        // Kotlin also requires an explicit return type
        includeFilesWithType = "*.kt" returns StringType
      }
    }
  }
// --8<-- [end:include_files_kotlin]

// --8<-- [start:individual_challenges]
val individualChallenges =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      group("Warmup-1") {
        packageName = "warmup1"

        // Add challenges one at a time
        challenge("SleepIn")
        challenge("MonkeyTrouble")
        challenge("SumDouble")
      }
    }

    python {
      group("Warmup-1") {
        packageName = "warmup1"

        // Python challenges need a return type
        challenge("sleep_in") { returnType = BooleanType }
        challenge("monkey_trouble") { returnType = BooleanType }
        challenge("sum_double") { returnType = IntType }
      }
    }

    kotlin {
      group("Warmup-1") {
        packageName = "warmup1"

        // Kotlin challenges also need a return type
        challenge("sleepIn") { returnType = BooleanType }
        challenge("monkeyTrouble") { returnType = BooleanType }
        challenge("sumDouble") { returnType = IntType }
      }
    }
  }
// --8<-- [end:individual_challenges]

// --8<-- [start:challenge_description]
val challengeWithDescription =
  readingBatContent {
    repo = FileSystemSource("./")

    java {
      group("String-1") {
        packageName = "string1"
        description = "Basic **string** problems using `charAt()` and `substring()`"

        challenge("HelloName") {
          description = "Return a greeting with the given name"
          codingBatEquiv = "p141943"
        }
        challenge("MakeAbba") {
          description = "Combine two strings in *abba* pattern"
          codingBatEquiv = "p161056"
        }
      }
    }
  }
// --8<-- [end:challenge_description]

// --8<-- [start:multiple_return_types]
val multipleReturnTypes =
  readingBatContent {
    repo = FileSystemSource("./")

    kotlin {
      group("Mixed-Types") {
        packageName = "mixed"

        challenge("isEven") { returnType = BooleanType }
        challenge("doubleIt") { returnType = IntType }
        challenge("greeting") { returnType = StringType }
        challenge("reverseArray") { returnType = IntArrayType }
        challenge("splitWords") { returnType = StringArrayType }
      }
    }
  }
// --8<-- [end:multiple_return_types]

// --8<-- [start:multiple_include_patterns]
val multipleIncludePatterns =
  readingBatContent {
    repo = FileSystemSource("./")

    python {
      group("Mixed-1") {
        packageName = "mixed1"
        description = "Problems with different return types"

        // Multiple includeFilesWithType lines for different patterns
        includeFilesWithType = "bool_*.py" returns BooleanType
        includeFilesWithType = "int_*.py" returns IntType
        includeFilesWithType = "str_*.py" returns StringType
      }
    }
  }
// --8<-- [end:multiple_include_patterns]
