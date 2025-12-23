package com.tymex.payment.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tymex.payment.config.PaymentProperties;
import com.tymex.payment.dto.ErrorResponseDTO;
import com.tymex.payment.enums.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private static final String API_KEY_HEADER = "X-API-Key";
    
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    
    public ApiKeyAuthenticationFilter(PaymentProperties paymentProperties, ObjectMapper objectMapper) {
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        
        String expectedKey = paymentProperties.getApi().getKey();

        
        // Reject all requests if API key is not configured (null, empty, blank, or unresolved placeholder)
        boolean isUnresolvedPlaceholder = expectedKey != null && expectedKey.startsWith("${") && expectedKey.endsWith("}");
        if (expectedKey == null || expectedKey.isBlank() || isUnresolvedPlaceholder) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
                ErrorCode.UNAUTHORIZED,
                "API key is not configured. Please set API_KEY environment variable."
            );
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        String apiKey = request.getHeader(API_KEY_HEADER);
        
        if (apiKey == null || !apiKey.equals(expectedKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
                ErrorCode.UNAUTHORIZED,
                "Invalid or missing API key"
            );
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}

