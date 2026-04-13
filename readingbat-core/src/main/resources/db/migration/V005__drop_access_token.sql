-- Remove access_token column from oauth_links.
-- The token was only used transiently during OAuth callbacks and never read back.
ALTER TABLE oauth_links DROP COLUMN IF EXISTS access_token;
