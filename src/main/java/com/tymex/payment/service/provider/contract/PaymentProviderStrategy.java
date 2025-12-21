package com.tymex.payment.service.provider.contract;

import com.tymex.payment.dto.PaymentRequestDTO;
import com.tymex.payment.dto.PaymentResponseDTO;

/**
 * Strategy interface for different payment provider implementations.
 * Supports both synchronous (Stripe) and asynchronous (MoMo) payment providers.
 */
public interface PaymentProviderStrategy {
    
    /**
     * Initiates payment with the provider.
     * 
     * For synchronous providers (e.g., Stripe):
     * - Calls provider API and waits for response
     * - Returns PaymentResponseDTO with COMPLETED/FAILED status and transactionNo
     * 
     * For asynchronous providers (e.g., MoMo):
     * - Calls provider API and returns immediately
     * - Provider provides webhook URL to listen for payment result
     * - Returns PaymentResponseDTO with PENDING status (transactionNo will be null)
     * - Final result comes via webhook callback
     * 
     * @param request the payment request
     * @param idempotencyKey the idempotency key for this payment
     * @return PaymentResponseDTO with payment result (or PENDING for async providers)
     */
    PaymentResponseDTO initiatePayment(PaymentRequestDTO request, String idempotencyKey);
    
    /**
     * Whether this provider uses synchronous or asynchronous payment flow.
     * 
     * Purpose:
     * - PaymentService can use this to determine if it should wait for response
     * - Useful for logging and debugging
     * - Can be used to determine if webhook polling is needed
     * 
     * Note: This can also be inferred from the response status (PENDING = async),
     * but having explicit method provides type safety and clarity.
     * 
     * @return true if synchronous (waits for response), false if asynchronous (returns immediately)
     */
    boolean isSynchronous();
}

