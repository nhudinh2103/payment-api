package com.tymex.payment.exception;

public class RequestInProgressException extends RuntimeException {
    private final String idempotencyKey;
    
    public RequestInProgressException(String message, String idempotencyKey) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}

