package com.tymex.payment.controller.v1;

import com.tymex.payment.dto.ErrorResponseDTO;
import com.tymex.payment.enums.ErrorCode;
import com.tymex.payment.enums.PaymentProvider;
import com.tymex.payment.service.provider.contract.WebhookCapablePaymentProviderStrategy;
import com.tymex.payment.service.provider.routing.PaymentProviderRouter;
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
    
    private final PaymentProviderRouter providerRouter;
    
    public WebhookController(PaymentProviderRouter providerRouter) {
        this.providerRouter = providerRouter;
    }
    
    /**
     * Receives webhook from MoMo payment provider.
     * 
     * Webhook format: {
     *   "transaction_id": "<provider_transaction_id>",
     *   "transaction_no": "<final_transaction_no>",
     *   "status": "SUCCEED" (or "FAILED"),
     *   ...
     * }
     * 
     * @param payload the webhook payload (JSON string)
     * @param request HTTP request (to read headers)
     * @return HTTP 200 OK if webhook processed successfully
     */
    @PostMapping("/momo")
    public ResponseEntity<?> handleMoMoWebhook(
            @RequestBody String payload,
            HttpServletRequest request) {
        try {
            // Extract headers
            Map<String, String> headers = extractHeaders(request);
            
            log.info("Received MoMo webhook: payload length={}, headers={}", 
                    payload.length(), headers.keySet());
            
            // Route to webhook-capable provider (only async providers support webhooks)
            // Each provider implementation is responsible for extracting provider_transaction_id
            // from its own webhook payload format
            WebhookCapablePaymentProviderStrategy webhookHandler = providerRouter.routeWebhook(PaymentProvider.MOMO);
            
            // Process webhook - provider will extract idempotency key internally
            webhookHandler.handleWebhook(payload, headers);
            
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("MoMo webhook validation failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponseDTO.of(
                            ErrorCode.BAD_REQUEST,
                            "Invalid webhook payload: " + e.getMessage()
                    ));
                    
        } catch (Exception e) {
            log.error("Error processing MoMo webhook", e);
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

