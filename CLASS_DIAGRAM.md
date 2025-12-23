# Payment API - Class Diagram

## Overview
This class diagram focuses on the key design patterns used in the Payment API system, particularly the **Strategy Pattern**, **Router Pattern**, and **Service Layer Pattern**.

## Class Diagram

```mermaid
classDiagram
    %% Service Layer
    class PaymentService {
        <<service>>
    }
    
    %% Strategy Pattern - Core Interface
    class PaymentProviderStrategy {
        <<interface>>
        +process(request, idempotencyKey) PaymentResponseDTO
    }
    
    class WebhookCapablePaymentProviderStrategy {
        <<interface>>
        +handleWebhook(payload, headers) WebhookResult
    }
    
    %% Strategy Pattern - Concrete Implementations
    class StripePaymentProvider {
        <<service>>
    }
    
    class MoMoPaymentProvider {
        <<service>>
    }
    
    %% Router Pattern
    class PaymentProviderRouter {
        <<service>>
        +route(provider) PaymentProviderStrategy
        +routeWebhook(provider) WebhookCapablePaymentProviderStrategy
    }
    
    %% Controllers (Presentation Layer)
    class WebhookController {
        <<controller>>
    }
    
    class PaymentController {
        <<controller>>
    }
    
    %% Relationships
    PaymentService --> PaymentProviderRouter : uses
    
    PaymentProviderRouter --> PaymentProviderStrategy : routes to
    PaymentProviderRouter --> WebhookCapablePaymentProviderStrategy : routes to
    
    PaymentProviderStrategy <|.. StripePaymentProvider : implements
    PaymentProviderStrategy <|.. MoMoPaymentProvider : implements
    WebhookCapablePaymentProviderStrategy <|.. MoMoPaymentProvider : implements
    
    PaymentController --> PaymentService : uses
    WebhookController --> PaymentService : uses
    
    %% Note: PaymentService uses strategies through PaymentProviderRouter
    %% Controllers delegate to PaymentService, which handles routing internally
```

