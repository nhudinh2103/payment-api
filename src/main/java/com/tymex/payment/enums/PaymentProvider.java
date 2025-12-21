package com.tymex.payment.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of payment provider names.
 * Represents the external payment providers supported by the system.
 * Supports case-insensitive deserialization from JSON.
 */
public enum PaymentProvider {
    STRIPE("STRIPE"),
    MOMO("MOMO");
    
    private final String name;
    
    PaymentProvider(String name) {
        this.name = name;
    }
    
    @JsonValue
    public String getName() {
        return name;
    }
    
    /**
     * Case-insensitive deserialization from JSON.
     * Accepts "stripe", "STRIPE", "Stripe", etc.
     */
    @JsonCreator
    public static PaymentProvider fromString(String value) {
        if (value == null) {
            return null;
        }
        // Case-insensitive matching
        String upperValue = value.toUpperCase();
        for (PaymentProvider provider : PaymentProvider.values()) {
            if (provider.name.equals(upperValue)) {
                return provider;
            }
        }
        throw new IllegalArgumentException(
            "Invalid payment provider: " + value + 
            ". Supported values: STRIPE, MOMO"
        );
    }
    
    @Override
    public String toString() {
        return name;
    }
}

