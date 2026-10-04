package com.flow.category.service;

import com.flow.category.dto.CategoryResponse;
import com.flow.category.dto.CreateCategoryRequest;
import com.flow.category.entity.Category;
import com.flow.category.entity.CategoryType;
import com.flow.category.repository.CategoryRepository;
import com.flow.common.exception.BadRequestException;
import com.flow.common.exception.ErrorCode;
import com.flow.common.exception.ResourceNotFoundException;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(UUID userId, CategoryType type) {
        List<Category> categories;
        if (type != null) {
            categories = categoryRepository.findAllAvailableForUserAndType(userId, type);
        } else {
            categories = categoryRepository.findAllAvailableForUser(userId);
        }
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Category getCategoryEntity(UUID categoryId, UUID userId) {
        return categoryRepository.findByIdAndUserAccess(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found or inaccessible"));
    }

    @Transactional
    public CategoryResponse createCategory(UUID userId, CreateCategoryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));

        String trimmedName = request.getName().trim();
        if (categoryRepository.existsByNameAndTypeForUser(userId, trimmedName, request.getType())) {
            throw new BadRequestException("A " + request.getType().name().toLowerCase() + " category named '" + trimmedName + "' already exists");
        }

        Category category = Category.builder()
                .user(user)
                .name(request.getName().trim())
                .icon(request.getIcon() != null ? request.getIcon().trim() : "tag")
                .color(request.getColor() != null ? request.getColor().trim() : "#64748B")
                .type(request.getType())
                .isSystem(false)
                .build();

        category = categoryRepository.save(category);
        log.info("Created custom category [{}] for user id: {}", category.getName(), userId);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId, UUID userId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found"));

        if (Boolean.TRUE.equals(category.getIsSystem())) {
            throw new BadRequestException("System categories cannot be deleted");
        }

        if (category.getUser() == null || !category.getUser().getId().equals(userId)) {
            throw new BadRequestException(ErrorCode.ACCESS_DENIED, "You cannot delete another user's category");
        }

        categoryRepository.delete(category);
        log.info("Deleted category id: {} for user id: {}", categoryId, userId);
    }
}
