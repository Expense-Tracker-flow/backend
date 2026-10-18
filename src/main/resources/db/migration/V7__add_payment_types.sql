-- User-defined payment types. Built-in types (UPI, CASH, ...) stay as enum codes.
-- A transaction's payment_method holds either a built-in code or the id of one of these rows.
CREATE TABLE IF NOT EXISTS payment_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_types_user_name ON payment_types (user_id, lower(name));
CREATE INDEX IF NOT EXISTS idx_payment_types_user ON payment_types (user_id);
