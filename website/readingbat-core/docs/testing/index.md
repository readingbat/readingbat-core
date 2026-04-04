---
icon: lucide/test-tube
---

# Testing

ReadingBat provides a dedicated testing module (`readingbat-kotest`) with utilities
for writing integration tests using [Kotest](https://kotest.io/).

## Test Module

The `readingbat-kotest` module provides the `TestSupport` object with DSL helpers
for iterating over content, checking answers, and setting up test applications.

## Setting Up Test Content

Define test content using the same DSL as production content:

```kotlin
--8<-- "TestSupportExamples.kt:test_content"
```

## Initializing Test Properties

Before running tests, initialize the test property system:

```kotlin
--8<-- "TestSupportExamples.kt:init_properties"
```

This sets `IS_PRODUCTION` to `false`, `IS_TESTING` to `true`, and marks
the property system as initialized.

## Iterating Over Content

The `TestSupport` object provides extension functions for iterating through
the content hierarchy:

```kotlin
--8<-- "TestSupportExamples.kt:iterate_content"
```

The iteration functions follow the DSL hierarchy:

| Function | Receiver | Iterates Over |
|----------|----------|---------------|
| `forEachLanguage` | `ReadingBatContent` | All language groups |
| `forEachGroup` | `LanguageGroup` | All challenge groups in a language |
| `forEachChallenge` | `ChallengeGroup` | All challenges in a group |
| `forEachFuncInfo` | `ChallengeGroup` | Function info for each challenge |

## Accessing Specific Challenges

Use convenience functions to access specific groups or challenges directly:

```kotlin
--8<-- "TestSupportExamples.kt:access_challenges"
```

### Available Accessors

| Function | Returns |
|----------|---------|
| `javaGroup(name)` | `ChallengeGroup<JavaChallenge>` |
| `pythonGroup(name)` | `ChallengeGroup<PythonChallenge>` |
| `kotlinGroup(name)` | `ChallengeGroup<KotlinChallenge>` |
| `javaChallenge(group, name)` | Applies block to `FunctionInfo` |
| `pythonChallenge(group, name)` | Applies block to `FunctionInfo` |
| `kotlinChallenge(group, name)` | Applies block to `FunctionInfo` |

## Answer Checking

### Check Individual Answers

```kotlin
val funcInfo = content.javaGroup("Warmup-1").functionInfo("SleepIn")

// Check a specific answer
val result = funcInfo.checkAnswer(index = 0, userResponse = "true")
result.shouldBeCorrect()

// Get a ChallengeAnswer for assertion-style checks
funcInfo.answerFor(0) shouldHaveAnswer true
funcInfo.answerFor(1) shouldNotHaveAnswer true
```

### Check All Answers via HTTP

For full integration testing against the HTTP endpoint:

```kotlin
// Submit the same answer for all invocations
challenge.answerAllWith(engine, "true") {
  // 'this' is a ChallengeResult
  println("Status: $answerStatus, Hint: $hint")
}

// Submit the correct answer for each invocation
challenge.answerAllWithCorrectAnswer(engine) {
  answerStatus shouldBe CORRECT
}
```

### Custom Matchers

| Matcher | Description |
|---------|-------------|
| `shouldBeCorrect()` | Asserts the answer result is correct |
| `shouldBeIncorrect()` | Asserts the answer result is incorrect |
| `shouldHaveAnswer(value)` | Asserts a `ChallengeAnswer` matches the given value |
| `shouldNotHaveAnswer(value)` | Asserts a `ChallengeAnswer` does not match |

## Test Application Setup

For HTTP-level integration tests, use `testModule()` to set up a Ktor test application:

```kotlin
class MyIntegrationTest : StringSpec() {
  init {
    "test challenge endpoint" {
      testApplication {
        application {
          testModule(testContent)
        }

        // Use the test client to make requests
        val response = client.get("/content/java/Test Cases/StringArrayTest1")
        response.status shouldBe HttpStatusCode.OK
      }
    }
  }
}
```

The `testModule()` function installs:

- All Ktor features (sessions, authentication, content negotiation)
- Admin, user, and system admin routes
- WebSocket routes
- Static resource serving

## Test Database

The `TestDatabase` object provides a Testcontainers-based PostgreSQL setup:

```kotlin
// Start a PostgreSQL test container and run Flyway migrations
val dataSource = TestDatabase.connectAndMigrate()
```

This uses a PostgreSQL 16 Alpine container and applies all migration scripts
from `src/main/resources/db/migration/`.

## Build Commands

```bash
# Run all tests
make tests
# or
./gradlew check

# Run a single test class
./gradlew :readingbat-core:test --tests "EndpointTest"

# Run a single test by name
./gradlew :readingbat-core:test --tests "EndpointTest.Simple endpoint tests"
```
