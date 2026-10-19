-- The user's timezone, so daily reminders are sent at their local evening
ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
