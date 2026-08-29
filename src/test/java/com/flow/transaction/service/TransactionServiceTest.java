package com.flow.transaction.service;

import com.flow.category.entity.Category;
import com.flow.category.entity.CategoryType;
import com.flow.category.service.CategoryService;
import com.flow.common.exception.ResourceNotFoundException;
import com.flow.transaction.dto.CreateTransactionRequest;
import com.flow.transaction.dto.TransactionResponse;
import com.flow.transaction.entity.PaymentMethod;
import com.flow.transaction.entity.Transaction;
import com.flow.transaction.entity.TransactionType;
import com.flow.transaction.repository.TransactionRepository;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("user@flow.app")
                .fullName("John Doe")
                .passwordHash("hashed")
                .currency("INR")
                .build();

        testCategory = Category.builder()
                .id(10L)
                .name("Food & Dining")
                .type(CategoryType.EXPENSE)
                .isSystem(true)
                .build();
    }

    @Test
    @DisplayName("Successfully creates an expense transaction with exact BigDecimal precision")
    void createExpenseTransaction_Success() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("450.50"))
                .description("Dinner with friends")
                .categoryId(10L)
                .transactionDate(LocalDate.now())
                .paymentMethod(PaymentMethod.UPI)
                .notes("Splits with Alice")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryService.getCategoryEntity(10L, 1L)).thenReturn(testCategory);

        Transaction savedTx = Transaction.builder()
                .id(100L)
                .user(testUser)
                .category(testCategory)
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("450.50"))
                .description("Dinner with friends")
                .transactionDate(LocalDate.now())
                .paymentMethod(PaymentMethod.UPI)
                .notes("Splits with Alice")
                .build();

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);

        TransactionResponse response = transactionService.createTransaction(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("450.50"));
        assertThat(response.getDescription()).isEqualTo("Dinner with friends");
        assertThat(response.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.getCategory().getName()).isEqualTo("Food & Dining");

        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when non-existent user creates transaction")
    void createTransaction_UserNotFound() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("100.00"))
                .description("Test")
                .transactionDate(LocalDate.now())
                .build();

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(999L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
