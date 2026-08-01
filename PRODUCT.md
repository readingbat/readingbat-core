# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

**Primary — students learning to program.** They work on school computers and Chromebooks, in a
classroom or lab, usually on work a teacher assigned. A student is handed a challenge: a short
function in Java, Python, or Kotlin, plus a column of calls to it. They read the code, predict what
each call returns, type the answers, and check them. The job is comprehension, not authorship.

**Teachers.** They create a class, hand out its class code, and watch progress. A teacher selects an
active class to enter *teacher mode*, then toggles between teacher and student views from the page
header. Class summary and student summary pages update live over WebSockets as students answer.

**Developers running their own instance.** `readingbat-core` is published to Maven Central under
Apache 2.0 and is designed to be self-hosted with challenge content the operator authors themselves
(see the `readingbat-template` repo). The UI in this repository is the UI they ship — anything
designed here reaches ReadingBat.com *and* every self-hosted instance.

**Administrators.** Admin, system-admin, system-configuration, and sessions pages exist for
operating a deployment.

## Product Purpose

ReadingBat teaches people to *read* code before asking them to write it. The premise, stated on the
About page and binding: students often start writing code before they can follow code, and it is
difficult to write what you cannot read. ReadingBat makes students comfortable reading code and
recognizing idioms, then explicitly hands them off — "once a student is comfortable with reading
code, they can head straight for CodingBat.com and move on to authoring their own code."

Success is a student who can look at an unfamiliar function and correctly say what it does, and a
teacher who can see at a glance which students got there.

## Positioning

The mechanism a neighboring product could not truthfully copy: challenges are **not a fixed
catalog**. They are defined in a Kotlin DSL (`readingBatContent { }`) that is evaluated at runtime
via JSR-223 script engines and sourced from either the local filesystem or a GitHub repository. Any
teacher, department, or developer can stand up their own instance with their own challenges in their
own languages, and the platform renders it. ReadingBat.com is one deployment of a thing anyone can
run.

Free and open source, Apache 2.0. There is no paid tier, no gated feature, and no commercial
offering of any kind.

## Operating Context

- **The scene is a desktop-width school machine.** Classroom or lab, teacher-assigned work.
  Phone support is not a live need. Today there is no viewport meta tag and zero responsive
  utilities in the codebase — the layout is fixed desktop by circumstance as much as by decision.
  Whether narrow screens ever need support is *not decided*; the current record is that they do not.
- **The class ritual:** teacher creates a class → distributes the class code → students join from
  the prefs page → teacher selects the active class → teacher mode reveals class and per-student
  progress. Students and teachers can hold both roles; enrolling in your own class is supported and
  documented as the way to self-demo.
- **Sign-in is OAuth-only** — Google or GitHub, and the provider must return a verified email. There
  is no password, no create-account form, and no password-reset flow; those were removed. The
  browser session is rotated on login.
- **Almost everything requires an account.** `Intercepts.publicPaths` leaves only the root page,
  the OAuth endpoints, `/help`, `/about`, `/privacy`, `/tos`, `/ping`, favicon, robots, and
  `/static/*` open. All challenge content is behind login, so a student cannot try a challenge
  before authenticating.
- **Reading surfaces per language:** challenge pages load per-language Prism.js syntax highlighting;
  the playground page embeds KotlinPlayground/CodeMirror for live Kotlin.
- **Deployment:** PostgreSQL required; targets include Heroku, Google Cloud Run, DigitalOcean, and
  Docker. Multi-node deployments must share one `SESSION_SECRET`, and rotating it signs everyone out
  once.

## Capabilities and Constraints

- 27 server-rendered pages generated with Kotlinx.html, plus hand-written JavaScript emitted from
  `pages/js/` for answer checking, like/dislike, and admin commands.
- **Tailwind is the binding styling constraint (confirmed).** Styling goes through the `tw-`
  prefixed Tailwind build (`make tw-css` / `:readingbat-core:tailwindBuild`) and the `TwClasses.kt`
  constants. The former Kotlin `CssBuilder` layer (`CssContent.kt`) is gone and is not coming back;
  no hand-rolled stylesheet, no drift back to inline `style` strings.
