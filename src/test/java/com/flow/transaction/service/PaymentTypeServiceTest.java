package com.flow.transaction.service;

import com.flow.common.exception.BadRequestException;
import com.flow.transaction.dto.PaymentTypeRequest;
import com.flow.transaction.entity.PaymentType;
import com.flow.transaction.repository.PaymentTypeRepository;
import com.flow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTypeServiceTest {

    @Mock
    private PaymentTypeRepository paymentTypeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentTypeService paymentTypeService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void builtInCodePassesThrough() {
        assertThat(paymentTypeService.validate(userId, "UPI")).isEqualTo("UPI");
        verifyNoInteractions(paymentTypeRepository);
    }

    @Test
    void missingCodeDefaultsToCash() {
        assertThat(paymentTypeService.validate(userId, null)).isEqualTo("CASH");
        assertThat(paymentTypeService.validate(userId, "  ")).isEqualTo("CASH");
    }

    @Test
    void customTypeOwnedByUserIsAccepted() {
        UUID typeId = UUID.randomUUID();
        when(paymentTypeRepository.findByIdAndUserId(typeId, userId)).thenReturn(Optional.of(new PaymentType()));

        assertThat(paymentTypeService.validate(userId, typeId.toString())).isEqualTo(typeId.toString());
    }

    @Test
    void customTypeOfAnotherUserIsRejected() {
        UUID typeId = UUID.randomUUID();
        when(paymentTypeRepository.findByIdAndUserId(typeId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentTypeService.validate(userId, typeId.toString()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void unknownCodeIsRejected() {
        assertThatThrownBy(() -> paymentTypeService.validate(userId, "BITCOIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createRejectsNameThatClashesWithBuiltIn() {
        assertThatThrownBy(() -> paymentTypeService.create(userId, new PaymentTypeRequest("cash")))
                .isInstanceOf(BadRequestException.class);
        verify(paymentTypeRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateCustomName() {
        when(paymentTypeRepository.existsByUserIdAndNameIgnoreCase(userId, "Paytm Wallet")).thenReturn(true);

        assertThatThrownBy(() -> paymentTypeService.create(userId, new PaymentTypeRequest("Paytm Wallet")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteBlockedWhenTransactionsUseIt() {
        UUID typeId = UUID.randomUUID();
        when(paymentTypeRepository.findByIdAndUserId(typeId, userId)).thenReturn(Optional.of(new PaymentType()));
        when(paymentTypeRepository.countTransactionsUsing(eq(userId), anyString())).thenReturn(3L);

        assertThatThrownBy(() -> paymentTypeService.delete(typeId, userId))
                .isInstanceOf(BadRequestException.class);
        verify(paymentTypeRepository, never()).delete(any(PaymentType.class));
    }
}
