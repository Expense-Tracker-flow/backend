package com.flow.category.controller;

import com.flow.auth.jwt.JwtService;
import com.flow.auth.security.UserPrincipal;
import com.flow.category.dto.CategoryResponse;
import com.flow.category.dto.CreateCategoryRequest;
import com.flow.category.entity.CategoryType;
import com.flow.category.service.CategoryService;
import com.flow.common.exception.UnauthorizedException;
import com.flow.common.response.ApiResponse;
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
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Categories management")
public class CategoryController {

    private final CategoryService categoryService;
    private final JwtService jwtService;

    @GetMapping
    @Operation(summary = "List all available categories for the authenticated user")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) CategoryType type
    ) {
        UUID userId = resolveUserId(principal, authHeader);
        List<CategoryResponse> categories = categoryService.getCategories(userId, type);
        return ResponseEntity.ok(ApiResponse.ok(categories));
    }

    @PostMapping
    @Operation(summary = "Create a new custom category for the user")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        UUID userId = resolveUserId(principal, authHeader);
        CategoryResponse response = categoryService.createCategory(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Category created successfully", response));
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
        throw new UnauthorizedException("Authentication token required to access categories");
    }
}
