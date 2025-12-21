package com.tymex.payment.service;

import com.tymex.payment.dto.PaymentRequestDTO;
import com.tymex.payment.dto.PaymentResponseDTO;
import com.tymex.payment.enums.PaymentStatus;
import com.tymex.payment.exception.PaymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mock external payment provider (e.g., Stripe, PayPal)
 * In real implementation, this would call actual payment gateway API
 */
@Service
public class ExternalPaymentProvider {
    
    private static final Logger log = LoggerFactory.getLogger(ExternalPaymentProvider.class);
    
    public PaymentResponseDTO charge(PaymentRequestDTO request) {
        log.info("Processing payment: amount={}, method={}", request.amount(), request.paymentMethod());
        
        // Simulate external API call delay (10 seconds as mentioned in requirements)
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException("Payment processing interrupted");
        }
        
        // Simulate payment processing
        // In real scenario, this would call actual payment gateway
        if (request.amount().compareTo(new BigDecimal("10000")) > 0) {
            throw new PaymentException("Payment amount exceeds limit");
        }
        
        // Simulate successful payment
        PaymentResponseDTO response = PaymentResponseDTO.of(
            UUID.randomUUID().toString(),  // transactionNo
            PaymentStatus.COMPLETED,       // status
            request.amount(),               // amount
            request.paymentMethod(),        // paymentMethod
            request.description(),          // description
            LocalDateTime.now()             // createdAt
        );
        
        log.info("Payment processed successfully: transactionNo={}", response.transactionNo());
        return response;
    }
}
