package com.tymex.payment.service.provider.routing;

import com.tymex.payment.enums.PaymentProvider;
import com.tymex.payment.service.provider.contract.PaymentProviderStrategy;
import com.tymex.payment.service.provider.contract.WebhookCapablePaymentProviderStrategy;
import com.tymex.payment.service.provider.MoMoPaymentProvider;
import com.tymex.payment.service.provider.StripePaymentProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Router for payment provider strategies.
 * Single Responsibility: Route PaymentProvider enum to appropriate PaymentProviderStrategy implementation.
 * 
 * Determines provider capabilities at construction time to avoid runtime type checking.
 */
@Service
public class PaymentProviderRouter {
    
    private final Map<PaymentProvider, PaymentProviderStrategy> strategies;
    private final Map<PaymentProvider, WebhookCapablePaymentProviderStrategy> webhookHandlers;
    
    public PaymentProviderRouter(StripePaymentProvider stripeProvider, MoMoPaymentProvider momoProvider) {
        // Build strategy map (all providers)
        this.strategies = Map.of(
            PaymentProvider.STRIPE, stripeProvider,
            PaymentProvider.MOMO, momoProvider
        );
        
        // Build webhook handler map (only webhook-capable providers)
        // Capability is determined at construction time, not at runtime
        this.webhookHandlers = new HashMap<>();
        if (momoProvider instanceof WebhookCapablePaymentProviderStrategy) {
            webhookHandlers.put(PaymentProvider.MOMO, (WebhookCapablePaymentProviderStrategy) momoProvider);
        }
        // Stripe doesn't implement WebhookCapablePaymentProvider, so not added to map
    }
    
    /**
     * Routes payment provider enum to the appropriate strategy implementation.
     * 
     * @param provider the payment provider from PaymentRequestDTO
     * @return the payment provider strategy
     * @throws IllegalArgumentException if provider is not supported
     */
    public PaymentProviderStrategy route(PaymentProvider provider) {
        PaymentProviderStrategy strategy = strategies.get(provider);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported payment provider: " + provider);
        }
        return strategy;
    }
    
    /**
     * Routes payment provider enum to webhook-capable provider implementation.
     * Only returns providers that support webhooks (asynchronous providers).
     * 
     * Capability is determined at construction time, not at runtime.
     * 
     * @param provider the payment provider from webhook
     * @return the webhook-capable payment provider strategy
     * @throws IllegalArgumentException if provider is not supported or does not support webhooks
     */
    public WebhookCapablePaymentProviderStrategy routeWebhook(PaymentProvider provider) {
        WebhookCapablePaymentProviderStrategy handler = webhookHandlers.get(provider);
        if (handler == null) {
            throw new IllegalArgumentException(
                "Provider " + provider + " does not support webhooks");
        }
        return handler;
    }
}

