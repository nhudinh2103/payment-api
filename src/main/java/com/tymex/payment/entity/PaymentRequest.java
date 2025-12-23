package com.tymex.payment.entity;

import com.tymex.payment.enums.PaymentProvider;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_requests", 
       uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
public class PaymentRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36)
    private String idempotencyKey;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    
    @Column(name = "processing_status", length = 20)
    @Enumerated(EnumType.STRING)
    private ProcessingStatus processingStatus;
    
    @Column(name = "response_status")
    private Integer responseStatus;
    
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;
    
    @Column(name = "transaction_no", length = 36)
    private String transactionNo;
    
    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;
    
    @Column(name = "payment_provider", length = 50)
    @Enumerated(EnumType.STRING)
    private PaymentProvider paymentProvider;
    
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;
    
    @Column(name = "description", length = 255)
    private String description;
    
    @Column(name = "payment_status", length = 20)
    private String paymentStatus;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (expiresAt == null) {
            expiresAt = now.plusHours(24);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum ProcessingStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
    
    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }
    
    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }
    
    public Integer getResponseStatus() {
        return responseStatus;
    }
    
    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }
    
    public String getResponseBody() {
        return responseBody;
    }
    
    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }
    
    public String getTransactionNo() {
        return transactionNo;
    }
    
    public void setTransactionNo(String transactionNo) {
        this.transactionNo = transactionNo;
    }
    
    public String getProviderTransactionId() {
        return providerTransactionId;
    }
    
    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }
    
    public PaymentProvider getPaymentProvider() {
        return paymentProvider;
    }
    
    public void setPaymentProvider(PaymentProvider paymentProvider) {
        this.paymentProvider = paymentProvider;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getPaymentStatus() {
        return paymentStatus;
    }
    
    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