- **Deliberately *not* locked:** server-side Kotlinx.html rendering, and the third-party embeds
  (Prism.js, KotlinPlayground/CodeMirror, and the Bootstrap 3 dropdown still used by the class
  summary page). These are the current implementation, not commitments — future work may propose
  replacing any of them. Bootstrap 3 is EOL and is a known liability.
- **Content is authored elsewhere.** Challenge count, group names, function signatures, and
  description length all come from an operator's DSL. No page may assume a particular catalog,
  a maximum number of groups, or short descriptions.
- Real-time updates ride WebSockets: challenge answers, class summary, student progress.
- Roles the UI must express: anonymous browser session, signed-in student, teacher with an active
  class, admin/system admin.

## Brand Commitments

- **Name and wordmark:** "ReadingBat", set as text with the tagline *"code reading practice"*
  (`PageUtils.bodyTitle`). There is no logo image in use.
- **Voice:** plain, direct, teacherly. First-person plural ("we"), no marketing register, no
  growth-copy superlatives.
- **Durable copy, all four confirmed binding:**
  1. the read-before-write premise — do not soften or reframe it;
  2. the CodingBat lineage — the inspiration, the outbound links, and the explicit hand-off stay
     factual and present;
  3. the father-son origin — ReadingBat as an effort by Paul and Matthew Ambrose;
  4. free and open source — never introduce pricing, gating, or implied commercial claims.
- **Contact and homes:** `suggestions@readingbat.com`; docs at
  <https://readingbat.github.io/readingbat-core/>; content template at
  `github.com/readingbat/readingbat-template`.
- Recorded as fact, not as a commitment: the About page states the site "shamelessly copied"
  CodingBat's look and feel. Whether that visual lineage continues is a visual-world decision and is
  **not** settled by this record.

## Evidence on Hand

Real and usable:

- About and Help page copy — including a step-by-step self-driven demo of the student/teacher flow.
- Seven help screenshots in `readingbat-core/src/main/resources/static/help/` (challenge feedback,
  class options, class summary, create account, group summary, student roster, student summary).
- The DSL itself, the template repository, the documentation site under `website/`, `LICENSE`
  (Apache 2.0), `CHANGELOG.md`, and `RELEASE_NOTES.md`.
- In-page imagery already referenced by the UI: green/white check marks, like/dislike icons,
  `nervous.png` / `panic.png`, `dbmsdown.jpg`, `run-button.png`. A few files in `static/` are stale
  and referenced by nothing (`s5j.png`, the x-ray-glasses images) — they are not identity assets.

Absent — **must not be fabricated**: there are no testimonials, no named schools, districts, or
customers, no user or usage counts, no learning-outcome or efficacy data, no pricing or plans, no
team beyond the two people named on the About page, and no compliance certifications.

## Product Principles

1. **Comprehension outranks everything on the page.** The code being read is the product; any
   element that competes with it for attention is a cost.
2. **Design for the classroom machine.** Desktop-width school hardware, teacher-assigned work — and
   a teacher who needs to read a whole class's state in one glance.
3. **The content is not ours.** Never design around a specific challenge, group, or catalog size;
   an operator's DSL decides what appears.
4. **Whatever ships here ships everywhere.** This UI is ReadingBat.com and every self-hosted
   instance at once, so no ReadingBat.com-only assumptions.
5. **Free and open, in substance and in tone.** No gating, no upsell surfaces, no commercial framing.

## Accessibility & Inclusion

No formal standard has been established — no district, procurement, or legal requirement is on
record, so **no compliance level may be claimed**. The expectation is accessible-by-default craft:
real labels, working keyboard paths, visible focus, and sufficient contrast, as a matter of quality
rather than of certification.

Current state, as debt rather than a standard: essentially no ARIA across the page set, `alt` text
on only two images, and no deliberate focus treatment. Answer entry is keyboard-driven (tab triggers
answer checking), so keyboard behavior is load-bearing and must not regress.
