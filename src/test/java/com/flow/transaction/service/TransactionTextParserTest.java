package com.flow.transaction.service;

import com.flow.category.dto.CategoryResponse;
import com.flow.category.entity.CategoryType;
import com.flow.transaction.dto.ParsedTransactionResponse;
import com.flow.transaction.entity.PaymentMethod;
import com.flow.transaction.entity.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    private final TransactionTextParser parser = new TransactionTextParser();
    private List<CategoryResponse> categories;

    @BeforeEach
    void setUp() {
        categories = new ArrayList<>();
        addSystem("Food & Dining", CategoryType.EXPENSE);
        addSystem("Groceries", CategoryType.EXPENSE);
        addSystem("Transportation", CategoryType.EXPENSE);
        addSystem("Bills & Utilities", CategoryType.EXPENSE);
        addSystem("Other Expense", CategoryType.EXPENSE);
        addSystem("Salary", CategoryType.INCOME);
        addSystem("Other Income", CategoryType.INCOME);
    }

    private void addSystem(String name, CategoryType type) {
        categories.add(CategoryResponse.builder().id(UUID.randomUUID()).name(name).type(type).isSystem(true).build());
    }

    private ParsedTransactionResponse parse(String text) {
        return parser.parse(text, categories, TODAY);
    }

    @Test
    void expenseWithPaymentAndCategory() {
        ParsedTransactionResponse r = parse("spent 250 on groceries by UPI");

        assertThat(r.isEntry()).isTrue();
        assertThat(r.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(r.getCategoryName()).isEqualTo("Groceries");
        assertThat(r.getPaymentMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(r.getTransactionDate()).isEqualTo(TODAY);
        assertThat(r.getDescription()).isEqualTo("Groceries");
    }

    @Test
    void incomeYesterdayViaBankTransfer() {
        ParsedTransactionResponse r = parse("received 50000 salary via bank transfer yesterday");

        assertThat(r.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(r.getCategoryName()).isEqualTo("Salary");
        assertThat(r.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(r.getTransactionDate()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void amountWithKSuffixAndExplicitDate() {
        ParsedTransactionResponse r = parse("paid 2.5k for rent on 5 oct");

        assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(r.getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(r.getCategoryName()).isEqualTo("Bills & Utilities");
    }

    @Test
    void dateIsNotReadAsAmount() {
        ParsedTransactionResponse r = parse("spent 180 on taxi 3 days ago");

        assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("180.00"));
        assertThat(r.getTransactionDate()).isEqualTo(TODAY.minusDays(3));
        assertThat(r.getCategoryName()).isEqualTo("Transportation");
    }

    @Test
    void thousandsSeparatorsAreParsed() {
        ParsedTransactionResponse r = parse("dinner with friends 1,200");

        assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(r.getCategoryName()).isEqualTo("Food & Dining");
        assertThat(r.getDescription()).isEqualTo("Dinner friends");
    }

    @Test
    void explicitCashPayment() {
        ParsedTransactionResponse r = parse("450 for food by cash");

        assertThat(r.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(r.getAssumptions()).doesNotContain("Payment method set to Cash");
    }

    @Test
    void missingPaymentIsAssumedAndShown() {
        ParsedTransactionResponse r = parse("spent 100 on coffee");

        assertThat(r.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(r.getAssumptions()).contains("Payment method set to Cash");
    }

    @Test
    void customCategoryWinsOverKeywords() {
        CategoryResponse pets = CategoryResponse.builder().id(UUID.randomUUID()).name("Pets").type(CategoryType.EXPENSE).isSystem(false).build();
        categories.add(pets);

        ParsedTransactionResponse r = parse("spent 300 on pets food");

        assertThat(r.getCategoryName()).isEqualTo("Pets");
        assertThat(r.getCategoryId()).isEqualTo(pets.getId());
    }

    @Test
    void missingAmountIsReportedNotSaved() {
        ParsedTransactionResponse r = parse("groceries by upi");

        assertThat(r.isEntry()).isFalse();
        assertThat(r.getMissingFields()).containsExactly("amount");
        assertThat(r.getAmount()).isNull();
    }

    @Test
    void questionsAreNotEntries() {
        assertThat(parse("how much did I spend last week").isEntry()).isFalse();
        assertThat(parse("show my recent transactions").isEntry()).isFalse();
        assertThat(parse("total spent this month?").isEntry()).isFalse();
    }

    @Test
    void rangeQueryIsNotAnAmount() {
        assertThat(parse("last 7 days").isEntry()).isFalse();
    }

    @Test
    void validEntryWithTimeWordStillCounts() {
        ParsedTransactionResponse r = parse("spent 200 on taxi last week");

        assertThat(r.isEntry()).isTrue();
        assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
