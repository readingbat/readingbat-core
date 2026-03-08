# TailwindCSS Conversion Guide for ReadingBat

This document is a comprehensive analysis of the issues, trade-offs, and migration strategy for
converting ReadingBat's CSS infrastructure from the current Kotlin-based approach to TailwindCSS.

## Table of Contents

1. [Current CSS Architecture](#current-css-architecture)
2. [What Changes with Tailwind](#what-changes-with-tailwind)
3. [Integration Infrastructure (Already Done)](#integration-infrastructure-already-done)
4. [Type Safety Trade-offs](#type-safety-trade-offs)
5. [Dynamic Style Problem](#dynamic-style-problem)
6. [Third-Party CSS Conflicts](#third-party-css-conflicts)
7. [CssContent.kt Rule-by-Rule Migration Map](#csscontentkt-rule-by-rule-migration-map)
8. [CssNames Migration Map](#cssnames-migration-map)
9. [Inline Style Inventory](#inline-style-inventory)
10. [Pattern Categories and Conversion Strategies](#pattern-categories-and-conversion-strategies)
11. [Kotlinx.html API Constraints](#kotlinxhtml-api-constraints)
12. [Build Tooling Considerations](#build-tooling-considerations)
13. [Tailwind Prefix Strategy](#tailwind-prefix-strategy)
14. [Migration Phases](#migration-phases)
15. [Risks and Mitigations](#risks-and-mitigations)
16. [Decision Checklist](#decision-checklist)

---

## Current CSS Architecture

ReadingBat uses three layers of CSS, all generated server-side with zero frontend build tooling:

### Layer 1: `CssContent.kt` (~430 lines)

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/common/CssContent.kt`

A Kotlin `CssBuilder` (from the `kotlin-css` library, `org.jetbrains.kotlin-wrappers:kotlin-css`)
that generates a CSS string served dynamically at `/static/styles.css` via a Ktor route. This is
**not** a static file -- it's generated at startup and served on demand.

Key characteristics:
- **Compile-time type safety** -- CSS properties are Kotlin typed values (`Color`,
  `LinearDimension`, `Padding`, etc.). Typos like `fonSize` or `colr` are caught by the compiler.
- **~50 CSS rules** covering global resets, element selectors, class-based component styles, and
  third-party overrides (Prism.js, KotlinPlayground CodeMirror).
- **Two font-size constants** used throughout: `textFs = 115.pct` and `codeFs = 95.pct`.
- **Lazy initialization** -- computed once and cached.

### Layer 2: `CssNames` object (~25 constants)

**File:** Same file as above (`CssContent.kt`, lines 114-145)

A sealed object providing type-safe CSS class name constants:

```kotlin
internal object CssNames {
  const val CHECK_ANSWERS = "checkAnswers"
  const val ADMIN_BUTTON = "loadChallenge"
  const val FUNC_COL = "funcCol"
  const val DASHBOARD = "dashboard"
  // ... 25 total constants
}
```

These constants are imported across 21 files (86 import statements) and used in `classes = `
constructor parameters. They provide a single source of truth for class names -- renaming a constant
updates all usages, and unused constants are flagged by the IDE.

### Layer 3: Inline `style` attributes (~96 instances across 15 files)

The largest surface area. These are `style = "..."` strings embedded directly in Kotlinx.html page
generation code. They are raw CSS strings with no type safety.

**File-by-file breakdown:**

| File | Count | Primary patterns |
|------|-------|-----------------|
| `ChallengePage.kt` | 27 | Dynamic colors, layout, visibility toggling |
| `ClassSummaryPage.kt` | 12 | Spacing, colors, table layout |
| `HelpAndLogin.kt` | 11 | Float layout, alignment, OAuth modal styling |
| `PageUtils.kt` | 11 | Title sizing, header layout, navigation borders |
| `TeacherPrefsPage.kt` | 10 | Table spacing, alignment, form layout |
| `ChallengeGroupPage.kt` | 7 | Font sizing, spacing, progress table |
| `StudentSummaryPage.kt` | 6 | Margin, color headers |
| `UserPrefsPage.kt` | 3 | Message colors, alignment |
| `PlaygroundPage.kt` | 2 | Padding, alignment |
| `UserInfoPage.kt` | 2 | Message color, table spacing |
| `AdminPage.kt` | 1 | Message color |
| `SystemAdminPage.kt` | 1 | Message color |
| `js/AdminCommandsJs.kt` | 1 | Spinner inside JS string |
| `js/LikeDislikeJs.kt` | 1 | Spinner inside JS string |
| `js/CheckAnswersJs.kt` | 1 | Spinner inside JS string |

### Additional CSS: Prism.js and KotlinPlayground

- **3 Prism.js CSS files** (`java-prism.css`, `python-prism.css`, `kotlin-prism.css`) in
  `src/main/resources/static/prism/` -- loaded per-language on challenge pages.
- **CodeMirror overrides** in `CssContent.kt` (`.CodeMirror` font-size, `.CodeMirror-scroll` height
  fix).
- **Blue code stripe** -- a custom `pre[class*="language-"] > code` rule adding a left border and
  box-shadow.

### Bootstrap 3 (limited usage)

**Only loaded on `ClassSummaryPage.kt`** via `loadBootstrap()`. Uses Bootstrap 3.4.1 from CDN for
dropdown components. This is a separate concern from the Tailwind migration and should be addressed
independently (Bootstrap 3 is EOL).

---

## What Changes with Tailwind

### What You Gain

1. **Utility-first classes replace inline styles** -- `style = "margin-left:15px; color:#419DC1"`
   becomes `classes = "tw-ml-4 tw-text-[#419DC1]"` (or a custom color).
2. **Consistent design tokens** -- spacing scale (4px increments), color palette, typography sizes
   are standardized. No more mixing `15px`, `10px`, `5px` margins arbitrarily.
3. **Responsive utilities** -- `tw-md:hidden`, `tw-lg:grid-cols-3`, etc. Currently the site has no
   responsive design.
4. **State variants** -- `tw-hover:bg-gray-200` replaces the `a:hover` rule and the `.$BTN:hover`
   rule in `CssContent.kt`.
5. **Purged production CSS** -- only classes actually used in `.kt` files are included. The current
   `CssContent.kt` ships all rules regardless of which pages use them.

### What You Lose

1. **Compile-time CSS validation** -- `CssBuilder` catches invalid property names/values at compile
   time. Tailwind classes are just strings; a typo like `tw-mt-44` (instead of `tw-mt-4`) produces
   no class at all, silently.
2. **Type-safe class name constants** -- `CssNames.CHECK_ANSWERS` is a typed constant. The Tailwind
   equivalent is a raw string that can be misspelled anywhere.
3. **Zero frontend tooling** -- Currently no Node.js, npm, or build step for CSS. Tailwind CLI adds
   a binary dependency; npm adds Node.js to the build chain.
4. **The `kotlin-css` dependency** -- Once `CssContent.kt` is retired, the
   `org.jetbrains.kotlin-wrappers:kotlin-css` library (currently `libs.css` in
   `libs.versions.toml`) can be removed.

---

## Integration Infrastructure (Already Done)

The following infrastructure has been added to the codebase:

### Property: `TAILWIND_ENABLED`

**File:** `Property.kt`
**HOCON key:** `readingbat.site.tailwindEnabled`
**Default:** `false`

Controls whether Tailwind CSS is loaded. In non-production mode, loads the Tailwind CDN play script.
In production mode, links the pre-built `tailwind.css` file.

### Dual-mode loading in `headDefault()`

**File:** `PageUtils.kt`

```kotlin
if (TAILWIND_ENABLED.getProperty(false)) {
  if (isProduction()) {
    // Links /static/tailwind.css (pre-built by CLI)
  } else {
    // Loads https://cdn.tailwindcss.com (JIT in browser)
  }
}
```

### Tailwind CLI Gradle task

**File:** `readingbat-core/build.gradle.kts`

```kotlin
tasks.register<Exec>("tailwindBuild") {
  commandLine("tailwindcss", "-c", "tailwind.config.js",
    "-i", "src/main/resources/css/tailwind-input.css",
    "-o", "src/main/resources/static/tailwind.css", "--minify")
}
```

### Tailwind config

**File:** `tailwind.config.js` (project root)

- Scans `./readingbat-core/src/main/kotlin/**/*.kt` for class names
- Uses `tw-` prefix to avoid conflicts during migration
- Extends theme with ReadingBat brand colors (`rb-link`, `rb-visited`, `rb-correct`, etc.)

### Proof of concept

**File:** `PageUtils.kt` -- `bodyTitle()` method

Three inline styles converted to dual-mode (Tailwind classes alongside existing inline styles):
- `margin-bottom:0em` -> `tw-mb-0`
- `font-size:200%` -> `tw-text-4xl`
- `padding-left:5px` -> `tw-pl-1`

---

## Type Safety Trade-offs

### Current: Compile-time guarantees

```kotlin
// CssContent.kt -- compiler catches typos
rule(".$CHECK_ANSWERS") {
  width = 14.em          // LinearDimension -- typed
  backgroundColor = Color("#f1f1f1")  // Color -- typed
  fontSize = textFs      // Percentage -- typed
  fontWeight = bold       // FontWeight -- typed enum
}
```

If you write `fonWeight = bold`, the Kotlin compiler immediately flags the error.

### After: String-based classes

```kotlin
// Tailwind -- no compile-time validation
div(classes = "tw-w-56 tw-bg-gray-100 tw-text-lg tw-font-bold") { ... }
```

If you write `tw-fonr-bold`, no error -- the class just doesn't exist, and the element silently
lacks bold styling.

### Mitigation strategies

1. **Kotlin constants for frequently-used class combinations:**
   ```kotlin
   object TwClasses {
     const val CHECK_ANSWERS_BTN = "tw-w-56 tw-h-8 tw-bg-gray-100 tw-text-lg tw-font-bold tw-rounded-md"
     const val ADMIN_BTN = "tw-px-4 tw-h-8 tw-bg-gray-100 tw-text-sm tw-font-bold tw-rounded-md"
   }
   ```
   This preserves single-source-of-truth for component styles, though the individual utility names
   remain unvalidated strings.

2. **IDE plugin** -- The Tailwind IntelliJ plugin provides autocomplete and validation for Tailwind
   classes, even inside Kotlin strings. This catches typos at edit time but not compile time.

3. **Tailwind Lint** -- Tools like `eslint-plugin-tailwindcss` can validate class names in CI.
   However, these typically target `.html`/`.jsx` files and would need configuration to scan `.kt`
   files.

---

## Dynamic Style Problem

This is the **most significant conversion challenge**. Approximately 20 of the 96 inline styles use
Kotlin string interpolation to set CSS values dynamically at render time. These **cannot** be
directly replaced with Tailwind utility classes because Tailwind purges classes at build time -- it
cannot know runtime values.

### Category 1: Dynamic colors from `msg.color` (~7 instances)

```kotlin
// Current (6 files)
style = "color:${msg.color}"  // msg.color is either "#4EAA3A" (green) or "#FF0000" (red)
```

`msg.color` returns `CORRECT_COLOR` (`#4EAA3A`) or `WRONG_COLOR` (`#FF0000`) based on
`msg.isError`. Since there are only two possible values, this can be converted:

```kotlin
// Tailwind approach
span(classes = if (msg.isError) "tw-text-rb-wrong" else "tw-text-rb-correct") { ... }
```

This requires `rb-wrong` and `rb-correct` to be defined in `tailwind.config.js` (already done).

### Category 2: Dynamic `HEADER_COLOR` (~10 instances)

```kotlin
// Current (4 files)
style = "margin-left:15px; color: $HEADER_COLOR"  // HEADER_COLOR = "#419DC1"
```

`HEADER_COLOR` is a compile-time constant (`#419DC1`), not truly dynamic. It can be added to the
Tailwind config:

```javascript
// tailwind.config.js
colors: {
  'rb-header': '#419DC1',
}
```

Then: `classes = "tw-ml-4 tw-text-rb-header"`

### Category 3: Dynamic background colors from answer state (~3 instances)

```kotlin
// ChallengePage.kt
style = "width:15%;white-space:nowrap; background-color:$color"
// where color is CORRECT_COLOR, WRONG_COLOR, INCOMPLETE_COLOR, or "white"
```

These use the same fixed set of colors. Convert with conditional classes:

```kotlin
val bgClass = when (color) {
  CORRECT_COLOR -> "tw-bg-rb-correct"
  WRONG_COLOR -> "tw-bg-rb-wrong"
  INCOMPLETE_COLOR -> "tw-bg-rb-incomplete"
  else -> "tw-bg-white"
}
td(classes = "tw-w-[15%] tw-whitespace-nowrap $bgClass") { ... }
```

### Category 4: Dynamic visibility toggling (~6 instances)

```kotlin
// ChallengePage.kt -- like/dislike buttons
style = "display:${if (likeDislikeVal == 0 || likeDislikeVal == 2) "inline" else "none"}"
```

These toggle `display:none` / `display:inline` based on state. In Tailwind:

```kotlin
val visClass = if (likeDislikeVal == 0 || likeDislikeVal == 2) "tw-inline" else "tw-hidden"
img(classes = visClass) { ... }
```

### Category 5: Dynamic `font-size` percentage (~1 instance)

```kotlin
// PageUtils.kt -- hideShowButton
style = "font-size:$sizePct%"  // sizePct is an Int parameter, default 85
```

This requires Tailwind arbitrary values:

```kotlin
button(classes = "tw-text-[${sizePct}%]") { ... }
```

**Warning:** Arbitrary values with interpolation work with the CDN but may not be purged correctly
by the CLI scanner, since the scanner uses regex and cannot evaluate Kotlin expressions. The
workaround is to use a fixed set of allowed values or keep this as an inline style.

### Category 6: Styles inside JavaScript string templates (~3 instances)

```kotlin
// js/CheckAnswersJs.kt, js/LikeDislikeJs.kt, js/AdminCommandsJs.kt
// Spinner styling inside dynamically-generated JS code
style="font-size:24px"  // inside a JS template setting element content
```

These are inside JavaScript strings generated by Kotlin and cannot use Tailwind classes (the
Tailwind scanner won't find them in JS string templates). **Keep these as inline styles.**

### Summary: Dynamic styles by convertibility

| Category | Count | Convertible? | Strategy |
|----------|-------|-------------|----------|
| `msg.color` (2 values) | 7 | Yes | Conditional Tailwind classes |
| `HEADER_COLOR` (1 value) | 10 | Yes | Custom color in config |
| Answer state colors (4 values) | 3 | Yes | Conditional Tailwind classes |
| Visibility toggle | 6 | Yes | `tw-hidden` / `tw-inline` |
| Dynamic font-size | 1 | Partially | Arbitrary value or keep inline |
| JS string templates | 3 | No | Keep as inline style |

---

## Third-Party CSS Conflicts

### Tailwind Preflight vs. existing styles

Tailwind's [Preflight](https://tailwindcss.com/docs/preflight) is an opinionated CSS reset that
removes default margins on `h1`-`h6`, `p`, `blockquote`; removes default list styles; and resets
borders. This **will conflict** with `CssContent.kt` rules that assume browser defaults.

Specific conflicts:
- Preflight removes `h1`-`h6` font sizes and bold weight. `CssContent.kt` sets `h1, h2, h3, h4 {
  fontWeight: bold }` and `h2 { fontSize: 150% }`.
- Preflight removes `ol`/`ul` list styles. The `nav ul` rule in `CssContent.kt` already removes
  list styles, but other lists may be affected.
- Preflight resets `a` styling. `CssContent.kt` sets `:link` and `:visited` colors.

**Mitigation:** The `tw-` prefix helps avoid the worst conflicts because Tailwind's preflight
targets unprefixed selectors. However, if `@tailwind base` is included in the input CSS, Preflight
still applies globally. During migration, consider:

```css
/* tailwind-input.css -- disable preflight during migration */
@tailwind components;
@tailwind utilities;
/* Omit: @tailwind base; */
```

### Bootstrap 3 conflicts

`ClassSummaryPage.kt` loads Bootstrap 3.4.1. Both Bootstrap and Tailwind define `.btn`,
`.container`, `.table`, and many other class names. The `tw-` prefix completely avoids this conflict
for Tailwind classes, but Bootstrap's global resets may interact with Tailwind's Preflight.

**Recommendation:** Resolve the Bootstrap 3 dependency independently before or during Tailwind
migration. Bootstrap 3 is EOL and only used for dropdown menus on one page. Consider replacing it
with a Tailwind-based dropdown or a lightweight alternative.

### Prism.js / CodeMirror

These have their own CSS that Tailwind should not interfere with. The `tw-` prefix ensures no
collision. The custom overrides in `CssContent.kt` (code stripe, CodeMirror font-size/height fixes)
cannot be expressed as Tailwind utilities and should either:
- Remain in `CssContent.kt` even after migration, or
- Be moved to a custom `tailwind-overrides.css` file using standard CSS (not Tailwind utilities)

---

## CssContent.kt Rule-by-Rule Migration Map

Each rule in `CssContent.kt` mapped to its Tailwind equivalent and migration notes.

### Global/Element Rules

| Rule | Current CSS | Tailwind Equivalent | Notes |
|------|-----------|-------------------|-------|
| `html, body` | `font-size:16px; font-family:verdana...` | `tw-text-base tw-font-sans` | Set in `tailwind.config.js` `fontFamily.sans` |
| `body` | `display:block; margin:8px 8px; line-height:normal` | `tw-block tw-m-2 tw-leading-normal` | Browser default; may be unnecessary |
| `h1-h4` | `font-weight:bold` | `tw-font-bold` on each heading | Apply via `@layer base` or per-element |
| `li` | `margin-top:10px` | `tw-mt-2.5` | Apply via `@layer base` or per-element |
| `p` | `max-width:800px; line-height:1.5` | `tw-max-w-3xl tw-leading-relaxed` | `800px` ~ `tw-max-w-3xl` (768px) or arbitrary `tw-max-w-[800px]` |
| `:link` | `color:#0000DD` | `tw-text-rb-link` | Custom color in config |
| `:visited` | `color:#551A8B` | `tw-text-rb-visited` | Tailwind doesn't have visited utilities by default; needs `visited:` variant |
| `td` | `vertical-align:middle` | `tw-align-middle` | |
| `a` | `text-decoration:none` | `tw-no-underline` | |
| `a:hover` | `color:red` | `tw-hover:text-red-500` | |
| `th, td` | `padding:1px; text-align:left` | `tw-p-px tw-text-left` | |
| `nav ul` | `list-style:none; padding:0; margin:0` | `tw-list-none tw-p-0 tw-m-0` | |
| `nav li` | `display:inline; border:1px solid; border-width:1px 1px 0 1px; margin:0 25px 0 6px` | Complex -- needs custom CSS or arbitrary values | Tab-style border pattern is hard to express purely in Tailwind |
| `nav li a` | `padding:0 40px` | `tw-px-10` | `40px` = `tw-px-10` (2.5rem ~ 40px) |

### Class-Based Component Rules

| CssNames Constant | Current CSS | Tailwind Equivalent |
|-------------------|-----------|-------------------|
| `CHECK_ANSWERS` | `w:14em; h:2em; bg:#f1f1f1; font-size:115%; font-weight:bold; border-radius:6px` | `tw-w-56 tw-h-8 tw-bg-rb-incomplete tw-text-lg tw-font-bold tw-rounded-md` |
| `ADMIN_BUTTON` | `px:1em; h:2em; bg:#f1f1f1; font-size:80%; font-weight:bold; border-radius:6px` | `tw-px-4 tw-h-8 tw-bg-rb-incomplete tw-text-xs tw-font-bold tw-rounded-md` |
| `LIKE_BUTTONS` | `bg:#f1f1f1; w:4em; h:4em; border-radius:6px` | `tw-bg-rb-incomplete tw-w-16 tw-h-16 tw-rounded-md` |
| `CHALLENGE_DESC` | `font-size:115%; margin-left:1em; margin-bottom:1em` | `tw-text-lg tw-ml-4 tw-mb-4` |
| `FUNC_ITEM1` | `margin-top:1em` | `tw-mt-4` |
| `FUNC_ITEM2` | `margin-top:1em; width:300px` | `tw-mt-4 tw-w-[300px]` |
| `GROUP_CHOICE` | `font-size:155%` | `tw-text-2xl` (approximate) |
| `GROUP_ITEM_SRC` | `max-w:300px; min-w:300px; m:15px; p:10px; border:1px solid gray; border-radius:1em` | `tw-w-[300px] tw-m-4 tw-p-2.5 tw-border tw-border-gray-400 tw-rounded-2xl` |
| `FUNC_COL` | `font-size:115%` | `tw-text-lg` |
| `ARROW` | `width:2em; font-size:115%; text-align:center` | `tw-w-8 tw-text-lg tw-text-center` |
| `USER_RESP` | `width:15em; font-size:90%` | `tw-w-60 tw-text-sm` |
| `FEEDBACK` | `width:10em; border:7px solid white` | `tw-w-40 tw-border-[7px] tw-border-white` |
| `DASHBOARD` | `border:1px solid #DDD; border-collapse:collapse` | `tw-border tw-border-gray-300 tw-border-collapse` |
| `STATUS` | `margin-left:5px; font-size:115%` | `tw-ml-1 tw-text-lg` |
| `SUCCESS` | `margin-left:14px; font-size:115%; color:black` | `tw-ml-3.5 tw-text-lg tw-text-black` |
| `INDENT_1EM` | `margin-left:1em` | `tw-ml-4` |
| `INDENT_2EM` | `margin-left:2em; margin-bottom:2em` | `tw-ml-8 tw-mb-8` |
| `SELECTED_TAB` | `position:relative; top:1px; background:white` | `tw-relative tw-top-px tw-bg-white` |
| `UNDERLINE` | `text-decoration:underline` | `tw-underline` |
| `INVOC_TABLE` | `border-collapse:separate; border-spacing:10px 5px` | `tw-border-separate tw-border-spacing-x-2.5 tw-border-spacing-y-1` |
| `INVOC_TD` | `border-collapse:separate; border:1px solid black; w:7px; h:15px; bg:#F1F1F1` | `tw-border tw-border-black tw-w-[7px] tw-h-[15px] tw-bg-rb-incomplete` |
| `INVOC_STAT` | `padding-left:5px; padding-right:1px; width:10px` | `tw-pl-1 tw-pr-px tw-w-2.5` |
| `BTN` | `background:white` + `:hover { background:#e7e7e7 }` | `tw-bg-white tw-hover:bg-gray-200` |
| `CENTER` | `display:block; margin:auto; width:50%` | `tw-block tw-mx-auto tw-w-1/2` |
| `EXPERIMENT` | `margin-top:1em; font-size:115%` | `tw-mt-4 tw-text-lg` |
| `CODINGBAT` | `margin-top:2em; font-size:115%` | `tw-mt-8 tw-text-lg` |
| `CODE_BLOCK` | `margin-top:2em; margin:0 1em; font-size:95%` | `tw-mt-8 tw-mx-4 tw-text-[95%]` |
| `KOTLIN_CODE` | `margin:0 1em` | `tw-mx-4` |
| `.h1` | `font-size:300%` | `tw-text-5xl` (approximate) or `tw-text-[300%]` |
| `.h2` | `font-size:166%; text-decoration:none` | `tw-text-3xl tw-no-underline` (approximate) or `tw-text-[166%]` |
| `.h3` | `font-size:120%` | `tw-text-xl` (approximate) or `tw-text-[120%]` |

### Rules That Cannot Be Tailwind Utilities

These rules use CSS selectors or patterns that Tailwind cannot express:

| Rule | Why | Recommendation |
|------|-----|----------------|
| `pre[class*="language-"] > code` | Attribute selector + child combinator with box-shadow | Keep in custom CSS file |
| `td.no` / `td.ok` | Element+class selectors used for answer state | Convert to utility classes on the element directly |
| `div.tdPadding th` / `div.tdPadding td` | Descendant combinators | Apply utilities directly to `th`/`td` elements |
| `.CodeMirror` / `.CodeMirror-scroll` | Third-party component overrides | Keep in custom CSS file |
| `p.max` | Element+class selector | Apply `tw-max-w-3xl` directly |

---

## CssNames Migration Map

Each `CssNames` constant, where it's used, and how to migrate:

| Constant | Value | Used in (files) | Migration |
|----------|-------|----------------|-----------|
| `CHECK_ANSWERS` | `"checkAnswers"` | ChallengePage | Replace with Tailwind class string |
| `ADMIN_BUTTON` | `"loadChallenge"` | PageUtils, ChallengeGroupPage, StudentSummaryPage, ChallengePage | Replace with Tailwind class string |
| `LIKE_BUTTONS` | `"likeButtons"` | ChallengePage | Replace with Tailwind class string |
| `FEEDBACK` | `"hint"` | ChallengePage | Replace with Tailwind class string |
| `HINT` | `"feedback"` | ChallengePage | Empty rule currently -- remove |
| `FUNC_COL` | `"funcCol"` | ChallengePage | Replace with Tailwind class string |
| `ARROW` | `"arrow"` | ChallengePage | Replace with Tailwind class string |
| `EXPERIMENT` | `"experiment"` | ChallengePage | Replace with Tailwind class string |
| `CODINGBAT` | `"codingbat"` | ChallengePage | Replace with Tailwind class string |
| `CODE_BLOCK` | `"codeBlock"` | ChallengePage | Replace with Tailwind class string |
| `KOTLIN_CODE` | `"kotlin-code"` | PlaygroundPage | Replace with Tailwind class string |
| `USER_RESP` | `"userResponse"` | ChallengePage | Replace with Tailwind class string |
| `CHALLENGE_DESC` | `"challenge-desc"` | ChallengePage | Replace with Tailwind class string |
| `GROUP_CHOICE` | `"groupChoice"` | LanguageGroupPage | Replace with Tailwind class string |
| `FUNC_ITEM1` | `"funcItem1"` | ChallengeGroupPage | Replace with Tailwind class string |
| `FUNC_ITEM2` | `"funcItem2"` | ChallengeGroupPage | Replace with Tailwind class string |
| `TD_PADDING` | `"tdPadding"` | SystemConfigurationPage, AdminPrefsPage, SessionsPage | Descendant rule -- apply utilities to children |
| `GROUP_ITEM_SRC` | `"groupItem"` | LanguageGroupPage | Replace with Tailwind class string |
| `SELECTED_TAB` | `"selected"` | PageUtils (used as `id`, not class) | Keep as ID or convert to Tailwind |
| `STATUS` | `"status"` | ChallengePage | Replace with Tailwind class string |
| `SUCCESS` | `"success"` | ChallengePage | Replace with Tailwind class string |
| `DASHBOARD` | `"dashboard"` | ClassSummaryPage, StudentSummaryPage | Replace with Tailwind class string |
| `INDENT_1EM` | `"indent-1em"` | PageUtils, HelpPage, PrivacyPage, AboutPage, InvalidRequestPage | Replace with `tw-ml-4` |
| `INDENT_2EM` | `"indent-2em"` | ClassSummaryPage | Replace with `tw-ml-8 tw-mb-8` |
| `UNDERLINE` | `"underline"` | ClassSummaryPage, StudentSummaryPage | Replace with `tw-underline` |
| `INVOC_TABLE` | `"invoc_table"` | ClassSummaryPage, StudentSummaryPage | Replace with Tailwind class string |
| `INVOC_TD` | `"invoc_td"` | ClassSummaryPage, StudentSummaryPage | Replace with Tailwind class string |
| `INVOC_STAT` | `"invoc_stat"` | ClassSummaryPage, StudentSummaryPage | Replace with Tailwind class string |
| `BTN` | `"btn"` | ClassSummaryPage | Replace with Tailwind class string; note Bootstrap 3 also defines `.btn` |
| `CENTER` | `"center"` | ErrorPage, NotFoundPage, DbmsDownPage | Replace with `tw-block tw-mx-auto tw-w-1/2` |

---

## Inline Style Inventory

### Static inline styles (easily converted)

These use literal CSS values with no string interpolation:

```kotlin
style = "margin-bottom:0em"                              // tw-mb-0
style = "font-size:200%"                                 // tw-text-4xl
style = "padding-left:5px"                               // tw-pl-1
style = "color:red"                                      // tw-text-red-500
style = "color:green; max-width:800"                     // tw-text-green-600 tw-max-w-3xl
style = "padding-top:10px; min-width:100vw; clear:both"  // tw-pt-2.5 tw-min-w-screen tw-clear-both
style = "border-top: 1px solid; clear: both"             // tw-border-t tw-border-solid tw-clear-both
style = "float:right; padding-top:10px"                  // tw-float-right tw-pt-2.5
style = "text-align:right; white-space:nowrap"           // tw-text-right tw-whitespace-nowrap
style = "margin:0"                                       // tw-m-0
style = "vertical-align:middle; margin-top:1; margin-bottom:0"  // tw-align-middle tw-mt-px tw-mb-0
style = "border-collapse: separate; border-spacing: 15px 10px"  // tw-border-separate + arbitrary
style = "font-size:110%"                                 // tw-text-[110%]
style = "display:none"                                   // tw-hidden
style = "margin-left:1em"                                // tw-ml-4
style = "margin-top:2em; margin-left:2em"                // tw-mt-8 tw-ml-8
style = "width:100%; border-spacing: 5px 10px"           // tw-w-full + arbitrary
```

**Count:** ~66 instances (68% of total)

### Dynamic inline styles (need conditional logic)

Listed in the [Dynamic Style Problem](#dynamic-style-problem) section above.

**Count:** ~27 instances (28% of total)

### Inline styles in JS strings (cannot convert)

```kotlin
// 3 instances across AdminCommandsJs.kt, LikeDislikeJs.kt, CheckAnswersJs.kt
style="font-size:24px"  // inside JS template setting spinner element styling
```

**Count:** 3 instances (3% of total)

---

## Pattern Categories and Conversion Strategies

### Pattern 1: Pure spacing/sizing (easiest -- ~40 instances)

```kotlin
// Before
style = "margin-left:15px; margin-bottom:15px"

// After
div(classes = "tw-ml-4 tw-mb-4") { ... }
```

### Pattern 2: Color styling (~18 instances)

```kotlin
// Before (static)
style = "color: $HEADER_COLOR"

// After
span(classes = "tw-text-rb-header") { ... }

// Before (dynamic msg.color)
style = "color:${msg.color}"

// After
span(classes = if (msg.isError) "tw-text-rb-wrong" else "tw-text-rb-correct") { ... }
```

### Pattern 3: Table layout (~15 instances)

```kotlin
// Before
style = "border-spacing: 15px 10px"

// After -- arbitrary values needed
table(classes = "tw-border-separate tw-border-spacing-x-[15px] tw-border-spacing-y-[10px]") { ... }
```

### Pattern 4: Form element styling (~8 instances)

```kotlin
// Before
style = "vertical-align:middle; margin-top:1; margin-bottom:0"

// After
submitInput(classes = "tw-align-middle tw-mt-px tw-mb-0") { ... }
```

### Pattern 5: Visibility toggling (~6 instances)

```kotlin
// Before
style = "display:${if (condition) "inline" else "none"}"

// After
img(classes = if (condition) "tw-inline" else "tw-hidden") { ... }
```

### Pattern 6: Composite layout (~9 instances)

```kotlin
// Before
style = "padding-top:10px; min-width:100vw; clear:both"

// After
div(classes = "tw-pt-2.5 tw-min-w-screen tw-clear-both") { ... }
```

---

## Kotlinx.html API Constraints

### How classes work

In Kotlinx.html, CSS classes are set via the **constructor parameter**:

```kotlin
div(classes = "tw-mt-4 tw-text-lg") {
  // content
}
```

Inside the lambda, you can also set classes on some tag types:

```kotlin
dropdownToggle {
  classes = setOf("btn")  // Works on some custom tags
}
```

But standard tags like `div`, `span`, `p`, `a`, `table`, `tr`, `td` require the constructor
parameter approach for classes.

### Combining CssNames constants with Tailwind classes

During migration, elements may need both existing `CssNames` classes and new Tailwind classes:

```kotlin
// Both a CssNames class and Tailwind classes
div(classes = "$DASHBOARD tw-p-4 tw-rounded") { ... }
```

This works because `classes` accepts any space-separated string.

### Dual-mode (inline style + classes) during migration

The proof-of-concept approach keeps both:

```kotlin
div(classes = "tw-mb-0") {
  style = "margin-bottom:0em"  // Fallback when Tailwind is off
  // ...
}
```

When Tailwind is enabled, the `tw-mb-0` class takes effect. The inline `style` attribute also
applies but has higher specificity -- so the inline style actually "wins" regardless. For a true
dual-mode approach during migration, you'd need to conditionally omit the `style` attribute, which
adds complexity:

```kotlin
div(classes = "tw-mb-0") {
  if (!TAILWIND_ENABLED.getProperty(false)) style = "margin-bottom:0em"
}
```

**Recommendation:** For the actual migration, do a clean cut per page rather than maintaining
dual-mode. Convert all styles on a page at once and remove the inline `style` attributes.

---

## Build Tooling Considerations

### Option A: Tailwind Standalone CLI (Recommended)

**No Node.js required.** Download a single binary.

- Binary: ~45MB, available for macOS (arm64/x64), Linux, Windows
- Install: download from GitHub releases or via `curl`/`brew`
- Gradle task already configured: `./gradlew :readingbat-core:tailwindBuild`

**CI/CD integration:**
```yaml
# Example GitHub Actions step
- name: Install Tailwind CLI
  run: |
    curl -sLO https://github.com/tailwindlabs/tailwindcss/releases/latest/download/tailwindcss-macos-arm64
    chmod +x tailwindcss-macos-arm64
    mv tailwindcss-macos-arm64 /usr/local/bin/tailwindcss

- name: Build Tailwind CSS
  run: ./gradlew :readingbat-core:tailwindBuild
```

**Watch mode for development:**
```bash
tailwindcss -c tailwind.config.js \
  -i readingbat-core/src/main/resources/css/tailwind-input.css \
  -o readingbat-core/src/main/resources/static/tailwind.css \
  --watch
```

### Option B: npm + Gradle Node plugin

Adds `package.json`, `node_modules/`, and Node.js to the build:

```kotlin
// build.gradle.kts
plugins {
  id("com.github.node-gradle.node") version "7.0.1"
}

node {
  version = "20.11.0"
  download = true
}
```

**Pros:** Full PostCSS ecosystem, `@apply` directive, plugins.
**Cons:** Adds ~200MB of `node_modules`, Node.js download in CI, additional build complexity.

**Recommendation:** Use Option A. The project has intentionally avoided frontend tooling. The
standalone CLI provides everything needed for Tailwind without introducing Node.js.

### Content scanning verification

Tailwind's content scanner uses regex to find class names in source files. For Kotlin files, it
correctly extracts strings like:

```kotlin
div(classes = "tw-mt-4 tw-text-lg tw-font-bold") { ... }
```

The scanner finds `tw-mt-4`, `tw-text-lg`, and `tw-font-bold` and includes them in the output.

**Edge cases that DON'T work:**
```kotlin
// String concatenation -- scanner can't resolve this
val base = "tw-mt"
div(classes = "${base}-4") { ... }  // tw-mt-4 NOT found

// Dynamic arbitrary values -- scanner can't evaluate Kotlin
div(classes = "tw-w-[${width}px]") { ... }  // NOT found
```

**Rule:** Always use complete, literal class name strings. Never construct class names dynamically.

---

## Tailwind Prefix Strategy

The current `tailwind.config.js` uses `prefix: 'tw-'`. This means:

- All Tailwind utilities are prefixed: `tw-mt-4`, `tw-text-lg`, `tw-bg-white`
- Existing CSS classes (`checkAnswers`, `dashboard`, etc.) are unaffected
- Bootstrap 3 classes (`.btn`, `.dropdown`, etc.) are unaffected
- No CSS specificity conflicts during migration

### When to remove the prefix

Remove `prefix: 'tw-'` from `tailwind.config.js` after:

1. `CssContent.kt` is fully retired (no more `cssContent` route)
2. Bootstrap 3 is removed from `ClassSummaryPage.kt`
3. All `CssNames` constants are replaced with Tailwind equivalents
4. A global find-and-replace of `tw-` to `` in all `.kt` files is done

This is a single-step operation: update config, find-and-replace in source, rebuild.

---

## Migration Phases

### Phase 0: Current State (Done)

- [x] `TAILWIND_ENABLED` property added
- [x] CDN loading for development
- [x] CLI Gradle task for production
- [x] `tailwind.config.js` with `.kt` scanning and `tw-` prefix
- [x] Custom colors in config (`rb-link`, `rb-visited`, `rb-correct`, `rb-wrong`, etc.)
- [x] Proof of concept on `bodyTitle()`

### Phase 1: Add missing custom values to Tailwind config

Add `HEADER_COLOR` and other constants:

```javascript
colors: {
  'rb-header': '#419DC1',
  'rb-incomplete': '#F1F1F1',
  // ... already have rb-link, rb-visited, rb-correct, rb-wrong, rb-code-stripe
}
```

Consider whether to disable Preflight (`@tailwind base`) during migration.

### Phase 2: Convert simple pages first

Start with pages that have the fewest inline styles and no dynamic values:

1. `AboutPage.kt` (1 class usage, 0 inline styles)
2. `PrivacyPage.kt` (1 class usage, 0 inline styles)
3. `HelpPage.kt` (1 class usage, 0 inline styles -- but uses `INDENT_1EM`)
4. `DbmsDownPage.kt` (1 class usage, 0 inline styles)
5. `ErrorPage.kt` (2 class usages, 0 inline styles)
6. `NotFoundPage.kt` (2 class usages, 0 inline styles)
7. `InvalidRequestPage.kt` (2 class usages, 0 inline styles)

### Phase 3: Convert shared components

Convert `PageUtils.kt` (11 inline styles) and `HelpAndLogin.kt` (11 inline styles). These are used
by every page, so converting them affects the entire application.

### Phase 4: Convert medium-complexity pages

1. `AdminPage.kt` (1 inline style)
2. `SystemAdminPage.kt` (1 inline style)
3. `UserInfoPage.kt` (2 inline styles)
4. `PlaygroundPage.kt` (2 inline styles)
5. `UserPrefsPage.kt` (3 inline styles)
6. `LanguageGroupPage.kt` (0 inline styles, 2 class usages)
7. `AdminPrefsPage.kt` (0 inline styles, 4 class usages)
8. `SessionsPage.kt` (0 inline styles, 3 class usages)

### Phase 5: Convert complex pages

These have the most inline styles and dynamic values:

1. `StudentSummaryPage.kt` (6 inline styles, 9 class usages)
2. `ChallengeGroupPage.kt` (7 inline styles, 2 class usages)
3. `TeacherPrefsPage.kt` (10 inline styles, 4 class usages)
4. `ClassSummaryPage.kt` (12 inline styles, 14 class usages, Bootstrap 3)
5. `ChallengePage.kt` (27 inline styles, 23 class usages -- most complex page)

### Phase 6: Retire CssContent.kt

1. Remove all `CssNames` constants (replace remaining usages with Tailwind strings or new
   constants)
2. Remove the `cssContent` lazy val
3. Remove the `get(CSS_ENDPOINT)` route in `UserRoutes.kt`
4. Remove the `kotlin-css` dependency from `build.gradle.kts` (`implementation(libs.css)`)
5. Remove `tw-` prefix from `tailwind.config.js`
6. Global find-and-replace `tw-` -> `` in all `.kt` files
7. Move any un-convertible rules (Prism overrides, CodeMirror fixes) to a standalone CSS file

### Phase 7: Remove Bootstrap 3

Replace the Bootstrap 3 dropdown on `ClassSummaryPage.kt` with a Tailwind-based implementation or a
lightweight JS dropdown.

---

## Risks and Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| Tailwind class typos cause silent styling failures | Medium | High during migration | Use IntelliJ Tailwind plugin; add visual regression tests |
| Preflight resets break existing page layouts | High | High if enabled | Disable `@tailwind base` during migration |
| Content scanner misses dynamically-constructed class names | Medium | Low if following rules | Never construct class names dynamically; always use literal strings |
| Bootstrap 3 + Tailwind class name collisions | Medium | Low (mitigated by `tw-` prefix) | Resolve Bootstrap 3 dependency separately |
| Tailwind CLI binary not available in CI | High | Medium | Pin version in CI config; cache binary |
| Migration creates regressions on complex pages | High | Medium for complex pages | Migrate page-by-page; test each page visually before moving on |
| `kotlin-css` library update breaks CssContent.kt during migration | Low | Low | Pin version; migrate quickly |

---

## Decision Checklist

Before starting the full migration, decide on:

- [ ] **Preflight:** Enable or disable `@tailwind base` during migration?
  - Recommended: Disable until Phase 6
- [ ] **Prefix removal timing:** Remove `tw-` prefix at end or live with it?
  - Recommended: Remove in Phase 6 after CssContent.kt is retired
- [ ] **Bootstrap 3:** Migrate before, during, or after Tailwind?
  - Recommended: After (Phase 7), since it's isolated to one page
- [ ] **Type-safe constants:** Create `TwClasses` object with Tailwind string constants?
  - Recommended: Yes, for component-level styles used in 3+ places
- [ ] **CDN vs CLI in development:** Use CDN for faster iteration or CLI for parity with production?
  - Recommended: CDN during active migration, CLI for final verification
- [ ] **Visual regression testing:** Add screenshot tests before migration?
  - Recommended: Yes, especially for ChallengePage and ClassSummaryPage
- [ ] **Custom CSS file:** Where to put non-Tailwind rules (Prism, CodeMirror overrides)?
  - Recommended: `src/main/resources/css/overrides.css`, linked in `headDefault()`
