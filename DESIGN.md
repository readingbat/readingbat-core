---
name: ReadingBat
description: A graded worksheet in the browser — plain paper, plain type, code marked in green and red
colors:
  rb-header: "#337E9C"
  rb-correct: "#4EAA3A"
  rb-wrong: "#FF0000"
  rb-correct-text: "#3E862E"
  rb-wrong-text: "#ED0000"
  rb-incomplete: "#F1F1F1"
  rb-code-stripe: "#0600EE"
  rb-link: "#0000DD"
  rb-visited: "#551A8B"
  paper: "#FFFFFF"
  ink: "#000000"
  muted-ink: "#666666"
  control-border: "#D1D5DB"
  field-border: "#767676"
  code-halo: "#DFDFDF"
  oauth-google: "#4285F4"
  oauth-github: "#333333"
typography:
  display:
    fontFamily: "verdana, arial, helvetica, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 400
    lineHeight: "normal"
  tab:
    fontSize: "166%"
    fontWeight: 700
  headline:
    fontSize: "150%"
    fontWeight: 700
  title:
    fontSize: "120%"
    fontWeight: 700
  reading:
    fontSize: "115%"
    fontWeight: 400
  body:
    fontFamily: "verdana, arial, helvetica, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
  code:
    fontSize: "95%"
  label:
    fontSize: "90%"
  micro:
    fontSize: "75%"
    fontWeight: 700
rounded:
  control: "0.25rem"
  button: "6px"
  card: "1em"
  overlay: "8px"
  full: "9999px"
spacing:
  page: "8px"
  hairline: "1px"
  tight: "4px"
  base: "8px"
  card-pad: "10px"
  gutter: "15px"
  indent: "16px"
  section: "32px"
components:
  button-check-answers:
    backgroundColor: "{colors.rb-incomplete}"
    rounded: "{rounded.button}"
    width: "224px"
    height: "32px"
    typography: "{typography.body}"
  button-admin:
    backgroundColor: "{colors.rb-incomplete}"
    rounded: "{rounded.button}"
    height: "30px"
    padding: "0 14px"
    typography: "{typography.micro}"
  button-clear-history:
    backgroundColor: "{colors.rb-incomplete}"
    rounded: "{rounded.control}"
    padding: "4px 16px"
  button-generic:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
  answer-input:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.control}"
    width: "240px"
    padding: "5px 5px 5px 7px"
    typography: "{typography.label}"
  feedback-cell:
    backgroundColor: "{colors.paper}"
    width: "160px"
  feedback-cell-correct:
    backgroundColor: "{colors.rb-correct}"
  feedback-cell-wrong:
    backgroundColor: "{colors.rb-wrong}"
  group-card:
    rounded: "{rounded.card}"
    width: "322px"
    padding: "10px"
  invocation-cell:
    backgroundColor: "{colors.rb-incomplete}"
    width: "7px"
    height: "15px"
  oauth-google:
    backgroundColor: "{colors.oauth-google}"
    textColor: "{colors.paper}"
    rounded: "{rounded.control}"
    padding: "10px 20px"
  oauth-github:
    backgroundColor: "{colors.oauth-github}"
    textColor: "{colors.paper}"
    rounded: "{rounded.control}"
    padding: "10px 20px"
---

# Design System: ReadingBat

## Overview

**Creative North Star: "The Graded Worksheet"**

ReadingBat looks like paper a teacher handed you. White ground, plain resident type, a column of
function calls on the left and a column of blanks on the right, and a mark in green or red once you
commit to an answer. Nothing on the page competes with the code, because the code is the only thing
a student is there to read. The system's central act — type a prediction, press Check, get marked —
is exactly the act of filling in a worksheet and having it graded, and every visual decision in the
implementation serves that act or gets out of its way.

The character is plain and self-effacing, and deliberately so. There is no web font, no gradient, no
elevated card, no accent bar, no illustration in the working surfaces. Structure is
carried entirely by 1px hairlines and by white space. Where most systems would reach for a fill, a
shadow, or a brand color to separate two regions, this one draws a single line and stops. The
restraint is the design, not the absence of one: a student reading `sum(1, 2)` and deciding what it
returns is doing the hardest thing on the page, and the interface's job is to have no opinion while
they do it.

