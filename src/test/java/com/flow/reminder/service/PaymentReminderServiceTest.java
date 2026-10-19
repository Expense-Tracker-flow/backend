package com.flow.reminder.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReminderServiceTest {

    @Test
    void dueLaterThisMonthStaysInThisMonth() {
        assertThat(PaymentReminderService.nextDueDate(20, LocalDate.of(2026, 10, 19)))
                .isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void dueTodayStaysToday() {
        assertThat(PaymentReminderService.nextDueDate(19, LocalDate.of(2026, 10, 19)))
                .isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void dayAlreadyPassedMovesToNextMonth() {
        assertThat(PaymentReminderService.nextDueDate(5, LocalDate.of(2026, 10, 19)))
                .isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void dayThirtyOneInShortMonthFallsOnLastDay() {
        // February 2027 has 28 days
        assertThat(PaymentReminderService.nextDueDate(31, LocalDate.of(2027, 2, 10)))
                .isEqualTo(LocalDate.of(2027, 2, 28));
    }
}
