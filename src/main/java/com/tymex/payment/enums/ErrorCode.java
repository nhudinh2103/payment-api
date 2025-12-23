package com.tymex.payment.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of all error codes returned by the Payment API.
 * These codes are used in ErrorResponseDTO to identify the type of error.
 */
public enum ErrorCode {
    REQUEST_IN_PROGRESS("REQUEST_IN_PROGRESS"),
    IDEMPOTENCY_KEY_CONFLICT("IDEMPOTENCY_KEY_CONFLICT"),
    PAYMENT_FAILED("PAYMENT_FAILED"),
    BAD_REQUEST("BAD_REQUEST"),
    UNAUTHORIZED("UNAUTHORIZED"),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE");
    
    private final String code;
    
    ErrorCode(String code) {
        this.code = code;
    }
    
    @JsonValue
    public String getCode() {
        return code;
    }
    
    @Override
    public String toString() {
        return code;
    }
}
