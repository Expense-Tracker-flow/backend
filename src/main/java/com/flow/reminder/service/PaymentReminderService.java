package com.flow.reminder.service;

import com.flow.common.exception.ErrorCode;
import com.flow.common.exception.ResourceNotFoundException;
import com.flow.notification.entity.Notification;
import com.flow.notification.repository.NotificationRepository;
import com.flow.push.service.PushSender;
import com.flow.reminder.dto.PaymentReminderDto;
import com.flow.reminder.entity.PaymentReminder;
import com.flow.reminder.repository.PaymentReminderRepository;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentReminderService {

    private final PaymentReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PushSender pushSender;

    @Transactional(readOnly = true)
    public List<PaymentReminderDto.Response> list(UUID userId) {
        return reminderRepository.findByUserIdOrderByNextDueDateAsc(userId).stream()
                .map(PaymentReminderDto.Response::from)
                .toList();
    }

    @Transactional
    public PaymentReminderDto.Response create(UUID userId, PaymentReminderDto.Request request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        PaymentReminder reminder = PaymentReminder.builder().user(user).build();
        apply(reminder, request, today(user));
        return PaymentReminderDto.Response.from(reminderRepository.save(reminder));
    }

    @Transactional
    public PaymentReminderDto.Response update(UUID id, UUID userId, PaymentReminderDto.Request request) {
        PaymentReminder reminder = findOwned(id, userId);
        apply(reminder, request, today(reminder.getUser()));
        return PaymentReminderDto.Response.from(reminder);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        reminderRepository.delete(findOwned(id, userId));
    }

    /** Called after the user logs the payment as an expense: the next due date moves to the following month. */
    @Transactional
    public PaymentReminderDto.Response markLogged(UUID id, UUID userId) {
        PaymentReminder reminder = findOwned(id, userId);
        YearMonth nextMonth = YearMonth.from(reminder.getNextDueDate()).plusMonths(1);
        reminder.setNextDueDate(onDay(nextMonth, reminder.getDueDay()));
        return PaymentReminderDto.Response.from(reminder);
    }

    /**
     * Sends the alerts that are due for one user's local date. Each reminder is sent once per due date,
     * so a payment that stays unlogged does not ping again on every run.
     */
    @Transactional
    public void sendDueReminders(UUID userId, LocalDate today) {
        for (PaymentReminder reminder : reminderRepository.findByUserIdAndActiveTrue(userId)) {
            LocalDate remindFrom = reminder.getNextDueDate().minusDays(reminder.getRemindDaysBefore());
            if (today.isBefore(remindFrom) || today.isAfter(reminder.getNextDueDate())) {
                continue;
            }

            String dedupeKey = "PAYMENT_DUE:" + reminder.getId() + ":" + reminder.getNextDueDate();
            if (notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
                continue;
            }

            String title = "Payment due " + dueLabel(reminder.getNextDueDate(), today);
            String message = reminder.getTitle() + " of " + reminder.getAmount().stripTrailingZeros().toPlainString()
                    + " is due on " + reminder.getNextDueDate() + ".";
            notificationRepository.save(Notification.builder()
                    .user(reminder.getUser())
                    .type("PAYMENT_DUE")
                    .title(title)
                    .message(message)
                    .dedupeKey(dedupeKey)
                    .build());
            pushSender.sendReminderToUser(userId, title, message, reminder.getId());
        }
    }

    /** Local time zone of a user, falling back to UTC when it is missing or unknown. */
    public static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private void apply(PaymentReminder reminder, PaymentReminderDto.Request request, LocalDate today) {
        reminder.setTitle(request.getTitle().trim());
        reminder.setAmount(request.getAmount());
        reminder.setCategoryId(request.getCategoryId());
        reminder.setPaymentMethod(request.getPaymentMethod());
        reminder.setDueDay(request.getDueDay());
        reminder.setRemindDaysBefore(request.getRemindDaysBefore());
        reminder.setNextDueDate(nextDueDate(request.getDueDay(), today));
    }

    private PaymentReminder findOwned(UUID id, UUID userId) {
        return reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Payment reminder not found"));
    }

    private static LocalDate today(User user) {
        return LocalDate.now(zoneOf(user.getTimezone()));
    }

    /** The first due date on or after today. Days past the end of a short month fall on its last day. */
    static LocalDate nextDueDate(int dueDay, LocalDate today) {
        YearMonth thisMonth = YearMonth.from(today);
        LocalDate candidate = onDay(thisMonth, dueDay);
        return candidate.isBefore(today) ? onDay(thisMonth.plusMonths(1), dueDay) : candidate;
    }

    private static LocalDate onDay(YearMonth month, int dueDay) {
        return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
    }

    private static String dueLabel(LocalDate dueDate, LocalDate today) {
        long days = today.until(dueDate).getDays();
        if (days <= 0) return "today";
        if (days == 1) return "tomorrow";
        return "in " + days + " days";
    }
}
