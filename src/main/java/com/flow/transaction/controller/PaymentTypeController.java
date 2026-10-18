package com.flow.transaction.controller;

import com.flow.auth.jwt.JwtService;
import com.flow.auth.security.UserPrincipal;
import com.flow.common.exception.UnauthorizedException;
import com.flow.common.response.ApiResponse;
import com.flow.transaction.dto.PaymentTypeRequest;
import com.flow.transaction.dto.PaymentTypeResponse;
import com.flow.transaction.service.PaymentTypeService;
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
@RequestMapping("/api/v1/payment-types")
@RequiredArgsConstructor
@Tag(name = "Payment Types", description = "Built-in and user-defined payment types")
public class PaymentTypeController {

    private final PaymentTypeService paymentTypeService;
    private final JwtService jwtService;

    @GetMapping
    @Operation(summary = "List built-in and custom payment types")
    public ResponseEntity<ApiResponse<List<PaymentTypeResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return ResponseEntity.ok(ApiResponse.ok(paymentTypeService.list(resolveUserId(principal, authHeader))));
    }

    @PostMapping
    @Operation(summary = "Create a custom payment type")
    public ResponseEntity<ApiResponse<PaymentTypeResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody PaymentTypeRequest request
    ) {
        PaymentTypeResponse created = paymentTypeService.create(resolveUserId(principal, authHeader), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Payment type added", created));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a custom payment type that no transaction uses")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id
    ) {
        paymentTypeService.delete(id, resolveUserId(principal, authHeader));
        return ResponseEntity.ok(ApiResponse.ok("Payment type deleted", null));
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
        throw new UnauthorizedException("Authentication token required to access payment types");
    }
}
