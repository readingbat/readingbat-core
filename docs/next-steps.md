# Next Steps

Things you need to do to complete the OAuth migration.

## 1. Register OAuth Applications

You need to create OAuth apps with both providers. See `docs/oauth-setup.md` for detailed instructions.

## 2. Configure Secrets

Add your OAuth credentials to `secrets/secrets.env`:

```
GITHUB_OAUTH_CLIENT_ID=your_github_client_id
GITHUB_OAUTH_CLIENT_SECRET=your_github_client_secret
GOOGLE_OAUTH_CLIENT_ID=your_google_client_id
GOOGLE_OAUTH_CLIENT_SECRET=your_google_client_secret
```

## 3. Run Database Migration

Apply the Flyway migration to your PostgreSQL database:

```bash
make dbmigrate
```

This will:
- Create the `oauth_links` table
- Make `salt`/`digest` nullable on `users`
- Add `auth_provider` column to `users`
- Drop `password_resets`, `session_answer_history`, and `session_challenge_info` tables

**Important:** Back up your database before running the migration.

## 4. Run Tests

```bash
make tests
```

Tests that reference form-based auth (create account, password reset) may need updates or removal.
The test content defined in `TestData.kt` should still work, but integration tests that simulate
login flows will need to be adapted for OAuth.

## 5. Manual Testing

- Visit the app unauthenticated -> should redirect to OAuth login page
- Public pages (`/help`, `/about`, `/privacy`, `/clock`) should be accessible without login
- Sign in with GitHub -> should create account and redirect to content
- Sign in with Google -> should create account and redirect to content
- Log out -> should redirect to login page
- Existing users whose OAuth email matches their ReadingBat email should be auto-linked
- User prefs page should no longer show password change section
- Challenge answers should be saved correctly for authenticated users

## 6. Production Deployment

- Set the `EMAIL_PREFIX` property (or env var) to your production URL
  (e.g., `https://readingbat.com`) so OAuth callback URLs resolve correctly
- Ensure the callback URLs registered with GitHub/Google match your production domain
- Deploy the database migration before deploying the new code

## 7. Existing User Migration Notes

- Users whose OAuth email matches their existing ReadingBat email will be auto-linked
  on first OAuth login (preserving their answer history)
- Users whose OAuth email does NOT match will get a new account
- There is no manual account linking flow — consider adding one if needed
- Legacy password-only users can no longer log in until they use OAuth with a matching email

## 8. Optional Cleanup

- Remove commented-out code in `TransferUsers.kt` (references removed session tables)
- Remove `NO_AUTH_KEY` constant from `Keys.kt` (no longer used)
- Consider removing `BrowserSession.correctAnswersKey` and related methods from `Cookies.kt`
  if confirmed unused
- Update any external documentation or help pages that reference password login