Color is rationed to the point of ceremony. Six chromatic values exist in the entire working
vocabulary, each with exactly one job, and four of them are only ever *earned* — by answering, by
following a link, by looking at a code block. A page with no answers on it is black text on white
paper with one blue heading. That scarcity is what makes green and red mean something the instant
they appear.

**Key Characteristics:**

- Resident system type only — Verdana at 16px, no font ever loaded over the network
- White paper ground with 1px hairline structure; no fills, no gradients, no elevated surfaces
- Six working colors, each with a single job; red and green are earned, not decorative
- The 115% / 95% pair: reading steps up, code steps down
- Flat by default — a shadow appears only on things you can press
- No transitions; feedback replaces state instantly, and the busy spinner is the only motion
- Fixed desktop width, no breakpoints, prose measured at 800px

## Colors

A worksheet palette: paper, pencil, two grading marks, and a small set of inks that each mean one
thing.

### Primary

- **Chalk Blue** (`#337E9C`): the one structural accent. It colors every section heading — the
  `Function Call` / `Return Value` table headers, the `h3` that names a challenge group, class and
  student summary section titles — and nothing else. It never fills a surface, never colors a
  button, never appears as a border. On a page with no answers marked, this is the only color
  present besides black text and blue links.

### Secondary

- **Grading Green** (`#4EAA3A`): a correct answer. Applied as a background fill on the feedback cell
  and on invocation-grid cells, by JavaScript, at the moment an answer is confirmed. A **fill only** —
  at 2.9:1 on white it is not legible as text.
- **Marker Red** (`#FF0000`): a wrong answer. Same mechanism, same restriction. Pure red, chosen for
  unmistakability rather than for taste. Also a fill only (4.0:1 on white).
- **Grading Green Text** (`#3E862E`) and **Marker Red Text** (`#ED0000`): the same two hues, darkened
  at identical hue and saturation until each clears 4.5:1 on white. These carry every success and
  error *message* in the app. Use the fill for cells, the text variant for words — never the reverse.

### Tertiary

- **Ballpoint Blue** (`#0600EE`): the code stripe. A 10px solid left border on every Prism-highlighted
  code block, doubled by a `-1px 0 0 0` box-shadow so the stripe reads as a hard edge with no seam,
  and ringed by a 1px **Code Halo** (`#DFDFDF`) outline around the whole block. This is the single
  most saturated element in the system and it exists to say *this region is code, read it
  differently*.
- **Link Blue** (`#0000DD`) and **Visited Purple** (`#551A8B`): the browser's own link convention,
  restated explicitly rather than restyled. Links carry no underline at rest
  (`text-decoration: none`) and gain one on `:hover` — a non-color hover cue that leaves red free to
  mean only "wrong".

### Neutral

- **Paper** (`#FFFFFF`): the ground for every page, every table, every card. There is no second
  surface tone anywhere in the system.
- **Ink** (`#000000`): all body text, and the 1px border on invocation-grid cells.
- **Newsprint Gray** (`#F1F1F1`): the unanswered state. It fills every button face and every
  invocation cell that hasn't been attempted yet — which means "a control you may press" and "a
  question you haven't answered" are, deliberately, the same gray. Both are waiting for you.
- **Muted Ink** (`#666666`): secondary copy inside the sign-in overlay only.
- **Control Border** (`#D1D5DB`) and **Field Border** (`#767676`): the two hairline grays. Buttons
  take the lighter one, text inputs and textareas the darker.

### Named Rules

**The Marker Red Rule.** Red means an answer is wrong. Its only other appearance is the darkened
text variant on error messages — not destructive actions, not emphasis, and no longer hover (link
hover is now an underline). New surfaces must not spend red on anything a student could mistake for
being marked wrong.

**The One Job Per Blue Rule.** Three blues coexist and never substitute for each other: Chalk Blue
heads sections, Ballpoint Blue stripes code, Link Blue marks links. Using one in another's role
collapses a distinction the system relies on.

