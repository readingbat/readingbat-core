# Testcontainers + Docker Desktop Setup (macOS)

Testcontainers is used in integration tests (e.g., `EnrollmentTest`) to run PostgreSQL in a Docker container.
Docker Desktop 4.x on macOS requires additional configuration due to API version mismatches and
sandboxed socket paths.

## Prerequisites

- Docker Desktop installed and running
- Docker CLI working (`docker version` should succeed)

## Setup Steps

### 1. Create a symlink to Docker's raw engine socket

Docker Desktop exposes two sockets:

- **CLI proxy socket** (`~/.docker/run/docker.sock`) — used by the Docker CLI, but returns empty
  API responses that break Testcontainers' docker-java library.
- **Raw engine socket** (`~/Library/Containers/com.docker.docker/Data/docker.raw.sock`) — the actual
  Docker Engine API, which works correctly with docker-java.

The raw socket is inside Docker Desktop's macOS sandbox container directory. Accessing it directly
from apps like IntelliJ or terminal emulators triggers macOS permission prompts. Creating a symlink
in a non-sandboxed location avoids this:

```bash
ln -sf ~/Library/Containers/com.docker.docker/Data/docker.raw.sock ~/.docker/docker-raw.sock
```

This symlink persists across reboots. Verify it works:

```bash
curl -s --unix-socket ~/.docker/docker-raw.sock http://localhost/info | python3 -c "import sys,json; d=json.load(sys.stdin); print('ServerVersion:', d.get('ServerVersion'))"
```

### 2. Create `~/.testcontainers.properties`

```properties
testcontainers.reuse.enable=true
docker.host=unix://<absolute-path-to-home>/.docker/docker-raw.sock
ryuk.disabled=true
```

Replace `<absolute-path-to-home>` with your actual home directory path (e.g., `/Users/yourname`).

**What each property does:**

| Property | Purpose |
|---|---|
| `testcontainers.reuse.enable=true` | Reuses containers across test runs for faster iteration |
| `docker.host` | Points Testcontainers to the raw Docker socket instead of the CLI proxy |
| `ryuk.disabled=true` | Disables Ryuk (cleanup container) which fails to mount the raw socket inside the Docker VM |

### 3. Gradle test configuration (already in repo)

The following is already configured in `build.gradle.kts` and requires no manual setup:

```kotlin
tasks.test {
    environment("DOCKER_API_VERSION", "1.44")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    jvmArgs("-Dapi.version=1.44")
}
```

This pins the Docker API version to 1.44 because docker-java 3.4.2 (bundled with Testcontainers 1.21.1)
defaults to API version 1.32, which Docker Desktop 4.x rejects (minimum supported is 1.44).

## Running in IntelliJ

When running tests from IntelliJ, the JVM args from Gradle may not apply. Add these to your
IntelliJ run configuration's VM options if needed:

```
-Dapi.version=1.44
```

And these environment variables:

```
DOCKER_API_VERSION=1.44
TESTCONTAINERS_RYUK_DISABLED=true
```

## Troubleshooting

### "Could not find a valid Docker environment"

- Verify Docker Desktop is running: `docker version`
- Verify the symlink exists and works:
  ```bash
  ls -la ~/.docker/docker-raw.sock
  curl -s --unix-socket ~/.docker/docker-raw.sock http://localhost/info
  ```
- Check `~/.testcontainers.properties` has the correct `docker.host` path

### "client version 1.32 is too old"

The API version pin isn't reaching the JVM. Ensure `jvmArgs("-Dapi.version=1.44")` is set in the
Gradle test task or IntelliJ run configuration.

### "error while creating mount source path ... docker.raw.sock"

Ryuk is trying to mount the raw socket. Ensure `TESTCONTAINERS_RYUK_DISABLED=true` is set.

### macOS "would like to access data from other apps" prompt

The symlink is either missing or still pointing to a path inside
`~/Library/Containers/com.docker.docker/`. Recreate it:

```bash
ln -sf ~/Library/Containers/com.docker.docker/Data/docker.raw.sock ~/.docker/docker-raw.sock
```

## Version Notes

This setup was tested with:

- Docker Desktop 4.63.0 (Docker Engine 29.2.1, API 1.53)
- Testcontainers 1.21.1 (docker-java 3.4.2)
- macOS (Apple Silicon)

The API version pin may become unnecessary with a future Testcontainers release that bundles
a docker-java version supporting API >= 1.44.
