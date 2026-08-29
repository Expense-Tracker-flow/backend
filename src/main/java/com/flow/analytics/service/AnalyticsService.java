package com.flow.analytics.service;

import com.flow.analytics.dto.CategoryBreakdownDto;
import com.flow.analytics.dto.DailyTrendDto;
import com.flow.analytics.dto.DashboardSummaryResponse;
import com.flow.transaction.entity.TransactionType;
import com.flow.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(UUID userId) {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();

        YearMonth prevMonth = currentMonth.minusMonths(1);
        LocalDate startOfPrevMonth = prevMonth.atDay(1);
        LocalDate endOfPrevMonth = prevMonth.atEndOfMonth();

        // 1. Overall Balance
        BigDecimal totalIncome = transactionRepository.sumTotalByUserIdAndType(userId, TransactionType.INCOME);
        BigDecimal totalExpense = transactionRepository.sumTotalByUserIdAndType(userId, TransactionType.EXPENSE);
        BigDecimal totalBalance = totalIncome.subtract(totalExpense);

        // 2. Month Income & Expense
        BigDecimal monthIncome = transactionRepository.sumByUserIdAndTypeAndDateRange(
                userId, TransactionType.INCOME, startOfMonth, endOfMonth
        );
        BigDecimal monthExpense = transactionRepository.sumByUserIdAndTypeAndDateRange(
                userId, TransactionType.EXPENSE, startOfMonth, endOfMonth
        );
        BigDecimal monthNetSavings = monthIncome.subtract(monthExpense);

        // 3. Month over Month Growth
        BigDecimal prevMonthExpense = transactionRepository.sumByUserIdAndTypeAndDateRange(
                userId, TransactionType.EXPENSE, startOfPrevMonth, endOfPrevMonth
        );
        Double monthOverMonthGrowth = 0.0;
        if (prevMonthExpense.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = monthExpense.subtract(prevMonthExpense);
            monthOverMonthGrowth = diff.divide(prevMonthExpense, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // 4. Category Breakdown
        List<Object[]> rawCategories = transactionRepository.findCategorySpendingBreakdown(userId, startOfMonth, endOfMonth);
        List<CategoryBreakdownDto> topCategories = new ArrayList<>();
        for (Object[] row : rawCategories) {
            String name = (String) row[0];
            String color = (String) row[1];
            String icon = (String) row[2];
            BigDecimal amount = (BigDecimal) row[3];
            Long count = ((Number) row[4]).longValue();

            Double pct = 0.0;
            if (monthExpense.compareTo(BigDecimal.ZERO) > 0) {
                pct = amount.divide(monthExpense, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            topCategories.add(CategoryBreakdownDto.builder()
                    .name(name)
                    .color(color)
                    .icon(icon)
                    .amount(amount)
                    .transactionCount(count)
                    .percentage(pct)
                    .build());
        }

        // 5. Daily Trend (Money Pulse)
        List<Object[]> rawDaily = transactionRepository.findDailyTrends(userId, startOfMonth, endOfMonth);
        List<DailyTrendDto> dailyPulse = new ArrayList<>();
        for (Object[] row : rawDaily) {
            LocalDate date = (LocalDate) row[0];
            BigDecimal expense = (BigDecimal) row[1];
            BigDecimal income = (BigDecimal) row[2];
            dailyPulse.add(DailyTrendDto.builder()
                    .date(date)
                    .expense(expense)
                    .income(income)
                    .build());
        }

        return DashboardSummaryResponse.builder()
                .totalBalance(totalBalance)
                .monthIncome(monthIncome)
                .monthExpense(monthExpense)
                .monthNetSavings(monthNetSavings)
                .monthOverMonthGrowth(monthOverMonthGrowth)
                .topCategories(topCategories)
                .dailyPulse(dailyPulse)
                .build();
    }
}
