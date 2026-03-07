# OAuth Upgrade Summary

This document summarizes all changes made to replace form-based email/password authentication
with GitHub and Google OAuth.

## Phase 1: Database Migration & Configuration

### Flyway Migration (`src/main/resources/db/migration/V002__oauth_migration.sql`)
- Created `oauth_links` table with columns: `id`, `created`, `updated`, `user_ref` (FK to `users`),
  `provider`, `provider_id`, `provider_email`, `access_token`
- Added unique constraint on `(provider, provider_id)`
- Made `users.salt` and `users.digest` nullable (existing users have values, OAuth users won't)
- Added `users.auth_provider` column (tracks which OAuth provider created the account)
- Dropped `password_resets` table
- Dropped `session_answer_history` and `session_challenge_info` tables

### PostgresTables.kt
- Added `OAuthLinksTable` object and `oauthLinksProviderIndex`
- Added `UsersTable.authProvider` (nullable text column)
- Made `UsersTable.salt` and `UsersTable.digest` nullable
- Removed `SessionChallengeInfoTable`, `SessionAnswerHistoryTable`, `PasswordResetsTable` and their indexes

### EnvVar.kt
- Replaced `GITHUB_OAUTH` with: `GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET`,
  `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`

### Property.kt
- Added 4 OAuth `Property` entries under `readingbat.site.*`
- Added all 4 to `initProperties()` list

### Constants.kt (Endpoints & FormFields)
- Added: `OAUTH_LOGIN_ENDPOINT`, `OAUTH_LOGIN_GITHUB_ENDPOINT`, `OAUTH_LOGIN_GOOGLE_ENDPOINT`,
  `OAUTH_CALLBACK_GITHUB_ENDPOINT`, `OAUTH_CALLBACK_GOOGLE_ENDPOINT`
- Added: `AuthName.GITHUB_OAUTH`, `AuthName.GOOGLE_OAUTH`
- Removed: `CREATE_ACCOUNT_ENDPOINT`, `PASSWORD_RESET_ENDPOINT`, `PASSWORD_CHANGE_ENDPOINT`,
  `INVALID_RESET_ID`, `AuthName.FORM`
- Removed FormFields: `PASSWORD_PARAM`, `CONFIRM_PASSWORD_PARAM`, `FULLNAME_PARAM`,
  `CURR_PASSWORD_PARAM`, `NEW_PASSWORD_PARAM`, `RESET_ID_PARAM`, `UPDATE_PASSWORD`

### HOCON Config
- Added OAuth placeholders to `application-test.conf`

---

## Phase 2: OAuth Configuration & Mandatory Auth

### ConfigureOAuth.kt (NEW)
- Configures GitHub and Google OAuth2 providers using Ktor's `oauth()` DSL
- Reads client ID/secret from `Property`; gracefully skips if not configured
- Resolves callback URLs using `EMAIL_PREFIX` property
- Shares an `HttpClient(CIO)` instance for OAuth flows and user-info API calls

### Installs.kt
- Replaced `configureFormAuth()` with `configureGitHubOAuth()` and `configureGoogleOAuth()`

### Intercepts.kt
- Added mandatory auth intercept in `Plugins` phase
- Unauthenticated requests to non-public paths redirect to `OAUTH_LOGIN_ENDPOINT`
- Public paths whitelist: `/oauth/*`, `/help`, `/about`, `/privacy`, `/static/*`, `/favicon.ico`,
  `/robots.txt`, `/css.css`, `/clock`

---

## Phase 3: OAuth Routes, Login Page, User Model

### OAuthRoutes.kt (NEW)
- `GET /oauth/login` — renders OAuth login page with GitHub/Google buttons
- GitHub flow: `authenticate(GITHUB_OAUTH)` wraps login and callback endpoints
- Google flow: `authenticate(GOOGLE_OAUTH)` wraps login and callback endpoints
- Callback handlers fetch user info from provider APIs (name, email)
- `findOrCreateOAuthUser()` implements 3-step logic:
  1. Look up `oauth_links(provider, provider_id)` -> log in existing user
  2. Email matches existing user with same/null provider -> auto-link
  3. No match -> create new user via `User.createOAuthUser()`

### OAuthLoginPage.kt (NEW)
- Simple page with "Sign in with GitHub" and "Sign in with Google" buttons

### User.kt
- Added `createOAuthUser(name, email, provider, providerId, accessToken)`
- Removed: `salt`/`digest` properties, `assignDigest()`, `userPasswordResetId()`,
  `savePasswordResetId()`, `createUser(name, email, password, browserSession)`

### Locations.kt
- Removed `Password` and `ResetId` value classes

### ReadingBatServer.kt
- Added `oauthRoutes()` call in the routing block

---

## Phase 4: Remove Password Auth Code

### Files Deleted
- `posts/CreateAccountPost.kt` — Account creation via form
- `posts/PasswordResetPost.kt` — Password reset flow
- `pages/CreateAccountPage.kt` — Account creation page
- `pages/PasswordResetPage.kt` — Password reset page
- `server/ConfigureFormAuth.kt` — Form-based authentication configuration

### Files Modified
- `UserRoutes.kt` — Removed routes for create account, password reset, password change
- `HelpAndLogin.kt` — Replaced email/password login form with OAuth login link
- `UserPrefsPage.kt` — Removed password change section
- `UserPrefsPost.kt` — Removed `updatePassword()` method and `UPDATE_PASSWORD` case

---

## Phase 5: Remove Session-Based Answer Tracking

Since all users are now authenticated, anonymous session-based answer tracking is no longer needed.

### ChallengePost.kt
- Removed `BrowserSession` parameter from `saveChallengeAnswers` and `saveLikeDislike`
- Removed `NO_AUTH_KEY` branches in `deleteChallengeInfo` and `deleteAnswerHistory`
- Simplified to only use `User*` tables

### Challenge.kt
- Changed `isCorrect(user, browserSession)` to `isCorrect(user)`
- Removed `SessionChallengeInfoTable` branch

### ChallengePage.kt
- Removed `browserSession` from `displayQuestions`, `likeDislike`,
  `clearChallengeAnswerHistoryOption`, `fetchPreviousResponses`
- Simplified to user-only lookup

### ChallengeGroupPage.kt
- Removed `browserSession` from `clearGroupAnswerHistoryOption` and `isCorrect` call

### LanguageGroupPage.kt
- Removed `browserSession` from `isCorrect` call

### Cookies.kt
- Removed `correctAnswersKey`, `challengeAnswerKey`, `likeDislike`, `answerHistory` methods
  from `BrowserSession`
- Kept `BrowserSession` class for session management (FK dependencies from `server_requests`
  and `user_sessions`)

### Keys.kt
- Simplified all key functions to remove `browserSession` fallback

---

## What Was Kept

- `BrowserSession` class and `browser_sessions` table — still used by `server_requests` and
  `user_sessions` tables for request logging
- `UserPrincipal` — still used for OAuth session cookies
- All `User*` tables (`UserChallengeInfoTable`, `UserAnswerHistoryTable`, etc.) — unchanged
- All WebSocket, admin, teacher, and class management functionality — unchanged
