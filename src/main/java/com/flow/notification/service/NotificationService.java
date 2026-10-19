package com.flow.notification.service;

import com.flow.common.exception.ErrorCode;
import com.flow.common.exception.ResourceNotFoundException;
import com.flow.notification.dto.NotificationResponse;
import com.flow.notification.entity.Notification;
import com.flow.notification.repository.NotificationRepository;
import com.flow.push.service.PushSender;
import com.flow.transaction.entity.TransactionType;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final BigDecimal SPIKE_THRESHOLD = new BigDecimal("1.2");

    // Local hour (24h) when the "nothing logged today" reminder is sent: 8 pm
    private static final int REMINDER_HOUR = 20;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PushSender pushSender;

    /**
     * Runs the alert checks for "today" and returns the latest alerts. The checks run when the app asks,
     * so no background job is needed and each user's own date is used.
     */
    @Transactional
    public NotificationResponse.Page list(UUID userId, LocalDate today) {
        // Alerts are created only by the daily job at the reminder hour, never when the app is opened
        List<NotificationResponse> items = notificationRepository.findTop30ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();

        return NotificationResponse.Page.builder()
                .unreadCount(notificationRepository.countByUserIdAndReadAtIsNull(userId))
                .items(items)
                .build();
    }

    @Transactional
    public void markRead(UUID id, UUID userId) {
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    /** Alert checks for one user's local date. Run once a day at the reminder hour. */
    void runChecks(UUID userId, LocalDate today) {
        // 1. Nothing logged today, for users who already use the app
        checkNoEntry(userId, today);

        // 2. Spending in the last 7 days is well above the 7 days before
        LocalDate weekStart = today.minusDays(6);
        BigDecimal thisWeek = notificationRepository.sumBetween(userId, TransactionType.EXPENSE, weekStart, today);
        BigDecimal lastWeek = notificationRepository.sumBetween(userId, TransactionType.EXPENSE,
                today.minusDays(13), today.minusDays(7));
        if (lastWeek.signum() > 0 && thisWeek.compareTo(lastWeek.multiply(SPIKE_THRESHOLD)) > 0) {
            long percent = thisWeek.subtract(lastWeek)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(lastWeek, 0, RoundingMode.HALF_UP)
                    .longValue();
            create(userId, "SPEND_UP", "Spending is up",
                    "You spent " + percent + "% more in the last 7 days than in the 7 days before.",
                    "SPEND_UP:" + weekStart);
        }
    }

    private void checkNoEntry(UUID userId, LocalDate today) {
        if (notificationRepository.countTransactionsOn(userId, today) == 0
                && notificationRepository.countAllTransactions(userId) > 0) {
            create(userId, "NO_ENTRY", "No entries yet today",
                    "Log today's spending or income so your totals stay accurate.",
                    "NO_ENTRY:" + today);
        }
    }

    /**
     * Called every hour by the scheduler. For each user whose local time is the reminder hour,
     * checks whether they have logged anything today and sends the reminder if not.
     */
    @Transactional
    public void sendDailyReminders(Instant now) {
        for (User user : userRepository.findAll()) {
            ZonedDateTime local = now.atZone(zoneOf(user.getTimezone()));
            if (local.getHour() == REMINDER_HOUR) {
                runChecks(user.getId(), local.toLocalDate());
            }
        }
    }

    private static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    // Other alert types (budgets, bills due) can call this once those features exist
    void create(UUID userId, String type, String title, String message, String dedupeKey) {
        if (dedupeKey != null && notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        notificationRepository.save(Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .dedupeKey(dedupeKey)
                .build());
        log.debug("Created {} notification for user {}", type, userId);

        // Also send to the user's phones (no-op when phone notifications are off)
        pushSender.sendToUser(userId, title, message);
    }
}
