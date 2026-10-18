package com.flow.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTypeResponse {

    // What a transaction stores: a built-in code, or the id of a custom type
    private String code;
    private String label;
    private boolean builtIn;
}
