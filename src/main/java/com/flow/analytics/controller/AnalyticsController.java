package com.flow.analytics.controller;

import com.flow.analytics.dto.DashboardSummaryResponse;
import com.flow.analytics.service.AnalyticsService;
import com.flow.auth.jwt.JwtService;
import com.flow.auth.security.UserPrincipal;
import com.flow.common.exception.UnauthorizedException;
import com.flow.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Financial analytics & dashboard statistics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final JwtService jwtService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get comprehensive dashboard summary (Balance, Month Totals, Category breakdown, Money Pulse)")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = resolveUserId(principal, authHeader);
        DashboardSummaryResponse response = analyticsService.getDashboardSummary(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private UUID resolveUserId(UserPrincipal principal, String authHeader) {
        if (principal != null && principal.getId() != null) {
            return principal.getId();
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                return jwtService.extractUserId(token);
            } catch (Exception ignored) {
                // fall through
            }
        }
        throw new UnauthorizedException("Authentication token required to access analytics");
    }
}
