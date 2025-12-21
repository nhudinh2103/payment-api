package com.tymex.payment.service.provider.contract;

import java.util.Map;

/**
 * Strategy interface for payment providers that support webhook callbacks.
 * Separate from PaymentProviderStrategy to follow Interface Segregation Principle (ISP).
 * 
 * Only asynchronous providers (e.g., MoMo) implement this interface.
 * Synchronous providers (e.g., Stripe) do not implement this interface.
 * 
 * Clients that need webhook handling should depend on this interface,
 * not on PaymentProviderStrategy.
 */
public interface WebhookCapablePaymentProviderStrategy {
    
    /**
     * Handles webhook event from the provider.
     * 
     * For asynchronous providers (e.g., MoMo): Provider sends webhook to our endpoint with payment result.
     * This method processes the webhook and updates the payment status.
     * 
     * Each provider implementation is responsible for:
     * - Extracting provider_transaction_id from its own webhook payload format
     * - Parsing the webhook payload according to its own schema
     * - Calling PaymentService.processWebhook() with the extracted idempotency key
     * 
     * @param payload the webhook payload (JSON string)
     * @param headers the webhook headers (for signature verification, etc.)
     * @throws IllegalArgumentException if webhook is invalid or payment not found
     */
    void handleWebhook(String payload, Map<String, String> headers);
}

