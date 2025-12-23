package com.tymex.payment.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tymex.payment.dto.PaymentRequestDTO;
import com.tymex.payment.dto.PaymentResponseDTO;
import com.tymex.payment.dto.WebhookResult;
import com.tymex.payment.enums.PaymentProvider;
import com.tymex.payment.enums.PaymentStatus;
import com.tymex.payment.exception.PaymentException;
import com.tymex.payment.service.provider.contract.PaymentProviderStrategy;
import com.tymex.payment.service.provider.contract.WebhookCapablePaymentProviderStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * MoMo payment provider implementation (Asynchronous).
 * Calls MoMo API, returns immediately with PENDING status.
 * MoMo provides webhook URL that we listen to for payment result.
 * Final result comes via webhook callback.
 */
@Service
public class MoMoPaymentProvider implements PaymentProviderStrategy, WebhookCapablePaymentProviderStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(MoMoPaymentProvider.class);
    private final ObjectMapper objectMapper;
    
    public MoMoPaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public PaymentResponseDTO process(PaymentRequestDTO request, String idempotencyKey) {
        log.info("Handling external provider MOMO: amount={}, method={}, idempotencyKey={}", 
                 request.amount(), request.paymentMethod(), idempotencyKey);
        
        // Simulate payment processing validation
        if (request.amount().compareTo(new BigDecimal("10000")) > 0) {
            throw new PaymentException("Payment amount exceeds limit");
        }
        
        // Simulate calling MoMo API (returns immediately, no delay)
        // In real implementation: HTTP call to MoMo API
        String providerJsonResponse = simulateProviderApiCall(request);
        
        // Extract provider transaction ID and webhook URL from response
        ProviderResponse providerResponse = parseProviderResponse(providerJsonResponse);
        
        // Store provider transaction ID and webhook URL for later webhook processing
        // In real implementation: Store in database for webhook lookup
        log.info("MoMo external provider handled: providerTransactionId={}, webhookUrl={}", 
                 providerResponse.transactionId(), providerResponse.webhookUrl());
        
        // Return PENDING response immediately (no transactionNo yet)
        // TransactionNo will be set when webhook arrives
        PaymentResponseDTO response = PaymentResponseDTO.of(
            null,                        // transactionNo is null (will be set via webhook)
            PaymentStatus.PENDING,       // Status is PENDING for async providers
            request.amount(),
            request.paymentMethod(),
            request.description(),
            LocalDateTime.now(),
            PaymentProvider.MOMO,
            providerResponse.transactionId()  // Store provider transaction ID for webhook lookup
        );
        
        log.info("MoMo external provider handled successfully: status=PENDING, providerTransactionId={}", 
                 providerResponse.transactionId());
        return response;
    }
    
    @Override
    public WebhookResult handleWebhook(String payload, Map<String, String> headers) {
        // Verify webhook signature (in real implementation)
        // verifyWebhookSignature(payload, headers);
        
        log.info("Parsing MoMo webhook: payload length={}, headers={}", 
                 payload.length(), headers.keySet());
        
        // Parse webhook payload
        WebhookEvent webhookEvent = parseWebhookPayload(payload);
        
        // Extract provider_transaction_id from MoMo webhook payload format
        // MoMo uses "transaction_id" field in their webhook payload
        String providerTransactionId = webhookEvent.transactionId();
        
        log.info("Parsed MoMo webhook for providerTransactionId={}", providerTransactionId);
        
        // Convert webhook status to PaymentStatus enum
        PaymentStatus paymentStatus;
        if ("SUCCEED".equalsIgnoreCase(webhookEvent.status())) {
            paymentStatus = PaymentStatus.COMPLETED;
        } else if ("FAILED".equalsIgnoreCase(webhookEvent.status())) {
            paymentStatus = PaymentStatus.FAILED;
        } else {
            log.warn("MoMo webhook: Unknown status '{}', defaulting to FAILED", webhookEvent.status());
            paymentStatus = PaymentStatus.FAILED;
        }
        
        // Return parsed webhook data
        return WebhookResult.of(
            providerTransactionId,  // provider_transaction_id (used to look up payment record)
            webhookEvent.transactionNo(),  // final transaction number (may be null if failed)
            paymentStatus,  // payment status (COMPLETED or FAILED)
            payload  // original payload for hash validation
        );
    }
    
    /**
     * Simulates calling MoMo API and returning JSON response.
     * MoMo returns immediately with provider transaction ID and webhook URL.
     * 
     * Response format: {
     *   "transaction_id": "<provider_transaction_id>",
     *   "webhook_url": "https://momo-provider.com/webhooks/...",
     *   "status": "PENDING"
     * }
     */
    private String simulateProviderApiCall(PaymentRequestDTO request) {
        // MoMo returns immediately (no delay for async providers)
        String providerTransactionId = "MOMO_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String webhookUrl = "https://momo-provider.com/webhooks/" + providerTransactionId;
        
        // Simulate MoMo JSON response format
        return String.format(
            "{\"transaction_id\":\"%s\",\"webhook_url\":\"%s\",\"status\":\"PENDING\"}",
            providerTransactionId, webhookUrl
        );
    }
    
    /**
     * Parses MoMo provider response to extract transaction ID and webhook URL.
     */
    private ProviderResponse parseProviderResponse(String jsonResponse) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            String transactionId = jsonNode.get("transaction_id").asText();
            String webhookUrl = jsonNode.get("webhook_url").asText();
            return new ProviderResponse(transactionId, webhookUrl);
        } catch (Exception e) {
            throw new PaymentException("Failed to parse MoMo response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parses webhook payload from MoMo.
     * 
     * Webhook format: {
     *   "transaction_id": "<provider_transaction_id>",
     *   "transaction_no": "<final_transaction_no>",
     *   "status": "SUCCEED" (or "FAILED"),
     *   ...
     * }
     */
    private WebhookEvent parseWebhookPayload(String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String transactionId = jsonNode.get("transaction_id").asText();
            String transactionNo = jsonNode.has("transaction_no") 
                ? jsonNode.get("transaction_no").asText() 
                : null;
            String status = jsonNode.get("status").asText();
            return new WebhookEvent(transactionId, transactionNo, status);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse MoMo webhook payload: " + e.getMessage(), e);
        }
    }
    
    /**
     * Record for provider response data.
     */
    private record ProviderResponse(String transactionId, String webhookUrl) {}
    
    /**
     * Record for webhook event data.
     */
    private record WebhookEvent(String transactionId, String transactionNo, String status) {}
}

