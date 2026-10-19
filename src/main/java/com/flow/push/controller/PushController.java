package com.flow.push.controller;

import com.flow.auth.jwt.JwtService;
import com.flow.auth.security.UserPrincipal;
import com.flow.common.exception.UnauthorizedException;
import com.flow.common.response.ApiResponse;
import com.flow.push.dto.PushSubscribeRequest;
import com.flow.push.service.PushSender;
import com.flow.push.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
@Tag(name = "Push", description = "Phone and browser notifications")
public class PushController {

    private final PushSender pushSender;
    private final PushSubscriptionService subscriptionService;
    private final JwtService jwtService;

    @GetMapping("/public-key")
    @Operation(summary = "Public key the browser needs to subscribe, and whether phone notifications are available")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publicKey() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", pushSender.isEnabled());
        data.put("publicKey", pushSender.isEnabled() ? pushSender.getPublicKey() : null);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/subscribe")
    @Operation(summary = "Turn on phone notifications for this browser")
    public ResponseEntity<ApiResponse<Void>> subscribe(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody PushSubscribeRequest request
    ) {
        subscriptionService.subscribe(resolveUserId(principal, authHeader), request);
        return ResponseEntity.ok(ApiResponse.ok("Phone notifications turned on", null));
    }

    @PostMapping("/unsubscribe")
    @Operation(summary = "Turn off phone notifications for this browser")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody UnsubscribeRequest request
    ) {
        subscriptionService.unsubscribe(resolveUserId(principal, authHeader), request.getEndpoint());
        return ResponseEntity.ok(ApiResponse.ok("Phone notifications turned off", null));
    }

    @Data
    public static class UnsubscribeRequest {
        @NotBlank
        private String endpoint;
    }

    private UUID resolveUserId(UserPrincipal principal, String authHeader) {
        if (principal != null && principal.getId() != null) {
            return principal.getId();
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                return jwtService.extractUserId(authHeader.substring(7));
            } catch (Exception ignored) {
                // fall through
            }
        }
        throw new UnauthorizedException("Authentication token required for notifications");
    }
}
