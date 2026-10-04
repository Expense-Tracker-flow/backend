package com.flow.transaction.repository;

import com.flow.transaction.entity.Transaction;
import com.flow.transaction.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
           "AND (:startDate IS NULL OR t.transactionDate >= :startDate) " +
           "AND (:endDate IS NULL OR t.transactionDate <= :endDate) " +
           "AND (:search IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:search as string), '%')) OR LOWER(CAST(t.notes as string)) LIKE LOWER(CONCAT('%', CAST(:search as string), '%'))) " +
           "ORDER BY t.transactionDate DESC, t.createdAt DESC")
    Page<Transaction> findAllFiltered(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("categoryId") UUID categoryId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate ORDER BY t.transactionDate ASC")
    List<Transaction> findAllInDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.type = :type")
    BigDecimal sumTotalByUserIdAndType(@Param("userId") UUID userId, @Param("type") TransactionType type);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.type = :type AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate")
    BigDecimal sumByUserIdAndTypeAndDateRange(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COALESCE(c.name, 'General'), COALESCE(c.color, '#6366F1'), COALESCE(c.icon, 'default'), SUM(t.amount), COUNT(t.id) " +
           "FROM Transaction t LEFT JOIN t.category c " +
           "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate " +
           "GROUP BY COALESCE(c.name, 'General'), COALESCE(c.color, '#6366F1'), COALESCE(c.icon, 'default') " +
           "ORDER BY SUM(t.amount) DESC")
    List<Object[]> findCategorySpendingBreakdown(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t.transactionDate, " +
           "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) " +
           "FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate " +
           "GROUP BY t.transactionDate " +
           "ORDER BY t.transactionDate ASC")
    List<Object[]> findDailyTrends(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
