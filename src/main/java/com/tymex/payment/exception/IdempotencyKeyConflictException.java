package com.tymex.payment.exception;

public class IdempotencyKeyConflictException extends RuntimeException {
    private final String idempotencyKey;
    
    public IdempotencyKeyConflictException(String message, String idempotencyKey) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}

