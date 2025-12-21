package com.tymex.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tymex.payment.dto.PaymentRequestDTO;
import com.tymex.payment.dto.PaymentResponseDTO;
import org.springframework.stereotype.Service;

/**
 * Service responsible for JSON serialization and deserialization of payment DTOs.
 * Single Responsibility: Handle JSON conversion operations.
 */
@Service
public class JsonSerializationService {
    
    private final ObjectMapper objectMapper;
    
    public JsonSerializationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public String serializeRequest(PaymentRequestDTO request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
    }
    
    public String serializeResponse(PaymentResponseDTO response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response", e);
        }
    }
    
    public PaymentResponseDTO deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentResponseDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize response", e);
        }
    }
}

