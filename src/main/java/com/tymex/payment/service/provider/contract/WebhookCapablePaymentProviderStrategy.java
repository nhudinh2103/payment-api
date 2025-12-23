package com.tymex.payment.service.provider.contract;

import com.tymex.payment.dto.WebhookResult;

import java.util.Map;

/**
 * Strategy interface for payment providers that support webhook callbacks.
 * Separate from PaymentProviderStrategy to follow Interface Segregation Principle (ISP).
 * 
 * Only asynchronous providers (e.g., MoMo) implement this interface.
 * Synchronous providers (e.g., Stripe) do not implement this interface.
 * 
 * Each provider implementation is responsible for:
 * - Extracting provider_transaction_id from its own webhook payload format
 * - Parsing the webhook payload according to its own schema
 * - Returning parsed webhook data as WebhookResult
 * 
 * The controller coordinates between provider (handling) and service (processing)
 * to avoid circular dependencies.
 */
public interface WebhookCapablePaymentProviderStrategy {
    
    /**
     * Handles webhook event from the provider by parsing it according to provider-specific format.
     * 
     * This method is responsible for provider-specific webhook handling:
     * - Verifying webhook signature (if applicable)
     * - Parsing webhook payload according to provider's schema
     * - Extracting provider_transaction_id, transaction_no, and status
     * - Converting provider-specific status to PaymentStatus enum
     * 
     * Note: This method only handles parsing/preparation. The actual processing
     * (idempotency, database updates) is handled by PaymentService.
     * 
     * @param payload the webhook payload (JSON string)
     * @param headers the webhook headers (for signature verification, etc.)
     * @return WebhookResult containing parsed webhook data ready for processing
     * @throws IllegalArgumentException if webhook is invalid
     */
    WebhookResult handleWebhook(String payload, Map<String, String> headers);
}

