package com.flow.transaction.dto;

import com.flow.category.dto.CategoryResponse;
import com.flow.transaction.entity.PaymentMethod;
import com.flow.transaction.entity.Transaction;
import com.flow.transaction.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID id;
    private TransactionType type;
    private BigDecimal amount;
    private String description;
    private CategoryResponse category;
    private LocalDate transactionDate;
    private PaymentMethod paymentMethod;
    private String notes;
    private Instant createdAt;

    public static TransactionResponse from(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .description(tx.getDescription())
                .category(CategoryResponse.from(tx.getCategory()))
                .transactionDate(tx.getTransactionDate())
                .paymentMethod(tx.getPaymentMethod())
                .notes(tx.getNotes())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
