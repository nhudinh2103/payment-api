package com.tymex.payment.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

/**
 * Configuration class to enable/disable SQL logging based on payment.debug.sql-logging flag.
 * This allows toggling detailed SQL logging without restarting the application.
 */
@Configuration
public class SqlLoggingConfig {
    
    private static final Logger log = LoggerFactory.getLogger(SqlLoggingConfig.class);
    
    private final PaymentProperties paymentProperties;
    
    public SqlLoggingConfig(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }
    
    @PostConstruct
    public void configureSqlLogging() {
        boolean sqlLoggingEnabled = Boolean.TRUE.equals(paymentProperties.getDebug().getSqlLogging());
        
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Configure Hibernate SQL statement logging
        ch.qos.logback.classic.Logger hibernateSqlLogger = loggerContext.getLogger("org.hibernate.SQL");
        hibernateSqlLogger.setLevel(sqlLoggingEnabled ? Level.DEBUG : Level.INFO);
        
        // Configure Hibernate SQL parameter binding logging (shows actual parameter values)
        ch.qos.logback.classic.Logger hibernateParamsLogger = loggerContext.getLogger("org.hibernate.orm.jdbc.bind");
        hibernateParamsLogger.setLevel(sqlLoggingEnabled ? Level.TRACE : Level.INFO);
        
        // Also set for older Hibernate versions (5.x compatibility)
        ch.qos.logback.classic.Logger hibernateTypeLogger = loggerContext.getLogger("org.hibernate.type.descriptor.sql.BasicBinder");
        hibernateTypeLogger.setLevel(sqlLoggingEnabled ? Level.TRACE : Level.INFO);
        
        if (sqlLoggingEnabled) {
            log.info("SQL logging enabled - All SQL statements and parameters will be logged");
        } else {
            log.debug("SQL logging disabled");
        }
    }
}

