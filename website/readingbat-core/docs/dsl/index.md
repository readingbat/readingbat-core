---
icon: lucide/code
---

# Content DSL

The Content DSL is the core innovation of ReadingBat. It provides an expressive Kotlin DSL for
defining programming challenges that are parsed and evaluated at runtime via JSR-223 script engines.

## Overview

Content is defined in a `Content.kt` file using the `readingBatContent { }` builder function.
The DSL supports three programming languages and can load challenge source files from local
directories or remote GitHub repositories.

### Minimal Example

```kotlin
--8<-- "ContentDslExamples.kt:basic_content"
```

### DSL Hierarchy

The DSL is organized as a four-level hierarchy:

| Level | Class | Purpose |
|-------|-------|---------|
| 1 | `ReadingBatContent` | Top-level container; holds all language groups |
| 2 | `LanguageGroup<T>` | Groups challenges for a single language (Java, Python, Kotlin) |
| 3 | `ChallengeGroup<T>` | A named set of challenges (e.g., "Warmup-1") |
| 4 | `Challenge` | An individual programming challenge |

### How It Works

1. The DSL file is read and evaluated as a Kotlin script
2. The resulting `ReadingBatContent` object is validated
3. Challenge source files are fetched (locally or from GitHub)
4. Source code is parsed to extract function bodies and test invocations
5. Test invocations are evaluated via script engines to compute correct answers
6. The web server presents challenges and checks user answers in real time

## Content Sources

Content can be loaded from two types of sources:

### Local Filesystem

Use `FileSystemSource` to load challenges from your local project:

```kotlin
--8<-- "ContentDslExamples.kt:basic_content"
```

The `FileSystemSource("./")` points to the project root directory. Each language group's `srcPath`
property determines the subdirectory where source files are located.

### GitHub Repositories

Use `GitHubRepo` to load challenges from a GitHub repository:

```kotlin
--8<-- "ContentDslExamples.kt:github_content"
```

The `ownerType` parameter distinguishes between GitHub organizations and individual users:

```kotlin
--8<-- "ContentDslExamples.kt:github_user_content"
```

### Mixed Sources

Different languages can use different content sources within the same `ReadingBatContent`:

```kotlin
--8<-- "ContentDslExamples.kt:mixed_sources"
```

!!! info "Default Inheritance"
    The `repo` and `branchName` set at the `ReadingBatContent` level serve as defaults.
    Each `LanguageGroup` can override these with its own values.

## DSL Marker

The `@ReadingBatDslMarker` annotation restricts implicit receiver scope within DSL blocks,
preventing accidental access to outer-level properties. This is standard Kotlin DSL design
that keeps the builder type-safe and unambiguous.

## What's Next

- [Language Groups](languages.md) -- Configure per-language settings and content sources
- [Challenge Groups](groups.md) -- Organize challenges into named sections
- [Challenges](challenges.md) -- Define individual challenges with descriptions and metadata
- [Return Types](return-types.md) -- Understand the type system for challenge answers
- [Content Inclusion](content-inclusion.md) -- Merge content from multiple sources