**The Earned Color Rule.** Green and red are applied only by a state change the user caused. A
static page never ships them. Their scarcity is what makes them legible at a glance across a
teacher's class summary of thirty students.

## Typography

**Display Font:** Verdana (with Arial, Helvetica, sans-serif)
**Body Font:** Verdana — the same stack; the system has exactly one family
**Code Font:** inherited from Prism.js and CodeMirror, scaled to 95%

**Character:** Verdana at 16px is the most unglamorous, most legible choice available, and it is the
right one — it was drawn for screens, its numerals and its `l`/`1`/`I` are distinguishable, and it
is resident on every school machine. The system loads no font file at all, so pages paint once with
no flash and no network dependency. Hierarchy is built entirely from size and weight within this one
family, expressed in percentages of the 16px root rather than in absolute pixels.

### Hierarchy

- **Display** (regular, `2.25rem` / 36px): the "ReadingBat" wordmark, the page's single `h1`, set as
  a link to `/`, with the tagline "code reading practice" trailing it at body size. Notably *not*
  bold — it carries `font-normal` to override the base layer's bold on `h1`–`h4`, because the
  wordmark being the largest and lightest-feeling thing on the page is part of the identity.
- **Tab** (bold, 166%): the language tabs — Java / Python / Kotlin. Larger than the page's own `h2`,
  because navigating between languages is the most frequent structural move a student makes.
- **Headline** (bold, 150%): the `h2` element — page titles like "About ReadingBat", "ReadingBat
  Help".
- **Title** (bold, 120%, Chalk Blue): `h3` section headings. Group names, class-summary sections,
  student-summary sections.
- **Reading** (regular, 115%): the signature step. Function-call cells, challenge descriptions,
  status text, table headers, the "experiment with this code" and CodingBat hand-off links. Anything
  a student must read *closely* steps up from body.
- **Body** (regular, 16px, line-height 1.5): default prose, capped at an 800px measure.
- **Code** (95%): Prism blocks and the CodeMirror playground. Code steps *down* while reading steps
  up, so a code block reads as a quoted artifact rather than as page copy.
- **Label** (90%): answer input fields.
- **Micro** (bold, 75–85%): admin buttons and the clear-history control — small, bold, and out of the
  student's way.

### Named Rules

**The 115/95 Rule.** Prose a student must read closely steps up to 115%; code steps down to 95%.
This pair predates the Tailwind migration (it was `textFs` / `codeFs` in the old Kotlin CSS builder)
and it is the system's only typographic signature. Preserve it; don't normalize the two toward 100%.

**The Resident Type Rule.** No web font, ever. The stack is Verdana → Arial → Helvetica → sans-serif
and every one of those is already on the machine. A page that needs a typeface the school computer
doesn't have is a page that will render wrong in a lab.

**The Percent Rule.** Type sizes are expressed as percentages of the 16px root (`text-[115%]`), not
in pixels. A student who raises their browser's font size scales the entire document, including the
code they're reading.

## Layout

There is no grid system, no container component, and no breakpoint — the system defines zero
responsive utilities and the pages emit no viewport meta tag. Layout is document flow: an 8px margin
on `body`, tables that size to their content, and blocks that begin at the left edge and stop where
their content stops.

Two constants do all the structural work. **Prose is measured at 800px** (`p { max-width: 800px }`)
so a paragraph never runs the full width of a lab monitor. **Indentation is the primary hierarchy
device** — content nests by `ml-4` (1em) and `ml-8` (2em) rather than by boxes, borders, or
backgrounds. The About page, the Help page, and every explanatory block indent one em from the
heading above them and that is the entire visual expression of "belongs to."

The spacing scale is Tailwind's 4px steps (`4` / `8` / `16` / `32`) with one persistent outsider:
**15px**, which recurs as `m-[15px]`, `ml-[15px]`, and `border-spacing-x-[15px]` throughout the class
and student summary pages. It is a survivor from the pre-Tailwind stylesheet, it is off-scale, and it
is consistent enough to be treated as a real gutter value rather than as noise.

