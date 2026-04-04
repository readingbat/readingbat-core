---
icon: lucide/file-code
---

# Challenges

A `Challenge` represents a single programming problem. It corresponds to a source file
containing a function and its test invocations. When loaded, the source code is parsed
to extract the function body, test cases, and correct answers.

## Challenge Types

There are three language-specific challenge subclasses:

| Type | Language | Return Type |
|------|----------|-------------|
| `JavaChallenge` | Java | Inferred from source |
| `PythonChallenge` | Python | Must be set explicitly |
| `KotlinChallenge` | Kotlin | Must be set explicitly |

## Defining Challenges

### Basic Definition

The simplest way to define a challenge:

```kotlin
--8<-- "ChallengeGroupExamples.kt:individual_challenges"
```

### With Descriptions and Metadata

Challenges can include descriptions and CodingBat equivalents:

```kotlin
--8<-- "ChallengeGroupExamples.kt:challenge_description"
```

### Custom File Names

By default, the file name is derived from the challenge name plus the language suffix.
You can override this:

```kotlin
--8<-- "ContentInclusionExamples.kt:challenge_file_name"
```

### CodingBat Equivalents

Link challenges to their CodingBat counterparts:

```kotlin
--8<-- "ContentInclusionExamples.kt:coding_bat_equiv"
```

## Source File Convention

Challenge source files follow a specific convention where the `main()` method contains
`println()` (or `print()` for Python) calls that serve as test invocations:

=== "Java"

    ```java
    public class SleepIn {
      public static boolean sleepIn(boolean weekday, boolean vacation) {
        return !weekday || vacation;
      }

      public static void main(String[] args) {
        System.out.println(sleepIn(false, false));
        System.out.println(sleepIn(true, false));
        System.out.println(sleepIn(false, true));
      }
    }
    ```

=== "Python"

    ```python
    def sleep_in(weekday, vacation):
        return not weekday or vacation

    def main():
        print(sleep_in(False, False))
        print(sleep_in(True, False))
        print(sleep_in(False, True))

    if __name__ == "__main__":
        main()
    ```

=== "Kotlin"

    ```kotlin
    fun sleepIn(weekday: Boolean, vacation: Boolean): Boolean {
      return !weekday || vacation
    }

    fun main() {
      println(sleepIn(false, false))
      println(sleepIn(true, false))
      println(sleepIn(false, true))
    }
    ```

The framework:

1. Extracts the function body (displayed to the user)
2. Extracts the `println` calls as test invocations
3. Evaluates the code to compute correct answers
4. Compares user answers against the computed results

## Source-Based Descriptions

Add `@desc` comments at the top of source files to provide descriptions without
modifying the DSL:

=== "Java"

    ```java
    // @desc Return true when you can sleep in
    public class SleepIn {
      // ...
    }
    ```

=== "Python"

    ```python
    # @desc Return true when you can sleep in
    def sleep_in(weekday, vacation):
        # ...
    ```

=== "Kotlin"

    ```kotlin
    // @desc Return true when you can sleep in
    fun sleepIn(weekday: Boolean, vacation: Boolean): Boolean {
      // ...
    }
    ```

!!! note
    Descriptions set explicitly in the DSL take precedence over `@desc` comments
    in source files.

## Challenge Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `fileName` | `String` | `"$challengeName.$suffix"` | Source file name |
| `description` | `String` | `""` | Markdown description (or from `@desc` comments) |
| `codingBatEquiv` | `String` | `""` | CodingBat problem identifier |
| `returnType` | `ReturnType` | Required for Python/Kotlin | Expected return type |

## Challenge Identification

Each challenge is uniquely identified by an MD5 hash computed from its language name,
group name, and challenge name. This hash is used for tracking user progress and
answer history.
