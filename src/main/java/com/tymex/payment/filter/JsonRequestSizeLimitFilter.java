package com.tymex.payment.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tymex.payment.dto.ErrorResponseDTO;
import com.tymex.payment.enums.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to limit JSON request body size.
 * Protects against large payload attacks (DoS) for JSON APIs.
 * 
 * Note: Tomcat's max-http-form-post-size only applies to form data,
 * not JSON bodies. This filter provides protection for application/json requests.
 * 
 * Configuration:
 * - payment.security.max-json-size: Maximum allowed JSON body size (default: 10KB)
 *   Supports human-readable formats: 10KB, 1MB, etc.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonRequestSizeLimitFilter extends OncePerRequestFilter {

    private final DataSize maxJsonSize;
    private final ObjectMapper objectMapper;

    public JsonRequestSizeLimitFilter(
            @Value("${payment.security.max-json-size:10KB}") DataSize maxJsonSize,
            ObjectMapper objectMapper) {
        this.maxJsonSize = maxJsonSize;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Only check JSON requests
        if (isApplicationJson(request)) {
            long contentLength = request.getContentLengthLong();

            // Reject if Content-Length exceeds limit
            if (contentLength > maxJsonSize.toBytes()) {
                sendPayloadTooLargeError(response, 
                    String.format("Request body exceeds %s limit", maxJsonSize));
                return;
            }

            // Reject if Content-Length is missing (chunked encoding)
            // This is a strict security measure to prevent bypass attacks
            if (contentLength == -1) {
                sendPayloadTooLargeError(response, 
                    "Content-Length header is required for JSON requests");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isApplicationJson(HttpServletRequest request) {
        String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(contentType)
            );
        } catch (Exception e) {
            return false; // Invalid Content-Type header
        }
    }

    private void sendPayloadTooLargeError(HttpServletResponse response, String message) 
            throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE); // 413
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
            ErrorCode.PAYLOAD_TOO_LARGE, 
            message
        );
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
