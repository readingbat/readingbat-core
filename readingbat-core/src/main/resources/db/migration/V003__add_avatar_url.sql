-- Add avatar URL column to users table for OAuth provider profile pictures
ALTER TABLE users ADD COLUMN avatar_url TEXT;
