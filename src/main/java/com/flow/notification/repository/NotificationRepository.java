package com.flow.notification.repository;

import com.flow.notification.entity.Notification;
import com.flow.transaction.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop30ByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    // Transactions are read here so the alert checks need no other repository
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId AND t.transactionDate = :date")
    long countTransactionsOn(@Param("userId") UUID userId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId")
    long countAllTransactions(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.type = :type "
            + "AND t.transactionDate BETWEEN :from AND :to")
    BigDecimal sumBetween(@Param("userId") UUID userId, @Param("type") TransactionType type,
                          @Param("from") LocalDate from, @Param("to") LocalDate to);
}
