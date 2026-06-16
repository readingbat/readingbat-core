---
icon: lucide/tag
---

# Release Notes

For the complete, commit-level history see [`CHANGELOG.md`](https://github.com/readingbat/readingbat-core/blob/master/CHANGELOG.md) in the repository.

## v3.2.0 — 2026-06-15

Security-hardening release. A multi-agent security review surfaced 48 confirmed findings (7 high, 19 medium, 22 low); all 48 are addressed here, alongside a batch of WebSocket/caching reliability fixes and the 3.1.9-era build/tooling cleanup.

!!! warning "Upgrade note — `SESSION_SECRET` is now required in production"

    Session cookies are now signed and encrypted, so the server **will not start
    in production without a `SESSION_SECRET`**. Generate one with
    `openssl rand -hex 32`, set it via the `SESSION_SECRET` environment variable,
    use the **same value on every node**, and note that **rotating it invalidates
    all existing sessions**. See [Configuration › Secrets](configuration/index.md#secrets).

### Security

- **Signed + encrypted session cookies** (`SessionTransportTransformerEncrypt`, AES-128 + HMAC-SHA256). The cookies were previously unsigned plaintext, allowing the `userId` to be forged for a trivial authentication bypass and admin takeover.
- **Server-side code injection (RCE) removed** — answer checking no longer evaluates the user's response as a script; list/array answers are parsed and compared directly.
- **Teacher IDOR closed** — class-management actions verify class ownership.
- **XSS fixes** — student answers over WebSockets are HTML-escaped; user-controlled names in `confirm()`/`onSubmit` handlers are escaped.
- **OAuth hardening** — Google and GitHub logins require a verified email; login rotates the browser session to prevent fixation.
- **Rate limiting enforced** per-IP; secrets masked in logs; the operational logging WebSocket requires admin.

### Reliability

- **WebSockets** — fixed pinger concurrent-modification crashes, removed a shared dispatcher that blocked all clients, collapsed N+1 blocking JDBC, and made the answer dashboard drop-oldest under backpressure.
- **Caching** — geo cache short-circuits the DB, drops its global mutex, stops caching failures permanently, and is size-bounded; a dir-contents cache key mismatch is fixed; per-user answer channels are bounded.
- **Parsing & startup** — nested-brace Kotlin script conversion, Java invocation ordering, quote-aware Python list parsing, a non-numeric env-var startup crash, and a bounded answer-check loop.
- **`Challenge.functionInfo()` is now `suspend`** — the blocking script eval runs on `Dispatchers.IO` instead of bridging through `runBlocking` inside request/WS coroutines.

### Dependencies

common-utils → 2.9.2 · Kotlin 2.4.0 · Ktor 3.5.0 · Exposed 1.3.0 · Kotest 6.2.0 · detekt 2.0.0-alpha.4 · HikariCP 7.1.0 · prometheus-proxy 3.2.0

[Full changelog: 3.1.8…3.2.0](https://github.com/readingbat/readingbat-core/compare/3.1.8...3.2.0)
