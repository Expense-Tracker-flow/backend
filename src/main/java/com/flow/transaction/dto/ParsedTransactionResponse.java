package com.flow.transaction.dto;

import com.flow.transaction.entity.PaymentMethod;
import com.flow.transaction.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A draft transaction read from free text. Nothing is saved until the user confirms it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedTransactionResponse {

    // False when the text does not look like an entry (for example a question about spending)
    private boolean entry;

    private TransactionType type;
    private BigDecimal amount;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private PaymentMethod paymentMethod;
    private LocalDate transactionDate;

    // Fields the user must supply before saving, such as "amount"
    private List<String> missingFields;

    // Defaults the parser filled in, shown to the user so they can correct them
    private List<String> assumptions;
}
