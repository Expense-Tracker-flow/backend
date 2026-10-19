package com.flow.notification.controller;

import com.flow.auth.jwt.JwtService;
import com.flow.auth.security.UserPrincipal;
import com.flow.common.exception.UnauthorizedException;
import com.flow.common.response.ApiResponse;
import com.flow.notification.dto.NotificationResponse;
import com.flow.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app alerts shown from the home screen bell")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtService jwtService;

    @GetMapping
    @Operation(summary = "List recent alerts and the unread count. Checks run for the given local date")
    public ResponseEntity<ApiResponse<NotificationResponse.Page>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today
    ) {
        LocalDate date = today != null ? today : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(notificationService.list(resolveUserId(principal, authHeader), date)));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one alert as read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id
    ) {
        notificationService.markRead(id, resolveUserId(principal, authHeader));
        return ResponseEntity.ok(ApiResponse.ok("Marked as read", null));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all alerts as read")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        notificationService.markAllRead(resolveUserId(principal, authHeader));
        return ResponseEntity.ok(ApiResponse.ok("All alerts marked as read", null));
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
        throw new UnauthorizedException("Authentication token required to access notifications");
    }
}