Tabular density is set by border-spacing rather than by cell padding: `th`/`td` carry 1px of padding
and tables separate their cells with explicit `border-spacing` (2.5/1, 15px/5px, or 5px/10px
depending on the table). The invocation grid pushes this to its limit at 1px spacing between 7px
cells.

The page header is a fixed vertical sequence, identical on every page: a utility bar (log in ·
about · help · admin · prefs) floated right, the wordmark, a message line, the language tabs, and a
1px black rule closing the header. The rule is the page's only full-width element.

### Named Rules

**The 800px Measure Rule.** Running prose never exceeds 800px, regardless of monitor width. Tables
and code blocks are exempt — they size to content.

**The Indent-Not-Box Rule.** Subordinate content indents by 1em or 2em. It does not get a card, a
tint, a border, or a background. Nesting is expressed by position alone.

## Elevation & Depth

The system is flat. No card, table, panel, or navigation element carries a shadow, and there is no
second surface color to layer with — everything sits directly on white paper, separated by 1px
hairlines. Depth, as a visual idea, is essentially absent from the working surfaces.

The single exception is deliberate and is the system's most useful rule: **a shadow means the thing
is pressable.** Every button — the global `input[type=submit]` / `button` base, Check Answers, the
admin buttons, the like/dislike controls, Clear History — carries a small drop shadow at rest and
swaps it for an *inset* shadow plus a 1px downward translate on `:active`. The button is the only
object in the system that appears to leave the page, and pressing it visibly pushes it back in.

One true overlay exists: the OAuth sign-in modal, which floats on a dimmed backdrop with a genuine
`0 4px 24px rgba(0,0,0,0.2)` ambient shadow and 8px corners. It is the only element in the system
allowed to look like it is above the page, and it earns that by actually being above the page.

### Shadow Vocabulary

- **Rest** (`box-shadow: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)`): the resting
  state of every button. Tight, low, barely visible — enough to read as an object, not enough to
  read as a card.
- **Pressed** (`box-shadow: inset 0 2px 4px 0 rgb(0 0 0 / 0.05)` + `transform: translateY(1px)`): the
  `:active` state of every button. Applied instantly, with no transition.
- **Overlay** (`box-shadow: 0 4px 24px rgba(0,0,0,0.2)`): the sign-in modal, and only the sign-in
  modal.

### Named Rules

**The Pressable Shadow Rule.** A shadow signals "you can press this." Surfaces that aren't controls
stay flat and are separated by hairlines. A shadowed card in this system would read as a giant
button.

**The Instant Feedback Rule.** State changes — a cell turning green, a button depressing, a panel
hiding — happen in one frame, with no transition. The single exception is `.rb-spinner`, the busy
indicator for answer checking, like/dislike, and admin commands; under `prefers-reduced-motion` it
slows to 2.4s rather than stopping, because it is the only signal that work is in flight.

## Shapes

Hairlines and right angles. A 1px border is the system's universal separator: it draws button edges
(`#D1D5DB`), field edges (`#767676`), invocation cells (black), group cards (gray-500), dashboards,
and the black rule that closes the page header. Nothing is separated by a fill.

Corners are minimal and stratified by function: **4px** (`0.25rem`) on the global control base and on
answer inputs, **6px** on the three named buttons, **8px** on the sign-in overlay, **1em (16px)** on
challenge-group cards — the softest shape in the system, and the only place a rounded container
appears — and **fully round** on the 36px OAuth avatar.

Two form details carry more identity than their size suggests:

**The folder tab.** Language tabs are `nav li` elements with a three-sided border
(`border-width: 1px 1px 0 1px`) — no bottom edge — set against the header's 1px black bottom rule.
The selected tab is pushed down 1px (`position: relative; top: 1px`) with a white background so it
covers the rule and merges into the page body. The tabs are bottom-aligned inline-blocks so that
1px lands exactly on the rule: an inline box is only as tall as the font's ascent plus descent,
which WebKit leaves fractional, and the nudge then falls short and leaves a hairline under the
selected language. It is a manila file-folder tab rendered in a handful of CSS declarations, and it
is the most characterful shape in the system.

