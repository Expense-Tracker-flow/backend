package com.flow.push.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.push.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PushSenderTest {

    @Test
    void disabledWithoutKeysAndSendsNothing() {
        PushSubscriptionRepository repo = mock(PushSubscriptionRepository.class);
        PushSender sender = new PushSender(repo, new ObjectMapper(), "", "", "mailto:test@example.com");
        sender.init();

        assertThat(sender.isEnabled()).isFalse();

        sender.sendToUser(UUID.randomUUID(), "Title", "Body");

        verifyNoInteractions(repo);
    }
}
