package com.tymex.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tymex.payment.enums.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(
    ErrorCode error,
    String message,
    String idempotencyKey
) {
    /**
     * Factory method to create ErrorResponseDTO without idempotencyKey.
     * Useful when idempotencyKey is not applicable to the error.
     * 
     * @param error the error code
     * @param message the error message
     * @return ErrorResponseDTO with idempotencyKey set to null (will be excluded from JSON)
     */
    public static ErrorResponseDTO of(ErrorCode error, String message) {
        return new ErrorResponseDTO(error, message, null);
    }
    
    /**
     * Factory method to create ErrorResponseDTO with idempotencyKey.
     * 
     * @param error the error code
     * @param message the error message
     * @param idempotencyKey the idempotency key (if applicable)
     * @return ErrorResponseDTO with all fields
     */
    public static ErrorResponseDTO of(ErrorCode error, String message, String idempotencyKey) {
        return new ErrorResponseDTO(error, message, idempotencyKey);
    }
}
