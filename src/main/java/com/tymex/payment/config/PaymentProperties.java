package com.tymex.payment.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentProperties.class);
    
    private Api api = new Api();
    private Idempotency idempotency = new Idempotency();
    private Debug debug = new Debug();
    private String apiVersion = "v1";
    
    public Api getApi() {
        return api;
    }
    
    public void setApi(Api api) {
        this.api = api;
    }
    
    public Idempotency getIdempotency() {
        return idempotency;
    }
    
    public void setIdempotency(Idempotency idempotency) {
        this.idempotency = idempotency;
    }
    
    public Debug getDebug() {
        return debug;
    }
    
    public void setDebug(Debug debug) {
        this.debug = debug;
    }
    
    public String getApiVersion() {
        return apiVersion;
    }
    
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
    
    @PostConstruct
    public void validateApiKey() {
        String apiKey = api.getKey();
        
        // Log the raw value for debugging (masked for security)
        if (apiKey != null && !apiKey.startsWith("${")) {
            String maskedKey = apiKey.length() > 4 
                ? apiKey.substring(0, 2) + "***" + apiKey.substring(apiKey.length() - 2)
                : "***";
            log.info("API key loaded successfully (masked: {})", maskedKey);
        } else {
            log.warn("API key value: {}", apiKey);
        }
        
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "API key is not configured. Please set API_KEY environment variable.\n" +
                "Example: export API_KEY=your-secret-key-here"
            );
        }
        // Check if Spring Boot failed to resolve the placeholder
        if (apiKey.startsWith("${") && apiKey.endsWith("}")) {
            throw new IllegalStateException(
                "API key placeholder was not resolved. Please ensure API_KEY environment variable is set.\n" +
                "Current value: " + apiKey + "\n" +
                "To set it in Linux: export API_KEY=your-secret-key-here\n" +
                "Then restart the application."
            );
        }
    }
    
    @Valid
    public static class Api {
        @NotBlank(message = "API key must be provided via API_KEY environment variable")
        private String key;
        
        public String getKey() {
            return key;
        }
        
        public void setKey(String key) {
            this.key = key;
        }
    }
    
    public static class Idempotency {
        private Integer ttlHours = 24;
        private Integer stuckThresholdMinutes = 5;
        private Integer cleanupIntervalMinutes = 5;
        
        public Integer getTtlHours() {
            return ttlHours;
        }
        
        public void setTtlHours(Integer ttlHours) {
            this.ttlHours = ttlHours;
        }
        
        public Integer getStuckThresholdMinutes() {
            return stuckThresholdMinutes;
        }
        
        public void setStuckThresholdMinutes(Integer stuckThresholdMinutes) {
            this.stuckThresholdMinutes = stuckThresholdMinutes;
        }
        
        public Integer getCleanupIntervalMinutes() {
            return cleanupIntervalMinutes;
        }
        
        public void setCleanupIntervalMinutes(Integer cleanupIntervalMinutes) {
            this.cleanupIntervalMinutes = cleanupIntervalMinutes;
        }
    }
    
    public static class Debug {
        private Boolean sqlLogging = false;
        
        public Boolean getSqlLogging() {
            return sqlLogging;
        }
        
        public void setSqlLogging(Boolean sqlLogging) {
            this.sqlLogging = sqlLogging;
        }
    }
}
