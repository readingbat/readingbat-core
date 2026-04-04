---
icon: lucide/type
---

# Return Types

The `ReturnType` enum defines the expected return type of a challenge function. Return types
are used for answer comparison and display formatting.

## Java vs. Python/Kotlin

- **Java**: Return types are inferred automatically from the method signature. Use `includeFiles`.
- **Python and Kotlin**: Return types must be specified explicitly. Use `includeFilesWithType` or
  set `returnType` on individual challenges.

## Available Types

### Scalar Types

```kotlin
--8<-- "ReturnTypeExamples.kt:scalar_types"
```

| Type | Type String | Example Values |
|------|-------------|----------------|
| `BooleanType` | `boolean` | `true`, `false` |
| `IntType` | `int` | `42`, `-1`, `0` |
| `FloatType` | `float` | `3.14`, `-0.5` |
| `StringType` | `String` | `"hello"`, `""` |
| `CharType` | `char` | `'a'`, `'Z'` |

### Array Types

```kotlin
--8<-- "ReturnTypeExamples.kt:array_types"
```

| Type | Type String | Example Values |
|------|-------------|----------------|
| `BooleanArrayType` | `boolean[]` | `[true, false, true]` |
| `IntArrayType` | `int[]` | `[1, 2, 3]` |
| `FloatArrayType` | `float[]` | `[1.0, 2.5]` |
| `StringArrayType` | `String[]` | `["a", "b", "c"]` |

### List Types

```kotlin
--8<-- "ReturnTypeExamples.kt:list_types"
```

| Type | Type String | Example Values |
|------|-------------|----------------|
| `BooleanListType` | `List<Boolean>` | `[true, false]` |
| `IntListType` | `List<Integer>` | `[1, 2, 3]` |
| `FloatListType` | `List<Float>` | `[1.0, 2.5]` |
| `StringListType` | `List<String>` | `["a", "b"]` |

### Runtime Type

The special `Runtime` type is used internally for Java challenges where the return type
is inferred from the source code. You should never need to set this explicitly.

## Using `returns` Infix Function

The `returns` infix function pairs a file glob pattern with a return type:

```kotlin
includeFilesWithType = "*.py" returns BooleanType
```

This is syntactic sugar for creating a `PatternReturnType(pattern, returnType)` pair.

## Multiple Return Types

A single group can contain challenges with different return types when challenges
are defined individually:

```kotlin
--8<-- "ChallengeGroupExamples.kt:multiple_return_types"
```

Or by using multiple `includeFilesWithType` assignments with different glob patterns:

```kotlin
--8<-- "ChallengeGroupExamples.kt:multiple_include_patterns"
```
