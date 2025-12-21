package com.tymex.payment.util;

import java.util.UUID;

/**
 * Utility class for idempotency key validation.
 * Single Responsibility: Validate idempotency key format (UUID v4).
 * 
 * This is a utility class with static methods - no Spring management needed.
 */
public final class IdempotencyKeyValidator {
    
    // Prevent instantiation
    private IdempotencyKeyValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Validates that the idempotency key is a valid UUID v4 format.
     * 
     * @param key the idempotency key to validate
     * @throws IllegalArgumentException if the key is null, empty, or not a valid UUID v4
     */
    public static void validate(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or empty");
        }
        
        try {
            UUID uuid = UUID.fromString(key);
            if (uuid.version() != 4) {
                throw new IllegalArgumentException("Idempotency key must be UUID v4 format");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid idempotency key format. Must be UUID v4.", e);
        }
    }
}

