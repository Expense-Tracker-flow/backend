package com.flow.notification.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;

    // Every hour, on the hour. Each user's own local time decides whether their reminder is due.
    @Scheduled(cron = "0 0 * * * *")
    public void sendDailyReminders() {
        try {
            notificationService.sendDailyReminders(Instant.now());
        } catch (Exception e) {
            log.warn("Daily reminder run failed: {}", e.getMessage());
        }
    }
}
