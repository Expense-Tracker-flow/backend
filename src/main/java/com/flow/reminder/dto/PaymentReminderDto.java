package com.flow.reminder.dto;

import com.flow.reminder.entity.PaymentReminder;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class PaymentReminderDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {

        @NotBlank(message = "Reminder name is required")
        @Size(max = 120, message = "Reminder name must be at most 120 characters")
        private String title;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        private BigDecimal amount;

        private UUID categoryId;

        @Size(max = 50)
        private String paymentMethod;

        @NotNull(message = "Due day is required")
        @Min(value = 1, message = "Due day must be between 1 and 31")
        @Max(value = 31, message = "Due day must be between 1 and 31")
        private Integer dueDay;

        @NotNull(message = "Reminder timing is required")
        @Min(value = 0, message = "Remind days before must be between 0 and 7")
        @Max(value = 7, message = "Remind days before must be between 0 and 7")
        private Integer remindDaysBefore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {

        private UUID id;
        private String title;
        private BigDecimal amount;
        private UUID categoryId;
        private String paymentMethod;
        private int dueDay;
        private int remindDaysBefore;
        private LocalDate nextDueDate;
        private boolean active;

        public static Response from(PaymentReminder reminder) {
            return Response.builder()
                    .id(reminder.getId())
                    .title(reminder.getTitle())
                    .amount(reminder.getAmount())
                    .categoryId(reminder.getCategoryId())
                    .paymentMethod(reminder.getPaymentMethod())
                    .dueDay(reminder.getDueDay())
                    .remindDaysBefore(reminder.getRemindDaysBefore())
                    .nextDueDate(reminder.getNextDueDate())
                    .active(reminder.isActive())
                    .build();
        }
    }
}
