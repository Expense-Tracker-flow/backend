package com.flow.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBreakdownDto {
    private String name;
    private String color;
    private String icon;
    private BigDecimal amount;
    private Long transactionCount;
    private Double percentage;
}
