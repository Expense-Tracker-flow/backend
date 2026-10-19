-- Payment reminders: a bill the user wants a phone alert for before it is due, with a button to log it as an expense
CREATE TABLE IF NOT EXISTS payment_reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    payment_method VARCHAR(50),
    due_day INTEGER NOT NULL,
    remind_days_before INTEGER NOT NULL DEFAULT 1,
    next_due_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_reminders_user ON payment_reminders (user_id);
