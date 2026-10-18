package com.flow.transaction.service;

import com.flow.category.dto.CategoryResponse;
import com.flow.category.entity.CategoryType;
import com.flow.transaction.dto.ParsedTransactionResponse;
import com.flow.transaction.entity.PaymentMethod;
import com.flow.transaction.entity.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.stream.Collectors.joining;

/**
 * Rule-based reader for short sentences such as "spent 250 on groceries by UPI yesterday".
 * It returns a draft only. Saving is done by the caller after the user confirms.
 */
@Component
public class TransactionTextParser {

    private static final String MONTH = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*";
    private static final List<String> MONTHS = List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");

    private static final Pattern TODAY = Pattern.compile("\\btoday\\b", CASE_INSENSITIVE);
    private static final Pattern YESTERDAY = Pattern.compile("\\byesterday\\b", CASE_INSENSITIVE);
    private static final Pattern DAYS_AGO = Pattern.compile("\\b(\\d{1,3})\\s+days?\\s+ago\\b", CASE_INSENSITIVE);
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern DAY_MONTH = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+" + MONTH + "\\b(?:,?\\s+(\\d{4}))?", CASE_INSENSITIVE);
    private static final Pattern MONTH_DAY = Pattern.compile("\\b" + MONTH + "\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s+(\\d{4}))?", CASE_INSENSITIVE);
    // Day first, as used in India, South Africa and the GCC
    private static final Pattern DMY_DATE = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}|\\d{2}))?\\b");

    private static final Pattern AMOUNT = Pattern.compile(
            // The lookahead skips ranges such as "last 7 days", which are queries rather than amounts
            "(?<![a-z0-9])(?:₹|rs\\.?|inr|\\$|£|€)?\\s*(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)\\s*(k|thousand|lakhs?)?(?![a-z0-9])(?!\\s*(?:days?|weeks?|months?|years?)\\b)",
            CASE_INSENSITIVE);

    private static final Pattern INCOME_CUE = Pattern.compile(
            "\\b(received|receive|salary|credited|credit|income|earned|earn|got paid|refund(ed)?|bonus|deposit(ed)?|freelance|dividend|interest|sold|cashback)\\b",
            CASE_INSENSITIVE);

    private static final Pattern QUESTION_CUE = Pattern.compile(
            "^\\s*(how|what|show|list|when|which|total|summary|balance|recent|help)\\b|\\?\\s*$|\\b(how much|how many)\\b",
            CASE_INSENSITIVE);

    private static final List<Map.Entry<Pattern, PaymentMethod>> PAYMENT_RULES = List.of(
            Map.entry(Pattern.compile("\\b(upi|gpay|google ?pay|phone ?pe|paytm|bhim|amazon ?pay)\\b", CASE_INSENSITIVE), PaymentMethod.UPI),
            Map.entry(Pattern.compile("\\bcredit ?card\\b|\\bcc\\b", CASE_INSENSITIVE), PaymentMethod.CREDIT_CARD),
            Map.entry(Pattern.compile("\\bdebit ?card\\b", CASE_INSENSITIVE), PaymentMethod.DEBIT_CARD),
            Map.entry(Pattern.compile("\\b(bank ?transfer|neft|imps|rtgs|net ?banking|transfer)\\b", CASE_INSENSITIVE), PaymentMethod.BANK_TRANSFER),
            Map.entry(Pattern.compile("\\bcash\\b", CASE_INSENSITIVE), PaymentMethod.CASH)
    );

    // Keyword rules map a sentence to one of the seeded category names. A user's own category with a matching name wins.
    private record CategoryRule(Pattern pattern, String categoryName) {
        CategoryRule(String regex, String categoryName) {
            this(Pattern.compile("\\b(" + regex + ")\\b", CASE_INSENSITIVE), categoryName);
        }
    }

    private static final List<CategoryRule> EXPENSE_RULES = List.of(
            new CategoryRule("food|restaurant|cafe|coffee|tea|lunch|dinner|breakfast|snack|pizza|burger|swiggy|zomato|meal|eating out", "Food & Dining"),
            new CategoryRule("grocer(y|ies)|supermarket|vegetables?|fruits?|milk|bigbasket|blinkit|dmart|kirana", "Groceries"),
            new CategoryRule("uber|ola|taxi|cab|auto|rickshaw|fuel|petrol|diesel|metro|bus|train|rapido|parking|toll", "Transportation"),
            new CategoryRule("rent|electricity|water bill|internet|wi-?fi|broadband|phone bill|recharge|bills?|utilit(y|ies)|gas bill|maintenance", "Bills & Utilities"),
            new CategoryRule("shopping|amazon|flipkart|myntra|clothes|clothing|shoes|mall|gadgets?|electronics", "Shopping"),
            new CategoryRule("movies?|netflix|spotify|prime video|games?|concert|cinema|hotstar|party", "Entertainment"),
            new CategoryRule("doctor|medicines?|medical|pharmacy|hospital|clinic|gym|dentist|health|lab test", "Health & Medical"),
            new CategoryRule("flights?|hotels?|trips?|travel|airbnb|airfare|vacation|holiday", "Travel"),
            new CategoryRule("courses?|books?|tuition|school|college|fees?|education|exams?|udemy", "Education")
    );

    private static final List<CategoryRule> INCOME_RULES = List.of(
            new CategoryRule("salary|payroll|wages?", "Salary"),
            new CategoryRule("freelance|clients?|invoices?", "Freelance"),
            new CategoryRule("dividends?|interest|stocks?|mutual funds?|investments?|sip", "Investments"),
            new CategoryRule("refunds?|cashback|reimburse(ment)?", "Refunds")
    );

    // Words that describe the entry but are not part of the description
    private static final Set<String> FILLER = Set.of(
            "spent", "spend", "paid", "pay", "bought", "buy", "purchased", "on", "for", "at", "using", "by", "via",
            "through", "with", "i", "a", "an", "the", "to", "from", "got", "received", "of", "in", "rs", "inr",
            "expense", "income", "add", "record", "log", "my", "me"
    );

    public ParsedTransactionResponse parse(String rawText, List<CategoryResponse> categories, LocalDate today) {
        String text = rawText == null ? "" : rawText.trim();

        if (text.isEmpty() || QUESTION_CUE.matcher(text).find()) {
            return ParsedTransactionResponse.builder()
                    .entry(false)
                    .type(TransactionType.EXPENSE)
                    .paymentMethod(PaymentMethod.CASH)
                    .transactionDate(today)
                    .missingFields(List.of())
                    .assumptions(List.of())
                    .build();
        }

        List<String> assumptions = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        String working = text;

        // 1. Date. Removed from the text first so its numbers are not read as the amount.
        Optional<LocalDate> date = Optional.empty();
        Matcher m;
        if ((m = ISO_DATE.matcher(working)).find()) {
            date = buildDate(parseInt(m.group(1)), parseInt(m.group(2)), parseInt(m.group(3)));
            working = removeMatch(working, m);
        } else if ((m = DAYS_AGO.matcher(working)).find()) {
            date = Optional.of(today.minusDays(parseInt(m.group(1))));
            working = removeMatch(working, m);
        } else if ((m = YESTERDAY.matcher(working)).find()) {
            date = Optional.of(today.minusDays(1));
            working = removeMatch(working, m);
        } else if ((m = TODAY.matcher(working)).find()) {
            date = Optional.of(today);
            working = removeMatch(working, m);
        } else if ((m = DAY_MONTH.matcher(working)).find()) {
            date = buildDate(m.group(3) != null ? parseYear(m.group(3)) : today.getYear(), monthIndex(m.group(2)), parseInt(m.group(1)));
            working = removeMatch(working, m);
        } else if ((m = MONTH_DAY.matcher(working)).find()) {
            date = buildDate(m.group(3) != null ? parseYear(m.group(3)) : today.getYear(), monthIndex(m.group(1)), parseInt(m.group(2)));
            working = removeMatch(working, m);
        } else if ((m = DMY_DATE.matcher(working)).find()) {
            date = buildDate(m.group(3) != null ? parseYear(m.group(3)) : today.getYear(), parseInt(m.group(2)), parseInt(m.group(1)));
            working = removeMatch(working, m);
        }
        LocalDate transactionDate = date.orElse(today);
        if (date.isEmpty()) {
            assumptions.add("Date set to today");
        }

        // 2. Amount
        BigDecimal amount = null;
        if ((m = AMOUNT.matcher(working)).find()) {
            amount = parseAmount(m.group(1), m.group(2));
            working = removeMatch(working, m);
        }
        if (amount == null) {
            missingFields.add("amount");
        }

        // 3. Type
        TransactionType type = INCOME_CUE.matcher(working).find() ? TransactionType.INCOME : TransactionType.EXPENSE;
        CategoryType categoryType = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;

        // 4. Payment method
        PaymentMethod paymentMethod = PaymentMethod.CASH;
        boolean paymentFound = false;
        for (Map.Entry<Pattern, PaymentMethod> rule : PAYMENT_RULES) {
            if ((m = rule.getKey().matcher(working)).find()) {
                paymentMethod = rule.getValue();
                paymentFound = true;
                working = removeMatch(working, m);
                break;
            }
        }
        if (!paymentFound) {
            assumptions.add("Payment method set to Cash");
        }

        // 5. Category
        CategoryResponse category = findCategory(working, categories, categoryType);
        String categoryName = category != null ? category.getName() : null;
        if (category == null) {
            assumptions.add("No matching category, so it is set to Other " + (type == TransactionType.INCOME ? "Income" : "Expense"));
        }

        // 6. Description: what is left once the amount, date, payment and filler words are removed
        String description = cleanDescription(working);
        if (description.isEmpty()) {
            description = categoryName != null ? categoryName : (type == TransactionType.INCOME ? "Income" : "Expense");
            assumptions.add("No description given, so it is set to " + description);
        }
        description = capitalise(description);
        if (description.length() > 255) {
            description = description.substring(0, 255);
        }

        return ParsedTransactionResponse.builder()
                .entry(amount != null)
                .type(type)
                .amount(amount)
                .description(description)
                .categoryId(category != null ? category.getId() : null)
                .categoryName(categoryName)
                .paymentMethod(paymentMethod)
                .transactionDate(transactionDate)
                .missingFields(missingFields)
                .assumptions(assumptions)
                .build();
    }

    // A user's own category whose name appears in the sentence wins over keyword rules
    private CategoryResponse findCategory(String text, List<CategoryResponse> categories, CategoryType type) {
        String lower = text.toLowerCase(Locale.ROOT);

        for (CategoryResponse c : categories) {
            if (Boolean.TRUE.equals(c.getIsSystem()) || c.getType() != type || c.getName() == null) continue;
            if (Pattern.compile("\\b" + Pattern.quote(c.getName().toLowerCase(Locale.ROOT)) + "\\b").matcher(lower).find()) {
                return c;
            }
        }

        List<CategoryRule> rules = type == CategoryType.INCOME ? INCOME_RULES : EXPENSE_RULES;
        for (CategoryRule rule : rules) {
            if (rule.pattern().matcher(lower).find()) {
                CategoryResponse match = byName(categories, rule.categoryName(), type);
                if (match != null) return match;
            }
        }

        return byName(categories, type == CategoryType.INCOME ? "Other Income" : "Other Expense", type);
    }

    private static CategoryResponse byName(List<CategoryResponse> categories, String name, CategoryType type) {
        return categories.stream()
                .filter(c -> c.getType() == type && name.equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElse(null);
    }

    private static String cleanDescription(String text) {
        return Arrays.stream(text.split("[\\s,]+"))
                .map(t -> t.replaceAll("^[^\\p{L}\\p{N}&]+|[^\\p{L}\\p{N}&]+$", ""))
                .filter(t -> !t.isEmpty())
                .filter(t -> !FILLER.contains(t.toLowerCase(Locale.ROOT)))
                .collect(joining(" "));
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static BigDecimal parseAmount(String number, String suffix) {
        BigDecimal value = new BigDecimal(number.replace(",", ""));
        if (suffix != null) {
            String s = suffix.toLowerCase(Locale.ROOT);
            value = value.multiply(BigDecimal.valueOf(s.startsWith("k") || s.equals("thousand") ? 1_000 : 100_000));
        }
        if (value.signum() <= 0) return null;
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String removeMatch(String s, Matcher m) {
        return s.substring(0, m.start()) + " " + s.substring(m.end());
    }

    private static int parseInt(String s) {
        return Integer.parseInt(s);
    }

    private static int parseYear(String s) {
        int y = Integer.parseInt(s);
        return s.length() == 2 ? 2000 + y : y;
    }

    private static int monthIndex(String prefix) {
        return MONTHS.indexOf(prefix.substring(0, 3).toLowerCase(Locale.ROOT)) + 1;
    }

    private static Optional<LocalDate> buildDate(int year, int month, int day) {
        try {
            return Optional.of(LocalDate.of(year, month, day));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
