package com.shopsphere.orderservice.repository;

import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status, Pageable pageable);

    /* ---------------- US46 — payment reconciliation ---------------- */

    List<Order> findByStatusAndPaidAtIsNotNullOrderByUpdatedAtDesc(OrderStatus status, Pageable pageable);

    List<Order> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus status, Instant before, Pageable pageable);
}
