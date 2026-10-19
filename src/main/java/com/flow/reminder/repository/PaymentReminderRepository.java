package com.flow.reminder.repository;

import com.flow.reminder.entity.PaymentReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentReminderRepository extends JpaRepository<PaymentReminder, UUID> {

    List<PaymentReminder> findByUserIdOrderByNextDueDateAsc(UUID userId);

    List<PaymentReminder> findByUserIdAndActiveTrue(UUID userId);

    Optional<PaymentReminder> findByIdAndUserId(UUID id, UUID userId);
}
