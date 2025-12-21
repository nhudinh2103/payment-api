package com.tymex.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tymex.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponseDTO(
    String transactionNo,
    PaymentStatus status,
    BigDecimal amount,
    String paymentMethod,
    String description,
    LocalDateTime createdAt,
    String idempotencyKey,
    boolean cached
) {
    /**
     * Factory method to create PaymentResponseDTO without optional fields.
     * Useful when idempotencyKey is set later. Cached defaults to false.
     * 
     * @param transactionNo the transaction number
     * @param status the payment status
     * @param amount the payment amount
     * @param paymentMethod the payment method
     * @param description the payment description
     * @param createdAt the creation timestamp
     * @return PaymentResponseDTO with idempotencyKey set to null and cached set to false
     */
    public static PaymentResponseDTO of(
            String transactionNo,
            PaymentStatus status,
            BigDecimal amount,
            String paymentMethod,
            String description,
            LocalDateTime createdAt) {
        return new PaymentResponseDTO(
            transactionNo,
            status,
            amount,
            paymentMethod,
            description,
            createdAt,
            null,
            false  // Default to not cached
        );
    }
    
    /**
     * Creates a new PaymentResponseDTO with updated idempotencyKey and cached fields.
     * 
     * @param idempotencyKey the idempotency key
     * @param cached whether the response is cached
     * @return new PaymentResponseDTO with updated fields
     */
    public PaymentResponseDTO withMetadata(String idempotencyKey, boolean cached) {
        return new PaymentResponseDTO(
            transactionNo,
            status,
            amount,
            paymentMethod,
            description,
            createdAt,
            idempotencyKey,
            cached
        );
    }
}
