package com.flow.push.service;

import com.flow.common.exception.ErrorCode;
import com.flow.common.exception.ResourceNotFoundException;
import com.flow.push.dto.PushSubscribeRequest;
import com.flow.push.entity.PushSubscription;
import com.flow.push.repository.PushSubscriptionRepository;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    /**
     * Saves a device for this user. If the same browser subscribes again, its keys and owner are updated.
     */
    @Transactional
    public void subscribe(UUID userId, PushSubscribeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));

        PushSubscription subscription = subscriptionRepository.findByEndpoint(request.getEndpoint())
                .orElseGet(() -> PushSubscription.builder().endpoint(request.getEndpoint()).build());

        subscription.setUser(user);
        subscription.setP256dh(request.getKeys().getP256dh());
        subscription.setAuth(request.getKeys().getAuth());
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        subscriptionRepository.deleteByEndpointAndUserId(endpoint, userId);
    }
}
