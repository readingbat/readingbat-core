# OAuth Provider Setup

How to register OAuth applications with GitHub and Google for ReadingBat.

## GitHub OAuth App

### 1. Create the App

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click **"OAuth Apps"** in the sidebar
3. Click **"New OAuth App"**
4. Fill in the form:
   - **Application name:** `ReadingBat` (or your preferred name)
   - **Homepage URL:** `http://localhost:8080` (or your production URL)
   - **Authorization callback URL:** `http://localhost:8080/oauth/callback/github`
5. Click **"Register application"**

### 2. Get Credentials

After registration:
- Copy the **Client ID**
- Click **"Generate a new client secret"** and copy the secret

### 3. Configure

Add to `secrets/secrets.env`:
```
GITHUB_OAUTH_CLIENT_ID=<your client id>
GITHUB_OAUTH_CLIENT_SECRET=<your client secret>
```

Or set in HOCON config (`application.conf`):
```hocon
readingbat.site {
  githubOAuthClientId = "<your client id>"
  githubOAuthClientSecret = "<your client secret>"
}
```

### Scopes

The app requests `user:email` scope, which allows reading the user's email address
(including private emails via the `/user/emails` API endpoint).

### Production Callback URL

For production, update the callback URL in your GitHub OAuth App settings to:
```
https://your-domain.com/oauth/callback/github
```

---

## Google OAuth App

### 1. Create the App

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project (or select an existing one)
3. Navigate to **"APIs & Services" > "Credentials"**
4. Click **"Create Credentials" > "OAuth client ID"**
5. If prompted, configure the **OAuth consent screen** first:
   - Choose **"External"** user type
   - Fill in the app name (`ReadingBat`), user support email, and developer email
   - Add scopes: `openid`, `profile`, `email`
   - Add any test users if in testing mode
6. Back on the Credentials page, click **"Create Credentials" > "OAuth client ID"**:
   - **Application type:** Web application
   - **Name:** `ReadingBat`
   - **Authorized redirect URIs:** `http://localhost:8080/oauth/callback/google`
7. Click **"Create"**

### 2. Get Credentials

After creation:
- Copy the **Client ID**
- Copy the **Client secret**

### 3. Configure

Add to `secrets/secrets.env`:
```
GOOGLE_OAUTH_CLIENT_ID=<your client id>
GOOGLE_OAUTH_CLIENT_SECRET=<your client secret>
```

Or set in HOCON config (`application.conf`):
```hocon
readingbat.site {
  googleOAuthClientId = "<your client id>"
  googleOAuthClientSecret = "<your client secret>"
}
```

### Scopes

The app requests `openid`, `profile`, and `email` scopes, which provide the user's
name and email address via the Google userinfo endpoint.

### Production Redirect URI

For production, add the production redirect URI in Google Cloud Console:
```
https://your-domain.com/oauth/callback/google
```

---

## Callback URL Resolution

The callback URLs are built at runtime using the `EMAIL_PREFIX` property:

```
callback_url = EMAIL_PREFIX + /oauth/callback/github (or /google)
```

`EMAIL_PREFIX` defaults to `http://localhost:8080`. For production, set it to your domain:

```
EMAIL_PREFIX=https://your-domain.com
```

This can be set via:
- HOCON property: `readingbat.site.emailPrefix`
- Environment variable: `EMAIL_PREFIX`

Make sure the computed callback URL matches exactly what you registered with each provider.

---

## Testing Locally

1. Register both OAuth apps with `http://localhost:8080` callback URLs
2. Add credentials to `secrets/secrets.env`
3. Run `make dbmigrate` to apply the database migration
4. Run `make run` to start the server
5. Visit `http://localhost:8080` — you should be redirected to the OAuth login page
6. Click "Sign in with GitHub" or "Sign in with Google" to test the flow

---

## Troubleshooting

**"GitHub OAuth not configured" / "Google OAuth not configured" in logs:**
The client ID or secret is blank. Check your `secrets/secrets.env` file and ensure the values
are set correctly.

**Redirect URI mismatch error from provider:**
The callback URL computed at runtime doesn't match what's registered with the provider.
Check `EMAIL_PREFIX` and ensure it matches the domain in your provider's OAuth app settings.

**User gets a new account instead of being linked to existing one:**
Auto-linking only works when the OAuth email matches the existing user's email AND the
`auth_provider` column is either null or matches the current provider. If the emails differ,
a new account is created.
