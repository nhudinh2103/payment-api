package com.tymex.payment.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Utility class for retry operations with exponential backoff.
 * Single Responsibility: Execute operations with automatic retry logic.
 * 
 * This is a utility class with static methods - no Spring management needed.
 */
public final class RetryUtil {
    
    private static final Logger log = LoggerFactory.getLogger(RetryUtil.class);
    public static final int DEFAULT_RETRY_ATTEMPT = 3;
    
    // Prevent instantiation
    private RetryUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Executes an operation with retry logic using exponential backoff (1s, 2s, 4s)
     * 
     * @param operation The operation to execute
     * @param maxAttempts Maximum number of attempts (use DEFAULT_RETRY_ATTEMPT or custom value)
     * @param <T> Return type
     * @return Result of the operation
     * @throws Exception Last exception if all retries fail
     */
    public static <T> T executeWithRetry(Supplier<T> operation, int maxAttempts) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
                
            } catch (Exception e) {
                lastException = e;
                
                // Retry if not last attempt
                if (attempt < maxAttempts) {
                    long backoffMs = calculateExponentialBackoff(attempt);
                    
                    log.warn("Operation failed (attempt {}/{}), retrying in {}ms. Error: {}", 
                            attempt, maxAttempts, backoffMs, e.getMessage());
                    
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                    
                    continue; // Retry
                } else {
                    // Final failure
                    log.error("Operation failed after {} attempts. Error: {}", 
                            attempt, e.getMessage());
                    throw e;
                }
            }
        }
        
        // All retries exhausted
        throw lastException != null ? lastException 
                : new RuntimeException("Operation failed after " + maxAttempts + " attempts");
    }
    
    /**
     * Calculates exponential backoff delay
     * Attempt 1: 1s (1000ms)
     * Attempt 2: 2s (2000ms)
     * Attempt 3: 4s (4000ms)
     */
    private static long calculateExponentialBackoff(int attempt) {
        // Exponential: 2^(attempt-1) seconds
        // Attempt 1: 2^0 = 1s
        // Attempt 2: 2^1 = 2s
        // Attempt 3: 2^2 = 4s
        return (long) Math.pow(2, attempt - 1) * 1000;
    }
}