The rule it sits on runs the full width of the viewport — it carries a negative horizontal margin
that cancels the body's 8px gutter, because a structural rule that stops short of the edge reads as
a mistake rather than as a margin. The strip itself is inset from the left by 37px, one tab gap, so
the first tab is spaced from the page edge the way the tabs are spaced from each other.

**The white gutter.** The answer feedback cell separates itself with a 7px *white* border rather than
with margin. When the cell fills green or red, that white border becomes the gap between color
chips — separation drawn in paper rather than in space.

### Named Rules

**The Hairline Rule.** Structure is drawn with 1px borders. Never with a background tint, never with
a shadow, never with a rule thicker than 1px — except the 10px code stripe, which is a label, not a
border.

## Components

### Buttons

- **Shape:** gently rounded (6px on named buttons, 4px on the global base), 1px `#D1D5DB` border.
- **Face:** Newsprint Gray (`#F1F1F1`) on every action button. There is no filled or colored button
  anywhere in the system; a primary action is distinguished by size and position, not by hue.
- **Check Answers** — the student's principal control: 224px × 32px, 100% type, bold. The largest
  control on any page.
- **Admin buttons** — 30px tall, 14px horizontal padding, 75% bold type. Small on purpose.
- **Clear History** — 4px/16px padding, 85% type, adds `hover:bg-gray-200`.
- **Generic (`BTN`)** — white face, gray-200 on hover. Used where a button must recede into a table.
- **States:** rest carries the Rest shadow; `:active` swaps to inset + `translateY(1px)`, instantly.
  Hover is styled only on Clear History and the generic variant. Interactive controls hold a 24px
  minimum hit area (`min-h-6`). **There is still no custom `:focus-visible` treatment** — the system
  relies on the browser default ring, which is preserved (nothing sets `outline: none`) but is weak
  against a Newsprint Gray face on white. Recorded as a real gap rather than invented away.

### Inputs / Fields

- **Style:** white face, 1px border, 4px corners. Answer inputs are 240px wide at 90% type with
  asymmetric padding (7px left, 5px elsewhere) that optically centers the typed value.
- **Philosophy:** system-native and barely styled. The base layer exists specifically to *restore*
  the native appearance Tailwind's preflight strips — `input[type=text]`, `input[type=password]`,
  and `textarea` are given back their border and white background rather than being given a new
  design. Controls should look like the operating system's, not like the site's.
- **Focus:** browser default. Not styled, not suppressed.

### Feedback Cell

The graded blank, and the component the whole system is arranged around. A 160px table cell with a
7px solid white border, filled by JavaScript on each answer check: white when unanswered, Grading
Green when correct, Marker Red when wrong. The white border keeps adjacent marks from touching.

The fill is never the only signal. Each cell is also labeled — **"✓ correct"** or **"✗ try again"** —
in centered bold black, which holds 7.1:1 on the green and 5.3:1 on the red. The two fills sit at
1.36:1 against *each other*, so for a red-green colorblind student the words are the message and the
color is the decoration.

### Invocation Grid

The signature component. A dense strip of 7px × 15px cells, each with a 1px black border and a
Newsprint Gray fill, separated by 1px border-spacing — one cell per challenge invocation, one row per
challenge. Cells fill green or red as students answer, so a teacher reads a whole class's progress as
a field of color at a glance, and a single student's history as a barcode. It is the system's only
data visualization and it uses no chart, no axis, and no legend.

### Cards / Containers

- **Challenge group card:** 322px fixed width (300px content + 10px padding + 1px border, border-box),
  15px margin, 10px padding, 1px gray-500 border, 1em corners. Cards wrap by inline flow rather than
  by grid. The group name inside is set at 155%.
- **Everything else:** not a card. Tables and indented blocks carry the rest of the system's content.

### Navigation

- **Language tabs:** 166% bold, three-sided folder-tab borders, 25px right / 6px left margins, 40px
  of horizontal padding inside the link, the strip inset 37px — one tab gap — from the left. Active
  tab is `#selected` — white ground, pushed 1px down over the header rule, which itself runs the
  full viewport width.
