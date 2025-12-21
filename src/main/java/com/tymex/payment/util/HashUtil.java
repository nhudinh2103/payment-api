package com.tymex.payment.util;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * Utility class for hash calculation operations.
 * Single Responsibility: Generate SHA-256 hashes for request validation.
 * 
 * This is a utility class with static methods - no Spring management needed.
 * Uses Apache Commons Codec DigestUtils for SHA-256 hashing (supports Java 8+).
 */
public final class HashUtil {
    
    // Prevent instantiation
    private HashUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Calculates SHA-256 hash of the given JSON string.
     * 
     * @param json the JSON string to hash
     * @return hexadecimal representation of the SHA-256 hash
     */
    public static String calculateSha256(String json) {
        return DigestUtils.sha256Hex(json);
    }
}

