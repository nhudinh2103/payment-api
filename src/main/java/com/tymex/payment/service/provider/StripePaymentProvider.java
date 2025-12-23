package com.tymex.payment.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tymex.payment.dto.PaymentRequestDTO;
import com.tymex.payment.dto.PaymentResponseDTO;
import com.tymex.payment.enums.PaymentProvider;
import com.tymex.payment.enums.PaymentStatus;
import com.tymex.payment.exception.PaymentException;
import com.tymex.payment.service.provider.contract.PaymentProviderStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stripe payment provider implementation (Synchronous).
 * Calls Stripe API and waits for response with transaction result.
 */
@Service
public class StripePaymentProvider implements PaymentProviderStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);
    private final ObjectMapper objectMapper;
    
    public StripePaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public PaymentResponseDTO process(PaymentRequestDTO request, String idempotencyKey) {
        log.info("Processing payment via STRIPE: amount={}, method={}, idempotencyKey={}", 
                 request.amount(), request.paymentMethod(), idempotencyKey);
        
        // Simulate payment processing
        if (request.amount().compareTo(new BigDecimal("10000")) > 0) {
            throw new PaymentException("Payment amount exceeds limit");
        }
        
        // Simulate external provider API call and get JSON response
        // In real implementation: HTTP call to Stripe API returns JSON response
        // This method includes the 10-second delay to simulate external API call
        String providerJsonResponse = simulateProviderApiCall(request);
        
        // Extract transaction_no from provider's JSON response
        String transactionNo = extractTransactionNo(providerJsonResponse);
        
        // Determine status from provider response
        PaymentStatus status = extractStatus(providerJsonResponse);
        
        PaymentResponseDTO response = PaymentResponseDTO.of(
            transactionNo,              // From external provider response
            status,
            request.amount(),
            request.paymentMethod(),
            request.description(),
            LocalDateTime.now(),
            PaymentProvider.STRIPE
        );
        
        log.info("Payment processed via STRIPE: transactionNo={}, status={}", 
                 transactionNo, status);
        return response;
    }
    // Stripe is synchronous and doesn't use webhooks for payment initiation
    
    /**
     * Simulates calling Stripe API and returning JSON response.
     * In real implementation, this would make an HTTP call to Stripe API.
     * 
     * Response format: {"id": "<uuidv4_str>", "status": "SUCCEED" (or "FAILED"), ...}
     */
    private String simulateProviderApiCall(PaymentRequestDTO request) {
        // Simulate external API call delay (10 seconds as mentioned in requirements)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException("Payment processing interrupted");
        }
        
        // Generate transaction number in Stripe format
        String transactionNo = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        
        // Simulate STRIPE JSON response format
        // Format: {"id": "<uuidv4_str>", "status": "SUCCEED" (or "FAILED"), ...}
        return String.format(
            "{\"id\":\"%s\",\"status\":\"SUCCEED\",\"amount\":%s}",
            transactionNo, request.amount()
        );
    }
    
    /**
     * Extracts transaction_no from Stripe JSON response.
     */
    private String extractTransactionNo(String jsonResponse) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            JsonNode idNode = jsonNode.get("id");
            if (idNode == null || !idNode.isTextual()) {
                throw new PaymentException("Invalid Stripe response: missing or invalid 'id' field");
            }
            return idNode.asText();
        } catch (Exception e) {
            throw new PaymentException("Failed to parse Stripe response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts payment status from Stripe JSON response.
     */
    private PaymentStatus extractStatus(String jsonResponse) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            JsonNode statusNode = jsonNode.get("status");
            if (statusNode == null || !statusNode.isTextual()) {
                return PaymentStatus.FAILED;
            }
            
            String status = statusNode.asText();
            return "SUCCEED".equalsIgnoreCase(status) 
                ? PaymentStatus.COMPLETED 
                : PaymentStatus.FAILED;
        } catch (Exception e) {
            log.warn("Failed to extract status from Stripe response, defaulting to FAILED", e);
            return PaymentStatus.FAILED;
        }
    }
}

