package com.tymex.payment.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of payment status values.
 * Represents the status of a payment transaction (e.g., completed, failed, pending).
 */
public enum PaymentStatus {
    COMPLETED("completed"),
    FAILED("failed"),
    PENDING("pending");
    
    private final String value;
    
    PaymentStatus(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return value;
    }
}

