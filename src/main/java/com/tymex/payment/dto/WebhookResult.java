package com.tymex.payment.dto;

import com.tymex.payment.enums.PaymentStatus;

/**
 * Result object containing parsed webhook data from payment providers.
 * Used to decouple webhook parsing (in providers) from webhook processing (in service).
 */
public record WebhookResult(
    String providerTransactionId,
    String transactionNo,
    PaymentStatus status,
    String payload
) {
    /**
     * Factory method to create WebhookResult.
     * 
     * @param providerTransactionId the provider's transaction ID (used to look up payment record)
     * @param transactionNo the final transaction number (may be null if payment failed)
     * @param status the payment status (COMPLETED or FAILED)
     * @param payload the original webhook payload (for hash validation)
     * @return WebhookResult with all fields
     */
    public static WebhookResult of(String providerTransactionId, String transactionNo, 
                                   PaymentStatus status, String payload) {
        return new WebhookResult(providerTransactionId, transactionNo, status, payload);
    }
}

