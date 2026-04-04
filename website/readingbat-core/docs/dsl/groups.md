---
icon: lucide/folder
---

# Challenge Groups

A `ChallengeGroup` is a named collection of programming challenges within a language.
Groups correspond to a package or directory of source files and appear as sections on
the language page (e.g., "Warmup-1", "String-2").

## Creating Groups

Groups are created inside a language block using the `group()` function:

```kotlin
--8<-- "ChallengeGroupExamples.kt:include_files_java"
```

## Including Files

### Java: `includeFiles`

For Java challenges, use `includeFiles` with a glob pattern. The return type is inferred
from the Java source code:

```kotlin
--8<-- "ChallengeGroupExamples.kt:include_files_java"
```

### Python: `includeFilesWithType`

Python challenges require the `includeFilesWithType` property with the `returns` infix function:

```kotlin
--8<-- "ChallengeGroupExamples.kt:include_files_python"
```

### Kotlin: `includeFilesWithType`

Kotlin follows the same pattern as Python:

```kotlin
--8<-- "ChallengeGroupExamples.kt:include_files_kotlin"
```

### Multiple Include Patterns

You can use multiple `includeFilesWithType` assignments to include files with different
return types in the same group:

```kotlin
--8<-- "ChallengeGroupExamples.kt:multiple_include_patterns"
```

## Individual Challenges

Instead of (or in addition to) bulk file inclusion, you can add challenges one at a time:

```kotlin
--8<-- "ChallengeGroupExamples.kt:individual_challenges"
```

## Descriptions

Both groups and individual challenges support Markdown descriptions:

```kotlin
--8<-- "ChallengeGroupExamples.kt:challenge_description"
```

!!! tip "Source code descriptions"
    Challenges can also derive descriptions from special `@desc` comments in their
    source files. A `// @desc Return true when...` comment at the top of a Java file
    will be used as the description unless one is explicitly set in the DSL.

## Group Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `packageName` | `String` | `""` | Package path for locating source files |
| `description` | `String` | `""` | Markdown description shown at the top of the group page |
| `includeFiles` | `String` | -- | Glob pattern for Java file inclusion |
| `includeFilesWithType` | `PatternReturnType` | -- | Pattern + return type for Python/Kotlin |

## Multiple Return Types in One Group

A single group can contain challenges with different return types when added individually:

```kotlin
--8<-- "ChallengeGroupExamples.kt:multiple_return_types"
```
