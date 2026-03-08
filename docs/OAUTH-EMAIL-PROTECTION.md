# OAuth Cross-Provider Email Linking

## The Problem

ReadingBat supports multiple OAuth providers (GitHub and Google). A single user may have accounts
on both providers registered with the same email address. Without cross-provider linking, signing in
with GitHub first and then later with Google (or vice versa) would create **two separate ReadingBat
accounts** — each with its own challenge progress, class enrollments, and answer history. From the
user's perspective, their work would appear to vanish when they sign in with the other provider.

## The Risk: Account Takeover via Unverified Email

The naive fix — auto-linking any OAuth account that shares an email with an existing user — introduces
a serious security vulnerability: **account takeover via unverified email**.

An attacker could:

1. Discover that `victim@example.com` has a ReadingBat account (e.g., via a public class roster).
2. Create an OAuth account on a provider that does not verify email addresses, registering
   `victim@example.com` as their email.
3. Sign in to ReadingBat with that provider.
4. The system would auto-link the attacker's OAuth identity to the victim's account, granting full
   access to the victim's data, enrolled classes, and (if the victim is a teacher) student records.

This is a well-documented attack vector. Services like GitLab, Slack, and others have issued CVEs
for exactly this class of bug.

## Why Our Solution Is Safe

ReadingBat's cross-provider linking is safe because **both configured providers only return verified
emails**:

### GitHub

The OAuth callback code explicitly filters for verified emails:

```kotlin
emails.firstOrNull { it.primary && it.verified }?.email
  ?: emails.firstOrNull { it.verified }?.email
```

GitHub's `/user/emails` API includes a `verified` boolean for each email. Only emails that the user
has confirmed via a verification link are marked `verified: true`. Unverified emails are never used
for account linking.

### Google

Google's OAuth2 userinfo endpoint (`googleapis.com/oauth2/v2/userinfo`) only returns the email
associated with the user's Google account. Google accounts require email verification during
registration, so any email returned by this endpoint is inherently verified.

## How It Works

Account resolution follows a three-step lookup in `findOrCreateOAuthUser()`:

### Step 1: Existing OAuth Link

Check `OAuthLinksTable` for a matching `(provider, providerId)` pair. If found, return the linked
user immediately. This is the fast path for repeat logins.

### Step 2: Email Match (Cross-Provider Linking)

If no OAuth link exists, look up the email in `UsersTable`. If a user with that email exists,
**auto-link** the new provider to the existing account by inserting a new row in `OAuthLinksTable`.
This works regardless of which provider the user originally signed in with.

After linking, the user has multiple rows in `OAuthLinksTable` (one per provider), all pointing to
the same `UsersTable` entry. Future logins via either provider resolve via Step 1.

### Step 3: New User

If neither an OAuth link nor an email match is found, create a new user account and a corresponding
`OAuthLinksTable` entry.

## Database Schema

After a user has signed in with both providers, the tables look like:

```
UsersTable
+----+---------------------+---------------+
| id | email               | auth_provider |
+----+---------------------+---------------+
| 42 | user@example.com    | github        |
+----+---------------------+---------------+

OAuthLinksTable
+----+----------+----------+-------------+---------------------+
| id | user_ref | provider | provider_id | provider_email      |
+----+----------+----------+-------------+---------------------+
|  1 |       42 | github   | 12345678    | user@example.com    |
|  2 |       42 | google   | 98765...    | user@example.com    |
+----+----------+----------+-------------+---------------------+
```

## Adding a New OAuth Provider

If a new OAuth provider is added to ReadingBat, verify that:

1. **The provider's API guarantees email verification.** Check the provider's documentation for
   whether the email field in the user info response is verified. If the provider returns unverified
   emails, you must either:
   - Filter for verified emails explicitly (as done with GitHub), or
   - Skip cross-provider linking for that provider and create a separate account.

2. **The email is not blank.** The linking code is gated behind `if (email.isNotBlank())`. Providers
   that don't return an email (e.g., Twitter/X) will always create separate accounts, which is the
   correct behavior.

## References

- [OWASP: Account Linking](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) —
  guidance on safely linking third-party identities
- [GitLab CVE-2022-1680](https://about.gitlab.com/releases/2022/06/01/critical-security-release-gitlab-15-0-1-released/) —
  account takeover via unverified SAML email, same class of vulnerability
