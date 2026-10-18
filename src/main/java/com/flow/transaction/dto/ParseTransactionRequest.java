package com.flow.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseTransactionRequest {

    @NotBlank(message = "Text is required")
    @Size(max = 500, message = "Text cannot exceed 500 characters")
    private String text;

    // The user's local date, so "today" and "yesterday" are right near midnight. Falls back to server date.
    private LocalDate today;
}
