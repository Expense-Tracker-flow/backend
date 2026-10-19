package com.flow.reminder.controller;

import com.flow.auth.jwt.JwtService;
import com.flow.auth.security.UserPrincipal;
import com.flow.common.exception.UnauthorizedException;
import com.flow.common.response.ApiResponse;
import com.flow.reminder.dto.PaymentReminderDto;
import com.flow.reminder.service.PaymentReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-reminders")
@RequiredArgsConstructor
@Tag(name = "Payment reminders", description = "Bills that send a phone alert before they are due")
public class PaymentReminderController {

    private final PaymentReminderService reminderService;
    private final JwtService jwtService;

    @GetMapping
    @Operation(summary = "List the user's payment reminders, soonest due first")
    public ResponseEntity<ApiResponse<List<PaymentReminderDto.Response>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reminderService.list(resolveUserId(principal, authHeader))));
    }

    @PostMapping
    @Operation(summary = "Create a payment reminder")
    public ResponseEntity<ApiResponse<PaymentReminderDto.Response>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody PaymentReminderDto.Request request
    ) {
        PaymentReminderDto.Response response = reminderService.create(resolveUserId(principal, authHeader), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Payment reminder created", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a payment reminder")
    public ResponseEntity<ApiResponse<PaymentReminderDto.Response>> update(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody PaymentReminderDto.Request request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reminderService.update(id, resolveUserId(principal, authHeader), request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a payment reminder")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        reminderService.delete(id, resolveUserId(principal, authHeader));
        return ResponseEntity.ok(ApiResponse.ok("Payment reminder deleted", null));
    }

    @PostMapping("/{id}/log")
    @Operation(summary = "Mark the payment as logged as an expense and move its due date to next month")
    public ResponseEntity<ApiResponse<PaymentReminderDto.Response>> markLogged(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reminderService.markLogged(id, resolveUserId(principal, authHeader))));
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
        throw new UnauthorizedException("Authentication token required to access payment reminders");
    }
}
