---
icon: lucide/tag
---

# Release Notes

For the complete, commit-level history see [`CHANGELOG.md`](https://github.com/readingbat/readingbat-core/blob/master/CHANGELOG.md) in the repository.

## v3.4.0 — 2026-09-21

Rendering-correctness and maintenance release, with one breaking API change. The language tabs did not read as tabs in Safari, the rule beneath them stopped short of both screen edges, and the app depended on a CDN for images it already carried in its own jar. No configuration or upgrade steps for the app itself; `Endpoints.STATIC_ROOT` has been removed, which is source-breaking for anything compiled against the published artifact.

### Rendering

- **The selected tab is open in every browser** — the folder-tab effect works by having the selected tab paint its white background over the 1px divider below the strip, nudged down with `top: 1px`. That only lands if the tab's painted box ends exactly where the divider begins, and `nav li` was `display: inline`, so its height came from the font's ascent plus descent. Blink rounds that to whole pixels and covered the rule exactly; WebKit leaves it fractional — box bottom **159.64** against a divider at **159–160** — so 0.36px of black survived, about 0.7 device pixels at 2×. A line that looks thinner but never disappears. The tabs are now bottom-aligned inline-blocks, whose box ends at the line box bottom, which is where the divider starts, in every engine.
- **The rule beneath the tabs runs edge to edge** — it is a block inside `<body>`, whose 8px margin clipped it at both ends. It now carries a negative margin that cancels that gutter. A structural rule that stops short of the edge reads as a mistake rather than as a margin.
- **The page no longer scrolls sideways** — the tab-strip container was `min-width: 100vw`, and `100vw` counts the scrollbar: 1665px inside a 1650px viewport, 23px of stray horizontal scroll. It also undid the fix above the moment you scrolled right.
- **The tab strip is inset from the page edge** by 37px — one tab gap — so the first tab is spaced from the edge the way the tabs are spaced from each other.

### Static Assets

- **Served from the jar, not a CDN** — all 29 assets were already packaged in the jar, but `Endpoints.STATIC_ROOT` was an absolute CDN URL and `staticResources` mounts the tree at whatever that constant is, so the route has been unreachable since 1.3.0. Nothing noticed because a missing asset returns the not-found page with a **200** status. They now serve from `/static` with a one-year cache, an ETag, and a `?v=<version>` on every URL so a replaced image is never stranded behind a warm cache.
- **The CDN is now optional** — `STATIC_URL_PREFIX` (env var, or `readingbat.site.staticUrlPrefix`) sets the prefix pages emit. Point it at a CDN origin to restore the old behavior with no rebuild; the app keeps serving the files itself either way.
- **`Endpoints.STATIC_ROOT` is gone** — it conflated the mount path with the emitted URL prefix, which is what made the route unreachable. Replaced by `Endpoints.STATIC_PATH` and the new property. Deleted rather than redefined, so call sites fail loudly instead of silently changing meaning.
- **`site.webmanifest` icons fixed** — root-relative srcs against files under `icons/`: 404 from the app, already 403 on the CDN. Now relative, resolving under any prefix.
- **Images are no longer compressed** — `deflate` declared its own condition, which under Ktor's rules opted it out of the default image/video/audio exclusions, and its priority put it ahead of gzip. Every response, images included, went through it.

### Testing

- **The suite now tests in two engines** — `PlaywrightTabsTest` asserts both promises the tab strip makes (the selected tab covers the divider; the divider spans the viewport with no overflow) in Chromium *and* WebKit. The hairline was invisible to Chromium at every font size probed, so a Chromium-only test could never have caught it. These are the first WebKit tests in the project.

### Changed

- **The design record covers the tab geometry** — `DESIGN.md` now states why the tabs must be bottom-aligned inline-blocks, why the rule runs full-bleed, and where the 37px inset comes from.
- **Line endings are normalized in the index** — `.gitattributes` gained `* text=auto` and dropped the `binary` attribute from `gradlew` and `*.bat`, which had been suppressing both diffs and the `eol` conversion those same lines asked for. The generated `static/tailwind.css` and the vendored `static/prism/**` are now marked so GitHub collapses them in diffs.

### Dependencies

Gradle 9.6.1 → 9.7.1 · Ktor 3.5.2 → 3.6.0 · Exposed 1.3.1 → 1.5.0 · Flyway 13.1.0 → 13.7.0 · Kotest 6.2.3 → 6.2.5 · Playwright 1.61.0 → 1.63.0 · Cloud SQL socket factory 1.29.0 → 1.30.0 · Resend 4.13.0 → 4.26.0 · prometheus-proxy 4.0.0 → 4.0.1 · detekt alpha.5 → alpha.6 · buildconfig 6.0.10 → 6.1.1 · versions plugin 0.57.0 → 0.64.0 · zensical 0.0.52 → 0.0.63

[Full changelog: 3.3.1…3.4.0](https://github.com/readingbat/readingbat-core/compare/3.3.1...3.4.0)

## v3.3.1 — 2026-08-01

Accessibility, performance, and maintenance release. The headline fix is that answer grading — the single most important thing the app tells a student — was communicated by fill color alone and was never announced to assistive technology. No configuration or upgrade steps — a drop-in bump from 3.3.0.

### Accessibility

- **Answer grading is perceivable without color or sight** — `checkAnswers` painted each result cell green or red and did nothing else, so a screen-reader user received no result at all and a colorblind user was left comparing two fills measuring **1.36:1** against each other, far below the 3:1 WCAG 2.1 AA non-text minimum and a 1.4.1 (Use of Color) failure. Result cells now carry `✓ correct` / `✗ try again` text, and the status cell is a `role="status"` / `aria-live="polite"` region announcing `N of M correct.`
- **Controls have accessible names** — answer inputs gained `aria-label="Return value for <invocation>"`, the like/dislike buttons gained labels with their images marked decorative, and the sign-in modal's close control (a `span` with an `onClick`, unreachable by keyboard) became a real `button`.
- **Text colors pass AA** — the pass/fail colors met contrast as fills but not as text, so `rb-correct-text` (`#3E862E`) and `rb-wrong-text` (`#ED0000`) were added as separate text tokens and `rb-header` was darkened to `#337E9C`. Re-toning the tokens alone would have changed nothing: `HEADER_COLOR` was emitted as an inline style on the same elements, so the inline value won. That constant and all 13 call sites are gone.
- **Link hover no longer turns red**, which collided with the wrongness signal — it is now an underline.
- **Spinners that were never visible now work** — the like/dislike and admin spinners used `fa-spin`, but Font Awesome is not loaded on those pages. Replaced with a CSS `.rb-spinner` that honors `prefers-reduced-motion`.
- The page wordmark is now an `h1`, the language nav carries `aria-label="Languages"`, and pages emit `<meta charset>` and a viewport tag.

### Performance

- **Images got much lighter** — the four like/dislike PNGs were 1600px wide and render at 30px (**345 KB → 12 KB**), and `nervous`/`panic` moved to JPEG (**~450 KB → ~72 KB**).
- All six `img` sites gained explicit `width`/`height`, removing layout shift, plus `loading` hints.

### Changed

- **The generated stylesheet can no longer drift unnoticed** — `static/tailwind.css` is checked in, but the Gradle wiring only regenerates it on macOS, so the accessibility pass left it stale, still carrying CSS for classes nothing emits. It has been rebuilt (80,540 → 78,905 bytes), and a new `Tailwind CSS` workflow now regenerates it in CI and fails if the committed artifact does not match.
- **A design-system record** — `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` capture the product context and the design system the accessibility work was audited against.
- **Documentation accuracy** — `make dbreset` was referenced in four places but has never existed; all now name the real Flyway targets (`dbmigrate`, `dbclean`, `dbinfo`, `dbvalidate`). `CLAUDE.md` also shed 57 lines of content derivable from the codebase itself.
- The versions plugin is applied by its catalog id rather than a hardcoded legacy string, clearing a Gradle deprecation warning.

### Dependencies

Ktor 3.5.1 → 3.5.2 · Flyway 13.0.0 → 13.1.0 · common-utils 3.2.1 → 3.2.2 · versions plugin 0.54.0 → 0.57.0 · zensical 3.10.2 → 3.10.3 · markdown 0.0.51 → 0.0.52

[Full changelog: 3.3.0…3.3.1](https://github.com/readingbat/readingbat-core/compare/3.3.0...3.3.1)

## v3.3.0 — 2026-07-26

Modernization + maintenance release. Adopts Kotlin's experimental collection-literal syntax across the codebase, refreshes dependencies (with major bumps to Flyway 13, prometheus-proxy 4.0, and common-utils 3.x), and lands a handful of code-quality cleanups. No configuration or upgrade steps — a drop-in bump from 3.2.1.

### Changed

- **Kotlin collection literals** — enabled the experimental `-Xcollection-literals` compiler flag and converted 191 call sites across `src` and `test`: `emptyList()` → `[]`, `listOf(...)` → `[...]`, and `mutableListOf(...)` → `[...]`, the last with an explicit `MutableList<T>` type on the declaration so mutability is never inferred away. The flag scopes to project sources only — the JSR-223 engine that evaluates content DSL files at runtime is unaffected, so DSL content keeps using `listOf()`/`mutableListOf()`.
- **Build-script structure** — the Dokka configuration moved into a root-level `configureDokka()` helper, and `configureVersions()` moved into an `allprojects {}` block.
- **Code-quality cleanups** — removed redundant imports of symbols defined within a file's own object/companion (five files), and simplified an early-return conditional in `Intercepts.isBrowsableContentPath` (`if (x) return true; return y` → `return x || y`, behavior-preserving).

### Fixed

- **Dokka is warning-free** — the unresolved `[initProperties]` KDoc link in `ContentDsl.kt` referenced a `Property` companion member that was not in scope, so it rendered as plain text. It now uses the fully-qualified custom-link-text form.

### Dependencies

Kotlin 2.4.0 → 2.4.10 · common-utils 2.9.3 → 3.2.1 · prometheus-proxy 3.2.0 → 4.0.0 · Flyway 12.10.0 → 13.0.0 · Kotest 6.2.1 → 6.2.3 · Logback 1.5.18 → 1.5.38 · PostgreSQL driver 42.7.12 → 42.7.13 · Cloud SQL socket factory 1.28.6 → 1.29.0 · Kotlinter 5.5.0 → 5.6.0 · Kover 0.9.8 → 0.9.9

[Full changelog: 3.2.1…3.3.0](https://github.com/readingbat/readingbat-core/compare/3.2.1...3.3.0)

## v3.2.1 — 2026-07-03

Maintenance release: a Gradle 9.6.1 upgrade, a routine dependency refresh, and two small build/test polish items. No functional changes to the running server and no upgrade steps — a drop-in bump from 3.2.0.

### Changed

- **Gradle 9.6.1** — the wrapper moves from 9.5.1 to 9.6.1.
- **Smarter pre-release version filter** — `configureVersions()` rejects a pre-release candidate only when the *current* dependency is stable, so libraries tracked on a pre-release line (e.g. the detekt 2.0 alpha) keep surfacing newer pre-releases. The `dependencyUpdates` task is also marked incompatible with the configuration cache.
- **`TestSupport.forEachAnswer` takes a `suspend` block** — matching the suspend `functionInfo()` call site introduced in 3.2.0.

### Dependencies

Gradle 9.5.1 → 9.6.1 · common-utils 2.9.2 → 2.9.3 · Ktor 3.5.0 → 3.5.1 · Exposed 1.3.0 → 1.3.1 · Kotest 6.2.0 → 6.2.1 · Flyway 12.8.1 → 12.10.0 · PostgreSQL driver 42.7.11 → 42.7.12 · Cloud SQL socket factory 1.28.4 → 1.28.6 · Playwright 1.60.0 → 1.61.0 · detekt 2.0.0-alpha.4 → 2.0.0-alpha.5 · maven-publish 0.36.0 → 0.37.0

[Full changelog: 3.2.0…3.2.1](https://github.com/readingbat/readingbat-core/compare/3.2.0...3.2.1)

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
