package com.flow.transaction.service;

import com.flow.category.entity.Category;
import com.flow.category.entity.CategoryType;
import com.flow.category.repository.CategoryRepository;
import com.flow.category.service.CategoryService;
import com.flow.common.exception.ErrorCode;
import com.flow.common.exception.ResourceNotFoundException;
import com.flow.common.response.PageResponse;
import com.flow.transaction.dto.CreateTransactionRequest;
import com.flow.transaction.dto.TransactionResponse;
import com.flow.transaction.dto.UpdateTransactionRequest;
import com.flow.transaction.entity.PaymentMethod;
import com.flow.transaction.entity.Transaction;
import com.flow.transaction.entity.TransactionType;
import com.flow.transaction.repository.TransactionRepository;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;

    @Transactional
    public TransactionResponse createTransaction(UUID userId, CreateTransactionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryService.getCategoryEntity(request.getCategoryId(), userId);
        } else {
            // Default to "General" category if no category selected
            CategoryType catType = request.getType() == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
            category = categoryRepository.findGeneralCategory(userId, catType).orElse(null);
        }

        PaymentMethod paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CASH;

        Transaction transaction = Transaction.builder()
                .user(user)
                .category(category)
                .type(request.getType())
                .amount(request.getAmount())
                .description(request.getDescription().trim())
                .transactionDate(request.getTransactionDate())
                .paymentMethod(paymentMethod)
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Created transaction id: {} ({} {}) for user id: {}", 
                transaction.getId(), transaction.getType(), transaction.getAmount(), userId);

        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID id, UUID userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"));
        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactions(
            UUID userId,
            TransactionType type,
            UUID categoryId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            Pageable pageable
    ) {
        Page<Transaction> page = transactionRepository.findAllFiltered(
                userId, type, categoryId, startDate, endDate, search, pageable
        );
        return PageResponse.from(page.map(TransactionResponse::from));
    }

    @Transactional
    public TransactionResponse updateTransaction(UUID id, UUID userId, UpdateTransactionRequest request) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"));

        if (request.getType() != null) {
            transaction.setType(request.getType());
        }
        if (request.getAmount() != null) {
            transaction.setAmount(request.getAmount());
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            transaction.setDescription(request.getDescription().trim());
        }
        if (request.getCategoryId() != null) {
            Category category = categoryService.getCategoryEntity(request.getCategoryId(), userId);
            transaction.setCategory(category);
        }
        if (request.getTransactionDate() != null) {
            transaction.setTransactionDate(request.getTransactionDate());
        }
        if (request.getPaymentMethod() != null) {
            transaction.setPaymentMethod(request.getPaymentMethod());
        }
        if (request.getNotes() != null) {
            transaction.setNotes(request.getNotes().trim());
        }

        transaction = transactionRepository.save(transaction);
        log.info("Updated transaction id: {} for user id: {}", id, userId);
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void deleteTransaction(UUID id, UUID userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"));

        transactionRepository.delete(transaction);
        log.info("Deleted transaction id: {} for user id: {}", id, userId);
    }
}
