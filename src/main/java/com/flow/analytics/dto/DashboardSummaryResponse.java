package com.flow.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {
    private BigDecimal totalBalance;
    private BigDecimal monthIncome;
    private BigDecimal monthExpense;
    private BigDecimal monthNetSavings;
    private Double monthOverMonthGrowth;
    private List<CategoryBreakdownDto> topCategories;
    private List<DailyTrendDto> dailyPulse;
}
