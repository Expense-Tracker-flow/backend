-- Count wrong code attempts so a reset code cannot be brute-forced
ALTER TABLE email_otps ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0;
