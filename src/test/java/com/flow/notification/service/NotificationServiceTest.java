package com.flow.notification.service;

import com.flow.notification.dto.NotificationResponse;
import com.flow.notification.entity.Notification;
import com.flow.notification.repository.NotificationRepository;
import com.flow.transaction.entity.TransactionType;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.flow.push.service.PushSender pushSender;

    @InjectMocks
    private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 10, 18);

    private void stubNoAlertsBaseline() {
        lenient().when(notificationRepository.sumBetween(eq(userId), eq(TransactionType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void userWithHistoryButNothingTodayGetsNoEntryAlert() {
        stubNoAlertsBaseline();
        when(notificationRepository.countTransactionsOn(userId, today)).thenReturn(0L);
        when(notificationRepository.countAllTransactions(userId)).thenReturn(10L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        notificationService.runChecks(userId, today);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("NO_ENTRY");
        assertThat(captor.getValue().getDedupeKey()).isEqualTo("NO_ENTRY:" + today);
    }

    @Test
    void dailyReminderSentAtEightPmLocalTime() {
        stubNoAlertsBaseline();
        // 20:05 UTC on 18 Oct is 20:05 in UTC: the reminder hour for this user
        User user = User.builder().email("a@b.com").passwordHash("x").fullName("Test").currency("INR")
                .timezone("UTC").build();
        user.setId(userId);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(notificationRepository.countTransactionsOn(any(), eq(today))).thenReturn(0L);
        when(notificationRepository.countAllTransactions(any())).thenReturn(10L);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));

        notificationService.sendDailyReminders(java.time.Instant.parse("2026-10-18T20:05:00Z"));

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void noDailyReminderOutsideReminderHour() {
        stubNoAlertsBaseline();
        User user = User.builder().email("a@b.com").passwordHash("x").fullName("Test").currency("INR")
                .timezone("UTC").build();
        when(userRepository.findAll()).thenReturn(List.of(user));

        notificationService.sendDailyReminders(java.time.Instant.parse("2026-10-18T12:05:00Z"));

        verify(notificationRepository, never()).countTransactionsOn(any(), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void newUserWithNoHistoryGetsNoAlert() {
        stubNoAlertsBaseline();
        when(notificationRepository.countTransactionsOn(userId, today)).thenReturn(0L);
        when(notificationRepository.countAllTransactions(userId)).thenReturn(0L);

        notificationService.runChecks(userId, today);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void spendingUpMoreThanTwentyPercentGetsAlert() {
        stubNoAlertsBaseline();
        when(notificationRepository.countTransactionsOn(userId, today)).thenReturn(1L);
        when(notificationRepository.sumBetween(userId, TransactionType.EXPENSE, today.minusDays(6), today))
                .thenReturn(new BigDecimal("1500"));
        when(notificationRepository.sumBetween(userId, TransactionType.EXPENSE, today.minusDays(13), today.minusDays(7)))
                .thenReturn(new BigDecimal("1000"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        notificationService.runChecks(userId, today);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("SPEND_UP");
        assertThat(captor.getValue().getMessage()).contains("50%");
    }

    @Test
    void alreadyStoredAlertIsNotSavedAgain() {
        stubNoAlertsBaseline();
        when(notificationRepository.countTransactionsOn(userId, today)).thenReturn(0L);
        when(notificationRepository.countAllTransactions(userId)).thenReturn(10L);
        when(notificationRepository.existsByUserIdAndDedupeKey(userId, "NO_ENTRY:" + today)).thenReturn(true);

        notificationService.runChecks(userId, today);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void listReturnsUnreadCountAndNeverCreatesAlerts() {
        when(notificationRepository.findTop30ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(notificationRepository.countByUserIdAndReadAtIsNull(userId)).thenReturn(3L);

        NotificationResponse.Page page = notificationService.list(userId, today);

        assertThat(page.getUnreadCount()).isEqualTo(3L);
        assertThat(page.getItems()).isEmpty();
        // Opening the app must not create alerts: that only happens at the reminder hour
        verify(notificationRepository, never()).save(any());
    }
}
