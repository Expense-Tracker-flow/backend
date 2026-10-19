package com.flow.push.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.push.entity.PushSubscription;
import com.flow.push.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends alerts to a user's phones and browsers using the Web Push protocol.
 * Disabled (silently) when the VAPID keys are not configured.
 */
@Slf4j
@Service
public class PushSender {

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    private PushService pushService;

    public PushSender(PushSubscriptionRepository subscriptionRepository,
                      ObjectMapper objectMapper,
                      @Value("${flow.push.vapid-public-key:}") String publicKey,
                      @Value("${flow.push.vapid-private-key:}") String privateKey,
                      @Value("${flow.push.vapid-subject:mailto:uksayoojjayarajan@gmail.com}") String subject) {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    @PostConstruct
    void init() {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.warn("Phone notifications are disabled: VAPID keys are not set");
            return;
        }
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushService(publicKey, privateKey, subject);
            log.info("Phone notifications are enabled");
        } catch (Exception e) {
            log.error("Phone notifications are disabled: could not load VAPID keys ({})", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    public String getPublicKey() {
        return publicKey;
    }

    /**
     * Sends one alert to every device the user has subscribed. Runs in the background so the
     * request that created the alert does not wait for the push services.
     */
    @Async
    @Transactional
    public void sendToUser(UUID userId, String title, String body) {
        send(userId, Map.of("title", title, "body", body, "url", "/"));
    }

    /**
     * Sends a payment reminder. The alert has an "Add expense" button that opens the app and logs the payment.
     * Browsers without notification buttons (for example iPhone) still open the app from the alert.
     */
    @Async
    @Transactional
    public void sendReminderToUser(UUID userId, String title, String body, UUID reminderId) {
        send(userId, Map.of(
                "title", title,
                "body", body,
                "url", "/",
                "actionUrl", "/?addReminder=" + reminderId,
                "actions", List.of(Map.of("action", "add-expense", "title", "Add expense"))));
    }

    private void send(UUID userId, Map<String, Object> data) {
        if (pushService == null) {
            return;
        }
        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Could not build push payload: {}", e.getMessage());
            return;
        }

        for (PushSubscription subscription : subscriptions) {
            try {
                Notification notification = new Notification(
                        subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), payload);
                HttpResponse response = pushService.send(notification);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    // The browser or phone removed the subscription (uninstalled, or permission revoked)
                    subscriptionRepository.delete(subscription);
                } else if (status >= 400) {
                    log.warn("Push service rejected a notification with status {}", status);
                }
            } catch (Exception e) {
                log.warn("Push to one device failed: {}", e.getMessage());
            }
        }
    }
}
