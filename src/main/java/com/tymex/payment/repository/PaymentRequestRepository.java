package com.tymex.payment.repository;

import com.tymex.payment.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {
    Optional<PaymentRequest> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Finds a payment request by provider transaction ID.
     * Used for webhook processing to look up payment by provider's transaction ID.
     * 
     * @param providerTransactionId the provider's transaction ID
     * @return Optional PaymentRequest if found
     */
    Optional<PaymentRequest> findByProviderTransactionId(String providerTransactionId);
}

