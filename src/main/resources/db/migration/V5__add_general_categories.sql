-- Seed General Default Categories (EXPENSE & INCOME)
INSERT INTO categories (user_id, name, icon, color, type, is_system)
SELECT NULL, 'General', 'tag', '#64748B', 'EXPENSE', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'general' AND type = 'EXPENSE'
);

INSERT INTO categories (user_id, name, icon, color, type, is_system)
SELECT NULL, 'General', 'tag', '#64748B', 'INCOME', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'general' AND type = 'INCOME'
);
