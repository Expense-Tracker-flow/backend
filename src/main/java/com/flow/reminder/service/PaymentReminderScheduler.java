package com.flow.reminder.service;

import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReminderScheduler {

    // Local hour (24h) when payment alerts go out: 9 am
    private static final int REMINDER_HOUR = 9;

    private final PaymentReminderService paymentReminderService;
    private final UserRepository userRepository;

    // Every hour, on the hour. Each user's own local time decides whether their alerts are due.
    @Scheduled(cron = "0 0 * * * *")
    public void sendDueReminders() {
        Instant now = Instant.now();
        for (User user : userRepository.findAll()) {
            try {
                ZonedDateTime local = now.atZone(PaymentReminderService.zoneOf(user.getTimezone()));
                if (local.getHour() == REMINDER_HOUR) {
                    paymentReminderService.sendDueReminders(user.getId(), local.toLocalDate());
                }
            } catch (Exception e) {
                log.warn("Payment reminder run failed for one user: {}", e.getMessage());
            }
        }
    }
}
