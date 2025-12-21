package com.tymex.payment.repository;

import com.tymex.payment.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {
    Optional<PaymentRequest> findByIdempotencyKey(String idempotencyKey);
}

