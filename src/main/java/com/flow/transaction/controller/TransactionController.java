package com.flow.transaction.controller;

import com.flow.auth.jwt.JwtService;
import com.flow.auth.security.UserPrincipal;
import com.flow.common.exception.UnauthorizedException;
import com.flow.common.response.ApiResponse;
import com.flow.common.response.PageResponse;
import com.flow.transaction.dto.CreateTransactionRequest;
import com.flow.transaction.dto.TransactionResponse;
import com.flow.transaction.dto.UpdateTransactionRequest;
import com.flow.transaction.entity.TransactionType;
import com.flow.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction management API")
public class TransactionController {

    private final TransactionService transactionService;
    private final JwtService jwtService;

    @PostMapping
    @Operation(summary = "Create a new expense or income transaction")
    public ResponseEntity<ApiResponse<TransactionResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        UUID userId = resolveUserId(principal, authHeader);
        TransactionResponse response = transactionService.createTransaction(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Transaction created successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get paginated list of transactions with flexible filters")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = resolveUserId(principal, authHeader);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate", "id"));
        PageResponse<TransactionResponse> result = transactionService.getTransactions(
                userId, type, categoryId, startDate, endDate, search, pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction details by ID")
    public ResponseEntity<ApiResponse<TransactionResponse>> getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(principal, authHeader);
        TransactionResponse response = transactionService.getTransaction(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing transaction")
    public ResponseEntity<ApiResponse<TransactionResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionRequest request
    ) {
        UUID userId = resolveUserId(principal, authHeader);
        TransactionResponse response = transactionService.updateTransaction(id, userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Transaction updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(principal, authHeader);
        transactionService.deleteTransaction(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Transaction deleted successfully", null));
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
        throw new UnauthorizedException("Authentication token required to access transactions");
    }
}
