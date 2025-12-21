package com.tymex.payment.filter;

import com.tymex.payment.config.PaymentProperties;
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
    
    public ApiKeyAuthenticationFilter(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        
        String apiKey = request.getHeader(API_KEY_HEADER);
        String expectedKey = paymentProperties.getApi().getKey();
        
        if (apiKey == null || !apiKey.equals(expectedKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            String errorJson = String.format(
                "{\"error\":\"%s\",\"message\":\"Invalid or missing API key\"}",
                ErrorCode.UNAUTHORIZED.getCode()
            );
            response.getWriter().write(errorJson);
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}

