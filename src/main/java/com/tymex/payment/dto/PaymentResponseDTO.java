package com.tymex.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tymex.payment.enums.PaymentProvider;
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
    PaymentProvider paymentProvider,
    String providerTransactionId,
    String idempotencyKey,
    boolean cached,
    String error,
    String message
) {
    /**
     * Factory method to create PaymentResponseDTO without optional fields.
     * Useful when idempotencyKey is set later. Cached defaults to false.
     * 
     * @param transactionNo the transaction number (from external provider)
     * @param status the payment status
     * @param amount the payment amount
     * @param paymentMethod the payment method
     * @param description the payment description
     * @param createdAt the creation timestamp
     * @param paymentProvider the payment provider used
     * @return PaymentResponseDTO with idempotencyKey set to null and cached set to false
     */
    public static PaymentResponseDTO of(
            String transactionNo,
            PaymentStatus status,
            BigDecimal amount,
            String paymentMethod,
            String description,
            LocalDateTime createdAt,
            PaymentProvider paymentProvider) {
        return new PaymentResponseDTO(
            transactionNo,
            status,
            amount,
            paymentMethod,
            description,
            createdAt,
            paymentProvider,
            null,  // providerTransactionId (null for sync providers)
            null,  // idempotencyKey
            false,  // Default to not cached
            null,   // error
            null    // message
        );
    }
    
    /**
     * Factory method to create PaymentResponseDTO with provider transaction ID.
     * Used for asynchronous providers that return a provider transaction ID.
     * 
     * @param transactionNo the transaction number (from external provider, may be null for async)
     * @param status the payment status
     * @param amount the payment amount
     * @param paymentMethod the payment method
     * @param description the payment description
     * @param createdAt the creation timestamp
     * @param paymentProvider the payment provider used
     * @param providerTransactionId the provider's transaction ID (for webhook lookup)
     * @return PaymentResponseDTO with providerTransactionId set
     */
    public static PaymentResponseDTO of(
            String transactionNo,
            PaymentStatus status,
            BigDecimal amount,
            String paymentMethod,
            String description,
            LocalDateTime createdAt,
            PaymentProvider paymentProvider,
            String providerTransactionId) {
        return new PaymentResponseDTO(
            transactionNo,
            status,
            amount,
            paymentMethod,
            description,
            createdAt,
            paymentProvider,
            providerTransactionId,
            null,  // idempotencyKey
            false,  // Default to not cached
            null,   // error
            null    // message
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
            paymentProvider,
            providerTransactionId,
            idempotencyKey,
            cached,
            error,
            message
        );
    }
    
    /**
     * Factory method to create PaymentResponseDTO for failed payments.
     * 
     * @param amount the payment amount
     * @param paymentMethod the payment method
     * @param description the payment description
     * @param createdAt the creation timestamp
     * @param paymentProvider the payment provider used
     * @param error the error code
     * @param message the error message
     * @return PaymentResponseDTO with status FAILED and error details
     */
    public static PaymentResponseDTO failed(
            BigDecimal amount,
            String paymentMethod,
            String description,
            LocalDateTime createdAt,
            PaymentProvider paymentProvider,
            String error,
            String message) {
        return new PaymentResponseDTO(
            null,  // transactionNo (null for failed payments)
            PaymentStatus.FAILED,
            amount,
            paymentMethod,
            description,
            createdAt,
            paymentProvider,
            null,  // providerTransactionId
            null,  // idempotencyKey
            false,  // cached
            error,
            message
        );
    }
}
