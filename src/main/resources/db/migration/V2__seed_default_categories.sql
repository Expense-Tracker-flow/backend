-- Seed System Default Categories

INSERT INTO categories (user_id, name, icon, color, type, is_system) VALUES
-- Expense Categories
(NULL, 'Food & Dining', 'utensils', '#F59E0B', 'EXPENSE', TRUE),
(NULL, 'Shopping', 'shopping-bag', '#EC4899', 'EXPENSE', TRUE),
(NULL, 'Bills & Utilities', 'receipt', '#6366F1', 'EXPENSE', TRUE),
(NULL, 'Transportation', 'car', '#3B82F6', 'EXPENSE', TRUE),
(NULL, 'Groceries', 'shopping-cart', '#10B981', 'EXPENSE', TRUE),
(NULL, 'Entertainment', 'film', '#8B5CF6', 'EXPENSE', TRUE),
(NULL, 'Health & Medical', 'activity', '#EF4444', 'EXPENSE', TRUE),
(NULL, 'Travel', 'plane', '#14B8A6', 'EXPENSE', TRUE),
(NULL, 'Education', 'book-open', '#F97316', 'EXPENSE', TRUE),
(NULL, 'Other Expense', 'credit-card', '#64748B', 'EXPENSE', TRUE),

-- Income Categories
(NULL, 'Salary', 'briefcase', '#10B981', 'INCOME', TRUE),
(NULL, 'Freelance', 'laptop', '#3B82F6', 'INCOME', TRUE),
(NULL, 'Investments', 'trending-up', '#8B5CF6', 'INCOME', TRUE),
(NULL, 'Refunds', 'rotate-ccw', '#F59E0B', 'INCOME', TRUE),
(NULL, 'Other Income', 'plus-circle', '#64748B', 'INCOME', TRUE);
