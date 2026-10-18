package com.flow.transaction.service;

import com.flow.common.exception.BadRequestException;
import com.flow.common.exception.ErrorCode;
import com.flow.common.exception.ResourceNotFoundException;
import com.flow.transaction.dto.PaymentTypeRequest;
import com.flow.transaction.dto.PaymentTypeResponse;
import com.flow.transaction.entity.PaymentMethod;
import com.flow.transaction.entity.PaymentType;
import com.flow.transaction.repository.PaymentTypeRepository;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentTypeService {

    private final PaymentTypeRepository paymentTypeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PaymentTypeResponse> list(UUID userId) {
        List<PaymentTypeResponse> result = new ArrayList<>();
        for (PaymentMethod m : PaymentMethod.values()) {
            result.add(PaymentTypeResponse.builder().code(m.name()).label(builtInLabel(m)).builtIn(true).build());
        }
        for (PaymentType t : paymentTypeRepository.findByUserIdOrderByNameAsc(userId)) {
            result.add(PaymentTypeResponse.builder().code(t.getId().toString()).label(t.getName()).builtIn(false).build());
        }
        return result;
    }

    @Transactional
    public PaymentTypeResponse create(UUID userId, PaymentTypeRequest request) {
        String name = request.getName().trim();
        if (name.length() < 2) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST, "Name must be at least 2 characters");
        }
        boolean clashesWithBuiltIn = Arrays.stream(PaymentMethod.values())
                .anyMatch(m -> builtInLabel(m).equalsIgnoreCase(name) || m.name().equalsIgnoreCase(name));
        if (clashesWithBuiltIn || paymentTypeRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST, "A payment type named \"" + name + "\" already exists");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        PaymentType saved = paymentTypeRepository.save(PaymentType.builder().user(user).name(name).build());
        return PaymentTypeResponse.builder().code(saved.getId().toString()).label(saved.getName()).builtIn(false).build();
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        PaymentType type = paymentTypeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Payment type not found"));

        long used = paymentTypeRepository.countTransactionsUsing(userId, id.toString());
        if (used > 0) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST,
                    "\"" + type.getName() + "\" is used by " + used + " transaction(s) and cannot be deleted");
        }
        paymentTypeRepository.delete(type);
    }

    /**
     * Returns the code to store on a transaction. Built-in codes pass through; anything else must be one of
     * this user's custom payment types. A missing code defaults to CASH.
     */
    @Transactional(readOnly = true)
    public String validate(UUID userId, String code) {
        if (code == null || code.isBlank()) {
            return PaymentMethod.CASH.name();
        }
        String trimmed = code.trim();
        if (PaymentMethod.isBuiltIn(trimmed)) {
            return trimmed;
        }
        try {
            UUID id = UUID.fromString(trimmed);
            if (paymentTypeRepository.findByIdAndUserId(id, userId).isPresent()) {
                return id.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // not a UUID, so fall through to the error below
        }
        throw new BadRequestException(ErrorCode.INVALID_REQUEST, "Unknown payment type: " + trimmed);
    }

    private static String builtInLabel(PaymentMethod m) {
        return switch (m) {
            case UPI -> "UPI";
            case CASH -> "Cash";
            case CREDIT_CARD -> "Credit card";
            case DEBIT_CARD -> "Debit card";
            case BANK_TRANSFER -> "Bank transfer";
            case OTHER -> "Other";
        };
    }
}