- **Utility bar:** small text links floated right above the wordmark — log in · about · help ·
  admin · prefs — separated by literal `|` characters. Items appear conditionally by role.
- **Links generally:** no underline; Link Blue at rest, Visited Purple once followed.

### Sign-In Overlay

The system's only modal and its only elevated surface. White panel, 8px corners, 32px padding, 300px
minimum width, centered text, ambient `0 4px 24px rgba(0,0,0,0.2)` shadow, and a `×` close affordance
at 22px in `#888`. Inside: a bold 18px "Sign In", muted-ink instruction copy, then two full-width
provider buttons in the providers' own brand colors — Google `#4285F4`, GitHub `#333333` — each with
its inline brand SVG, 10px/20px padding, 4px corners, 14px type. These are the only saturated fills
in the system, and they belong to Google and GitHub rather than to ReadingBat.

### Code Block

Prism-highlighted source with a 10px Ballpoint Blue left stripe, a doubling `-1px 0 0 0` shadow that
removes the seam, and a 1px `#DFDFDF` halo around the block. Set at 95%, offset by 2em top margin and
1em side margins. The Kotlin playground variant (CodeMirror) matches the 95% size and releases its
height constraint so code is never scrolled inside a box.

## Do's and Don'ts

### Do:

- **Do** put every class string in `TwClasses.kt` as a complete literal. The Tailwind v4 scanner
  reads `**/*.kt` by regex and cannot evaluate a Kotlin expression — a dynamically assembled class
  name silently produces no CSS.
- **Do** step reading copy up to 115% and code down to 95%. The pair is the system's typographic
  signature.
- **Do** separate structure with 1px hairlines and indentation (`ml-4` / `ml-8`), not with cards,
  tints, or shadows.
- **Do** keep new type sizes as percentages of the 16px root so browser zoom scales the document.
- **Do** reserve the shadow-plus-inset-on-active treatment for things the user can press.
- **Do** let green and red be *earned* — applied by a state change the user caused, never present on
  a static page.
- **Do** restore native form appearance when preflight strips it, rather than designing a
  replacement control.
- **Do** pair every color-coded state with a word or glyph. Color is a second channel here, never the
  only one.

### Don't:

- **Don't** spend red on anything but a wrong answer or an error message. The old
  `a:hover { color: red }` collision is resolved — hover is an underline now.
- **Don't** introduce a third green or a third red. There are exactly two of each — a fill and a
  text variant — and both live in `@theme`. The old `text-green-500` / `text-red-500` message colors
  and the dead `td.ok` / `td.no` rules are gone; route new work through `Message.colorClass`.
- **Don't** load a web font or an icon font. The type stack is resident, and the built Tailwind file
  plus Prism's local per-language CSS are the whole payload. The Font Awesome CDN load is gone,
  replaced by the `.rb-spinner` rule. (**Remaining:** the playground page loads `kotlin-playground`
  from unpkg — inherent to that embed.)
- **Don't** add transitions or animations to state changes. Feedback is instantaneous by design; the
  busy spinner is the only moving element, and it honors `prefers-reduced-motion`.
- **Don't** add a colored or filled button. Action buttons are Newsprint Gray; prominence comes from
  size and position.
- **Don't** style a surface with a shadow. A shadowed panel reads as a giant button in this system.
- **Don't** write new inline `style =` strings. **Known debt:** ~50 remain across 11 page files, but
  the static ones are now largely migrated — what is left is mostly genuinely dynamic (display
  toggles, computed cell colors). The direction of travel is toward `TwClasses` constants.
- **Don't** underline links at rest, or remove their color. Color is the resting link affordance and
  the convention is consistent site-wide; the underline belongs to `:hover` alone.
- **Don't** assume a breakpoint exists. The system defines zero responsive utilities and declares a
  fixed `width=1024` viewport — deliberately not `width=device-width`, which would trade a
  zoomed-but-coherent phone rendering for horizontal-scroll breakage. Adding a `md:` variant to one
  component without a system-wide decision produces a layout responsive in exactly one place.
