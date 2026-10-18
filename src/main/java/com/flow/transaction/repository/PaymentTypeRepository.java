package com.flow.transaction.repository;

import com.flow.transaction.entity.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTypeRepository extends JpaRepository<PaymentType, UUID> {

    List<PaymentType> findByUserIdOrderByNameAsc(UUID userId);

    Optional<PaymentType> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId AND t.paymentMethod = :code")
    long countTransactionsUsing(@Param("userId") UUID userId, @Param("code") String code);
}
