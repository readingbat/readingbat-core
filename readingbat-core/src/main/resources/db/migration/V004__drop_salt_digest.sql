-- Remove unused salt and digest columns from users table.
-- These were part of the original form-based auth, replaced by OAuth.
ALTER TABLE users DROP COLUMN IF EXISTS salt;
ALTER TABLE users DROP COLUMN IF EXISTS digest;
