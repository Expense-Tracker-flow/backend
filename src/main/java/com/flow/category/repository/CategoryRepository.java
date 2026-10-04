package com.flow.category.repository;

import com.flow.category.entity.Category;
import com.flow.category.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE (c.user.id = :userId OR c.isSystem = true) ORDER BY c.name ASC")
    List<Category> findAllAvailableForUser(@Param("userId") UUID userId);

    @Query("SELECT c FROM Category c WHERE (c.user.id = :userId OR c.isSystem = true) AND c.type = :type ORDER BY c.name ASC")
    List<Category> findAllAvailableForUserAndType(@Param("userId") UUID userId, @Param("type") CategoryType type);

    @Query("SELECT c FROM Category c WHERE c.id = :id AND (c.user.id = :userId OR c.isSystem = true)")
    Optional<Category> findByIdAndUserAccess(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE (c.user.id = :userId OR c.isSystem = true) AND LOWER(c.name) = LOWER(:name) AND c.type = :type")
    boolean existsByNameAndTypeForUser(@Param("userId") UUID userId, @Param("name") String name, @Param("type") CategoryType type);
}
