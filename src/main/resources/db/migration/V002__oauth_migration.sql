-- Add OAuth links table
CREATE TABLE oauth_links
(
    id             BIGSERIAL PRIMARY KEY,
    created        TIMESTAMPTZ DEFAULT NOW(),
    updated        TIMESTAMPTZ DEFAULT NOW(),
    user_ref       BIGINT NOT NULL REFERENCES users ON DELETE CASCADE,
    provider       TEXT NOT NULL,
    provider_id    TEXT NOT NULL,
    provider_email TEXT NOT NULL DEFAULT '',
    access_token   TEXT NOT NULL DEFAULT '',
    CONSTRAINT oauth_links_provider_unique UNIQUE (provider, provider_id)
);

CREATE INDEX idx_oauth_links_user_ref ON oauth_links (user_ref);

-- Make salt/digest nullable (existing users have values, OAuth users won't)
ALTER TABLE users ALTER COLUMN salt DROP NOT NULL;
ALTER TABLE users ALTER COLUMN digest DROP NOT NULL;

-- Track which OAuth provider created the account (null for legacy password users)
ALTER TABLE users ADD COLUMN auth_provider TEXT;

-- Drop password_resets table
DROP TABLE IF EXISTS password_resets;

-- Drop session-based answer tracking tables (browser_sessions kept for request logging)
DROP TABLE IF EXISTS session_answer_history;
DROP TABLE IF EXISTS session_challenge_info;
