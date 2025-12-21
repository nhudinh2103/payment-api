package com.tymex.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {
    
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
    
    public static class Api {
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
