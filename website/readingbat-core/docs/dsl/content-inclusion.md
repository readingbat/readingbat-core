---
icon: lucide/git-merge
---

# Content Inclusion

ReadingBat supports several mechanisms for composing content from multiple sources.
This is useful when you want to combine your own challenges with community-contributed
content or split large challenge sets across repositories.

## Merging Language Groups with Unary Plus

Use the `+` operator to merge all challenge groups from an external language group
into the current content:

```kotlin
--8<-- "ContentInclusionExamples.kt:unary_plus_language"
```

The `+externalPython.python` line merges all Python challenge groups from
`externalPython` into the current `ReadingBatContent`.

## Including with Name Prefixes

When merging content that might have conflicting group names, use the `include()`
function with a `namePrefix` parameter:

```kotlin
--8<-- "ContentInclusionExamples.kt:include_with_prefix"
```

The community "Warmup-1" group becomes "Community-Warmup-1", avoiding collision
with your own "My-Warmup" group.

## Conditional Content

Since the DSL is plain Kotlin code, you can use conditionals to vary content
based on environment:

```kotlin
--8<-- "ContentInclusionExamples.kt:conditional_content"
```

The `isProduction()` and `isTesting()` functions are also available within DSL
files for this purpose:

```kotlin
if (isProduction()) {
  // Include production-only groups
}

if (isTesting()) {
  // Include test-only groups
}
```

## Remote Content Loading

For content defined in remote `Content.kt` files, you can load and evaluate remote
DSL scripts using the `ContentSource` extension method. This fetches the remote file,
evaluates it as a Kotlin script, and returns the resulting `ReadingBatContent`:

```kotlin
val remote = GitHubContent(
  ownerType = Organization,
  ownerName = "readingbat",
  repoName = "readingbat-java-content",
  fileName = "Content.kt",
)

// Load the remote DSL and merge its Java content
val externalContent = remote.evaluate(this, "content")
include(externalContent.java)
```

!!! note
    The second parameter (`"content"`) is the variable name in the remote script that
    holds the `ReadingBatContent` result. The method fetches the file, runs it as a
    Kotlin script, and extracts the content variable.

## Inclusion Methods Summary

| Method | Use Case |
|--------|----------|
| `+languageGroup` | Merge an entire language group's challenges |
| `include(languageGroup, prefix)` | Merge with a name prefix to avoid collisions |
| `remote.evaluate(this)` | Load and evaluate a remote Content.kt file |
| Conditional logic | Vary content based on environment or configuration |
