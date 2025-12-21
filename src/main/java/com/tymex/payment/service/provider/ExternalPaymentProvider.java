package com.tymex.payment.service.provider;

import com.tymex.payment.dto.PaymentRequestDTO;
import com.tymex.payment.dto.PaymentResponseDTO;
import com.tymex.payment.enums.PaymentProvider;
import com.tymex.payment.service.provider.contract.PaymentProviderStrategy;
import com.tymex.payment.service.provider.routing.PaymentProviderRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Facade for external payment providers.
 * Single Responsibility: Orchestrate payment processing flow.
 * Delegates routing to PaymentProviderRouter and execution to PaymentProviderStrategy.
 * Uses Strategy Pattern to support both synchronous (Stripe) and asynchronous (MoMo) providers.
 */
@Service
public class ExternalPaymentProvider {
    
    private static final Logger log = LoggerFactory.getLogger(ExternalPaymentProvider.class);
    private final PaymentProviderRouter router;
    
    public ExternalPaymentProvider(PaymentProviderRouter router) {
        this.router = router;
    }
    
    /**
     * Charges payment using the appropriate provider strategy.
     * 
     * For synchronous providers (Stripe): Waits for response and returns final result.
     * For asynchronous providers (MoMo): Returns immediately with PENDING status.
     * 
     * @param request the payment request (contains paymentProvider field)
     * @param idempotencyKey the idempotency key for this payment
     * @return PaymentResponseDTO with payment result
     */
    public PaymentResponseDTO charge(PaymentRequestDTO request, String idempotencyKey) {
        PaymentProvider provider = request.paymentProvider();
        
        log.info("Processing payment via {}: amount={}, method={}, idempotencyKey={}", 
                 provider, request.amount(), request.paymentMethod(), idempotencyKey);
        
        // Route to appropriate provider strategy
        PaymentProviderStrategy strategy = router.route(provider);
        
        // Log whether provider is synchronous or asynchronous
        if (strategy.isSynchronous()) {
            log.debug("Using synchronous flow for provider: {}", provider);
        } else {
            log.debug("Using asynchronous flow for provider: {}", provider);
        }
        
        // Delegate to provider strategy
        PaymentResponseDTO response = strategy.initiatePayment(request, idempotencyKey);
        
        log.info("Payment processed via {}: status={}, transactionNo={}", 
                 provider, response.status(), response.transactionNo());
        return response;
    }
}

