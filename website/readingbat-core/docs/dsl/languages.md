---
icon: lucide/languages
---

# Language Groups

A `LanguageGroup` contains all challenge groups for a single programming language.
Each `ReadingBatContent` instance has three language groups: `java`, `python`, and `kotlin`.

## Language Properties

Each language has built-in defaults:

| Language | Suffix | Default `srcPath` | Quote Style |
|----------|--------|--------------------|-------------|
| Java | `.java` | `src/main/java` | Double quotes |
| Python | `.py` | `python` | Single quotes |
| Kotlin | `.kt` | `src/main/kotlin` | Double quotes |

## Java Language Group

Java challenges have the simplest syntax because return types are inferred from source code:

```kotlin
--8<-- "LanguageGroupExamples.kt:java_group"
```

Java groups use `includeFiles` (not `includeFilesWithType`) because the return type is
derived from the method signature at parse time.

## Python Language Group

Python challenges require explicit return types via the `returns` infix function:

```kotlin
--8<-- "LanguageGroupExamples.kt:python_group"
```

!!! warning "Python requires `includeFilesWithType`"
    Using `includeFiles` for Python challenges will throw an error. Python's dynamic
    typing means the return type cannot be inferred from source code alone.

## Kotlin Language Group

Kotlin challenges also require explicit return types:

```kotlin
--8<-- "LanguageGroupExamples.kt:kotlin_group"
```

## Per-Language Repository

Each language group can specify its own repository source, independently of the
top-level `repo` setting:

```kotlin
--8<-- "LanguageGroupExamples.kt:per_language_repo"
```

## Configurable Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `repo` | `ContentRoot` | Inherited from `ReadingBatContent.repo` | Content source (local or GitHub) |
| `branchName` | `String` | Inherited from `ReadingBatContent.branchName` | Git branch for remote content |
| `srcPath` | `String` | Language-specific default | Source directory path within the repo |
