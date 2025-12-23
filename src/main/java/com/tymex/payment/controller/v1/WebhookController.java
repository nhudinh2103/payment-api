package com.tymex.payment.controller.v1;

import com.tymex.payment.dto.ErrorResponseDTO;
import com.tymex.payment.enums.ErrorCode;
import com.tymex.payment.enums.PaymentProvider;
import com.tymex.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for receiving webhooks from payment providers.
 * Supports asynchronous payment providers (e.g., MoMo) that send webhooks
 * to notify about payment status changes.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    
    private final PaymentService paymentService;
    
    public WebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
    
    /**
     * Receives webhook from payment providers.
     * 
     * Webhook format varies by provider:
     * - MoMo: { "transaction_id": "...", "transaction_no": "...", "status": "SUCCEED" }
     * - Other providers may have different formats
     * 
     * Flow:
     * 1. Controller extracts provider from path parameter
     * 2. Delegates to PaymentService which handles routing, parsing, and processing
     * 
     * @param provider the payment provider (from path parameter)
     * @param payload the webhook payload (JSON string)
     * @param request HTTP request (to read headers)
     * @return HTTP 200 OK if webhook processed successfully
     */
    @PostMapping("/{provider}")
    public ResponseEntity<?> handleWebhook(
            @PathVariable String provider,
            @RequestBody String payload,
            HttpServletRequest request) {
        try {
            // Extract headers
            Map<String, String> headers = extractHeaders(request);
            
            // Parse provider enum from path parameter
            PaymentProvider paymentProvider = PaymentProvider.fromString(provider);
            
            log.info("Received webhook from {}: payload length={}, headers={}", 
                    paymentProvider, payload.length(), headers.keySet());
            
            // Delegate to PaymentService - service handles routing, parsing, and processing
            paymentService.processWebhook(paymentProvider, payload, headers);
            
            log.info("Webhook processed successfully: provider={}", paymentProvider);
            
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Webhook validation failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponseDTO.of(
                            ErrorCode.BAD_REQUEST,
                            "Invalid webhook: " + e.getMessage()
                    ));
                    
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponseDTO.of(
                            ErrorCode.PAYMENT_FAILED,
                            "Failed to process webhook: " + e.getMessage()
                    ));
        }
    }
    
    /**
     * Extracts all headers from HTTP request into a Map.
     * 
     * @param request the HTTP request
     * @return Map of header names to header values
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }
}

