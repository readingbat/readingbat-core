# Testcontainers + Docker Desktop Setup (macOS)

Testcontainers is used in integration tests (e.g., `EnrollmentTest`) to run PostgreSQL in a Docker container,
through `TestDatabase` in `readingbat-kotest`. With Docker Desktop running, the tests need no setup: no
`~/.testcontainers.properties`, no environment variables, and no IntelliJ run configuration changes.

## Prerequisites

- Docker Desktop installed and running
- Docker CLI working (`docker version` should succeed)

## How Testcontainers finds Docker

Testcontainers uses `DOCKER_HOST` (or `docker.host` in `~/.testcontainers.properties`) when one is set, and
otherwise looks for a socket, starting with `/var/run/docker.sock`. On a Mac where that path links to Docker
Desktop's `~/.docker/run/docker.sock`, it connects with no configuration at all.

Ryuk, Testcontainers' cleanup container, runs by default and removes the PostgreSQL container when the test
JVM exits, even if the JVM is killed.

## Troubleshooting

### "Could not find a valid Docker environment"

- Verify Docker Desktop is running: `docker version`
- Point Testcontainers at Docker Desktop's socket explicitly, either in your shell or IntelliJ run
  configuration:
  ```bash
  export DOCKER_HOST=unix://$HOME/.docker/run/docker.sock
  ```
  or in `~/.testcontainers.properties`, with your absolute home directory path:
  ```properties
  docker.host=unix:///Users/yourname/.docker/run/docker.sock
  ```

### "client version 1.32 is too old"

Testcontainers pings Docker at API 1.44 first and retries at 1.32 only when that ping fails, so this
error means Docker never answered the first ping and the retry was rejected. Fix the connection, not
the version: see "Could not find a valid Docker environment" above. Pinning `api.version` does not
help, because the 1.32 retry applies to a pinned version too.

### Settings left over from the old setup

Earlier versions of this page had you link Docker Desktop's raw engine socket
(`~/Library/Containers/com.docker.docker/Data/docker.raw.sock`) to `~/.docker/docker-raw.sock`, point
`docker.host` at it, disable Ryuk because it could not mount that socket, and pin the Docker API version
to 1.44. None of that is needed any more: Docker Desktop's regular socket works with Testcontainers, Ryuk
works through it, and Testcontainers picks the API version itself.

If you followed the old steps, remove these lines from `~/.testcontainers.properties` and delete the
symlink with `rm ~/.docker/docker-raw.sock`:

```properties
docker.host=unix:///Users/yourname/.docker/docker-raw.sock
ryuk.disabled=true
```

`testcontainers.reuse.enable=true`, which the old page also recommended, is harmless but does nothing
here, because `TestDatabase` never opts in with `withReuse(true)`. Testcontainers writes a
`docker.client.strategy` line into the file itself; leave that one alone.

## Version Notes

Verified with:

- Docker Desktop 4.96.2 (Docker Engine 29.9.2, API 1.56)
- Testcontainers 2.0.5 (docker-java 3.7.1)
- macOS (Apple Silicon)

With no `~/.testcontainers.properties` and no environment variables, and again with `DOCKER_HOST` set to
`~/.docker/run/docker.sock`, Testcontainers connected, started Ryuk, and ran PostgreSQL, and no
containers were left behind.

The raw-socket setup this page used to describe was written for Docker Desktop 4.63.0 and
Testcontainers 1.21.1.
