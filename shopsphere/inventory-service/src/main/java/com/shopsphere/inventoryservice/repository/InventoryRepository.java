package com.shopsphere.inventoryservice.repository;

import com.shopsphere.inventoryservice.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    /**
     * Pessimistic-write lock when we need a guaranteed-serialised stock mutation.
     * Use sparingly — only for the critical adjust path. For read-only checks use findByProductId.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    @Query("""
            SELECT i FROM Inventory i
            WHERE (i.availableQuantity - i.reservedQuantity) <= :threshold
            """)
    List<Inventory> findLowStock(@Param("threshold") int threshold);
}
