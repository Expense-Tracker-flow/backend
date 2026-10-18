package com.flow.transaction.entity;

import java.util.Arrays;

public enum PaymentMethod {
    UPI,
    CASH,
    CREDIT_CARD,
    DEBIT_CARD,
    BANK_TRANSFER,
    OTHER;

    public static boolean isBuiltIn(String code) {
        return code != null && Arrays.stream(values()).anyMatch(v -> v.name().equals(code));
    }
}
