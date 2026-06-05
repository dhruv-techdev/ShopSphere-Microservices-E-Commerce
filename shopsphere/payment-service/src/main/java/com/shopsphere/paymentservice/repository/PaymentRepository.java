package com.shopsphere.paymentservice.repository;

import com.shopsphere.paymentservice.entity.Payment;
import com.shopsphere.paymentservice.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    /** Multiple payments per order are possible (retries after failure). Most recent first. */
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<Payment> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PaymentStatus status);
}
