package com.tymex.payment.service;

import com.tymex.payment.dto.PaymentRequestDTO;
import com.tymex.payment.dto.PaymentResponseDTO;
import com.tymex.payment.entity.PaymentRequest;
import com.tymex.payment.enums.ErrorCode;
import com.tymex.payment.enums.PaymentStatus;
import com.tymex.payment.exception.IdempotencyKeyConflictException;
import com.tymex.payment.exception.PaymentException;
import com.tymex.payment.exception.RequestInProgressException;
import com.tymex.payment.repository.PaymentRequestRepository;
import com.tymex.payment.service.provider.ExternalPaymentProvider;
import com.tymex.payment.util.IdempotencyKeyValidator;
import com.tymex.payment.util.RetryUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRequestRepository repository;
    private final ExternalPaymentProvider externalPaymentProvider;
    private final JsonSerializationService jsonSerializationService;

    public PaymentService(PaymentRequestRepository repository,
            ExternalPaymentProvider externalPaymentProvider,
            JsonSerializationService jsonSerializationService) {
        this.repository = repository;
        this.externalPaymentProvider = externalPaymentProvider;
        this.jsonSerializationService = jsonSerializationService;
    }

    public static class ProcessPaymentResult {
        private final PaymentResponseDTO response;
        private final boolean cached;

        public ProcessPaymentResult(PaymentResponseDTO response, boolean cached) {
            this.response = response;
            this.cached = cached;
        }

        public PaymentResponseDTO getResponse() {
            return response;
        }

        public boolean isCached() {
            return cached;
        }
    }

    public class PaymentRequestResetInfo {
            private final PaymentRequest record;
            private final boolean isReset;

            public PaymentRequestResetInfo(PaymentRequest record, boolean isReset) {
                this.record = record;
                this.isReset = isReset;
            }

            public PaymentRequest getRecord() {
                return record;
            }

            public boolean isReset() {
                return isReset;
            }
        }

        public ProcessPaymentResult processPayment(String idempotencyKey, PaymentRequestDTO request) {
            // Validate idempotency key format (UUID v4)
            IdempotencyKeyValidator.validate(idempotencyKey);

            // Serialize request and calculate hash
            String requestBody = jsonSerializationService.serializeRequest(request);
            String requestHash = DigestUtils.sha256Hex(requestBody);

            // Transaction 1: Create PENDING record (SHORT - 10ms)
            PaymentRequest record = createPendingRecord(idempotencyKey, requestHash, requestBody, request);

            // Check if this is a cached response (record was already COMPLETED)
            boolean isCached = record.getProcessingStatus() == PaymentRequest.ProcessingStatus.COMPLETED;
            if (isCached) {
                PaymentResponseDTO cachedResponse = jsonSerializationService
                        .deserializeResponse(record.getResponseBody());
                return new ProcessPaymentResult(cachedResponse, true);
            }

            // NO TRANSACTION: Call external provider with retry logic (LONG - 10s for sync, immediate for async)
            PaymentResponseDTO response;
            try {
                // Execute payment with automatic retry (3 attempts with exponential backoff: 1s, 2s, 4s)
                response = RetryUtil.executeWithRetry(
                        () -> externalPaymentProvider.charge(request, idempotencyKey),
                        RetryUtil.DEFAULT_RETRY_ATTEMPT
                );

            } catch (Exception e) {
                // Handle payment failure - RetryUtil throws Exception, but we expect PaymentException
                PaymentException paymentException;
                if (e instanceof PaymentException) {
                    paymentException = (PaymentException) e;
                } else {
                    // Wrap unexpected exceptions as PaymentException
                    paymentException = new PaymentException("Payment processing failed: " + e.getMessage(), e);
                }
                
                // Gracefully handle payment failure - return error response instead of throwing
                // Transaction 2: Update to FAILED (SHORT - 10ms)
                PaymentResponseDTO errorResponse = createErrorResponse(paymentException, request);
                updateRecordFailed(record, errorResponse);
                return new ProcessPaymentResult(errorResponse, false);
            }

            // Transaction 3: Update record based on response status
            // For synchronous providers: Update to COMPLETED
            // For asynchronous providers (PENDING): Update to PROCESSING (keep processing, don't complete yet)
            if (response.status() == PaymentStatus.PENDING) {
                // Async provider - store provider transaction ID and keep as PROCESSING
                updateRecordPending(record, response);
            } else {
                // Sync provider - update to COMPLETED
                updateRecordCompleted(record, response);
            }

            return new ProcessPaymentResult(response, false);
        }

        @Transactional
        private PaymentRequest createPendingRecord(String idempotencyKey,
                String requestHash,
                String requestBody,
                PaymentRequestDTO request) {
            try {
                PaymentRequest record = new PaymentRequest();
                record.setIdempotencyKey(idempotencyKey);
                record.setProcessingStatus(PaymentRequest.ProcessingStatus.PROCESSING);
                record.setRequestHash(requestHash);
                record.setRequestBody(requestBody);
                record.setAmount(request.amount());
                record.setPaymentMethod(request.paymentMethod());
                record.setDescription(request.description());
                record.setPaymentProvider(request.paymentProvider());
                record.setExpiresAt(LocalDateTime.now().plusHours(24));

                repository.save(record); // INSERT
                log.debug("Created PENDING record for idempotency key: {}", idempotencyKey);
                return record;
                // COMMIT here - lock released in ~10ms!

            } catch (DataIntegrityViolationException e) {
                // Key already exists, handle existing record
                try {
                    return handleExistingRecord(idempotencyKey, requestHash, request);
                } catch (ObjectOptimisticLockingFailureException retryException) {
                    // All retries failed - fallback to read-only check
                    // Another thread might have updated the record to COMPLETED
                    log.info("handleExistingRecordReadOnly");
                    return handleExistingRecordReadOnly(idempotencyKey, requestHash);
                }
            }
        }

    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    private PaymentRequest handleExistingRecord(String idempotencyKey, 
                                                String requestHash, 
                                                PaymentRequestDTO request) {
        // First read: Check expiration and get initial state
        PaymentRequestResetInfo existingWithReset = readAndCheckExpiration(idempotencyKey, requestHash, request);
        PaymentRequest existing = existingWithReset.getRecord();
        
        // Validate request hash - must match regardless of expiration/reset status
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(
                "Idempotency key already used with different request body.",
                idempotencyKey
            );
        }

        // If record was reset (expired and updated), return immediately
        // The record is already in PROCESSING status with updated fields, ready for processing
        if (existingWithReset.isReset()) {
            return existing;
        }

        // Re-read to get fresh status after hash validation
        // Purpose: Catch concurrent status changes (e.g., PROCESSING → COMPLETED) that occurred
        // between the first read and this point. This avoids unnecessary optimistic lock exceptions
        // when another thread has already completed the payment.
        existingWithReset = readAndCheckExpiration(idempotencyKey, requestHash, request);
        existing = existingWithReset.getRecord();
        
        // Re-validate hash after re-read (defensive check - hash shouldn't change, but verify)
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(
                "Idempotency key already used with different request body.",
                idempotencyKey
            );
        }
        
        // If record was reset during re-read (expired between initial check and re-read), return it
        if (existingWithReset.isReset()) {
            return existing;
        }

        // Handle based on current status (using fresh data from re-read)
        switch (existing.getProcessingStatus()) {
            case PROCESSING:
                throw new RequestInProgressException(
                    "Payment is being processed. Please retry later.",
                    idempotencyKey
                );
                
            case COMPLETED:
                // Return cached response - payment already completed by another thread
                return existing;
                
            case FAILED:
                // Allow retry - reset to PROCESSING and process again
                existing.setProcessingStatus(PaymentRequest.ProcessingStatus.PROCESSING);
                existing.setRequestHash(requestHash);
                existing.setRequestBody(jsonSerializationService.serializeRequest(request));
                existing.setAmount(request.amount());
                existing.setPaymentMethod(request.paymentMethod());
                existing.setDescription(request.description());
                existing.setPaymentProvider(request.paymentProvider());
                existing.setExpiresAt(LocalDateTime.now().plusHours(24));
                repository.save(existing);
                return existing;
                
            default:
                throw new IllegalStateException("Unknown processing status: " + existing.getProcessingStatus());
        }
    }

        private PaymentRequestResetInfo readAndCheckExpiration(String idempotencyKey, String requestHash,
                PaymentRequestDTO request) {

            PaymentRequest existing = repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Record should exist but not found"));

            // Check expiration
            if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.debug("Existing record expired, resetting for new request for key: {}", idempotencyKey);
                // Instead of DELETE+INSERT (which causes recursion), UPDATE the existing record
                // This avoids recursion and race conditions
                existing.setProcessingStatus(PaymentRequest.ProcessingStatus.PROCESSING);
                existing.setRequestHash(requestHash);
                existing.setRequestBody(jsonSerializationService.serializeRequest(request));
                existing.setAmount(request.amount());
                existing.setPaymentMethod(request.paymentMethod());
                existing.setDescription(request.description());
                existing.setPaymentProvider(request.paymentProvider());
                existing.setExpiresAt(LocalDateTime.now().plusHours(24));
                // Clear previous response data
                existing.setResponseStatus(null);
                existing.setResponseBody(null);
                existing.setTransactionNo(null);
                existing.setPaymentProvider(null);
                existing.setPaymentStatus(null);
                repository.save(existing);

                return new PaymentRequestResetInfo(existing, true);
            }

            return new PaymentRequestResetInfo(existing, false);

        }

        /**
         * Fallback method: Read-only check when all retries fail due to optimistic
         * locking.
         * This handles the case where high contention prevents updates, but we can
         * still
         * return cached responses if the record is COMPLETED.
         * 
         * @param idempotencyKey the idempotency key
         * @param requestHash    the request hash to validate
         * @return PaymentRequest if status allows, otherwise throws appropriate
         *         exception
         */
        @Transactional(readOnly = true)
        private PaymentRequest handleExistingRecordReadOnly(String idempotencyKey, String requestHash) {
            PaymentRequest existing = repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Record should exist but not found"));

            // Validate request hash
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException(
                        "Idempotency key already used with different request body.",
                        idempotencyKey);
            }

            // Handle based on current status (read-only, no updates)
            switch (existing.getProcessingStatus()) {
                case PROCESSING:
                    throw new RequestInProgressException(
                            "Payment is being processed. Please retry later.",
                            idempotencyKey);

                case COMPLETED:
                    // Return cached response - requirement: return cached if duplicated key
                    return existing;

                case FAILED:
                    // Still FAILED after all retries - throw exception to indicate high contention
                    // Client can retry with same idempotency key
                    throw new RequestInProgressException(
                            "Payment processing is currently unavailable due to high contention. Please retry later.",
                            idempotencyKey);

                default:
                    throw new IllegalStateException("Unknown processing status: " + existing.getProcessingStatus());
            }
        }

        @Transactional
        private void updateRecordPending(PaymentRequest record, PaymentResponseDTO response) {
            // For async providers: Keep as PROCESSING, store provider transaction ID for webhook lookup
            record.setResponseBody(jsonSerializationService.serializeResponse(response));
            record.setResponseStatus(HttpStatus.ACCEPTED.value());  // 202 Accepted for async processing
            record.setProviderTransactionId(response.providerTransactionId());
            record.setPaymentProvider(response.paymentProvider());
            record.setPaymentStatus(response.status().getValue());
            // Keep processingStatus as PROCESSING (don't change to COMPLETED)
            repository.save(record); // UPDATE
            log.debug("Updated record for async payment (PENDING): idempotencyKey={}, providerTransactionId={}", 
                     record.getIdempotencyKey(), response.providerTransactionId());
        }

        @Transactional
        private void updateRecordCompleted(PaymentRequest record, PaymentResponseDTO response) {
            record.setProcessingStatus(PaymentRequest.ProcessingStatus.COMPLETED);
            record.setResponseBody(jsonSerializationService.serializeResponse(response));
            record.setResponseStatus(HttpStatus.OK.value());
            record.setTransactionNo(response.transactionNo());
            record.setProviderTransactionId(response.providerTransactionId());  // May be null for sync providers
            record.setPaymentProvider(response.paymentProvider());
            record.setPaymentStatus(response.status().getValue());
            repository.save(record); // UPDATE
            log.debug("Updated record to COMPLETED for idempotency key: {}", record.getIdempotencyKey());
            // COMMIT here - lock released in ~10ms!
        }

        /**
         * Creates an error response DTO for failed payments.
         */
        private PaymentResponseDTO createErrorResponse(PaymentException e, PaymentRequestDTO request) {
            return PaymentResponseDTO.failed(
                    request.amount(),
                    request.paymentMethod(),
                    request.description(),
                    LocalDateTime.now(),
                    request.paymentProvider(),
                    ErrorCode.PAYMENT_FAILED.getCode(),
                    e.getMessage()
            );
        }
        
        @Transactional
        private void updateRecordFailed(PaymentRequest record, PaymentResponseDTO errorResponse) {
            record.setProcessingStatus(PaymentRequest.ProcessingStatus.FAILED);
            record.setResponseStatus(HttpStatus.OK.value()); // 200 OK (gracefully handled)
            record.setResponseBody(jsonSerializationService.serializeResponse(errorResponse));
            record.setPaymentStatus(PaymentStatus.FAILED.getValue());
            repository.save(record); // UPDATE
            log.debug("Updated record to FAILED for idempotency key: {}", record.getIdempotencyKey());
            // COMMIT here - lock released in ~10ms!
        }

        public PaymentResponseDTO getCachedResponse(String idempotencyKey) {
            PaymentRequest record = repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalArgumentException("Idempotency key not found"));

            if (record.getProcessingStatus() != PaymentRequest.ProcessingStatus.COMPLETED) {
                throw new IllegalStateException("Record is not in COMPLETED status");
            }

            return jsonSerializationService.deserializeResponse(record.getResponseBody());
        }

        /**
         * Processes webhook callback with idempotency logic (similar to processPayment).
         * Used for asynchronous payment providers (e.g., MoMo) that send webhooks.
         * 
         * Webhook idempotency flow (similar to processPayment):
         * 1. Look up payment by provider_transaction_id to get the actual idempotency_key
         * 2. Calculate webhook payload hash
         * 3. Check for existing record by idempotency_key
         * 4. Validate webhook hash (if record exists, ensure it matches)
         * 5. Handle duplicate webhooks (if already COMPLETED/FAILED, return early)
         * 6. Update payment status if still in PROCESSING
         * 
         * @param providerTransactionId the provider's transaction ID (used as idempotency key for webhooks)
         * @param webhookPayload the webhook payload (JSON string) for hash validation
         * @param transactionNo the final transaction number (from webhook, may be null if failed)
         * @param status the payment status (COMPLETED or FAILED)
         * @throws IllegalArgumentException if payment not found
         */
        @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
        )
        @Transactional
        public void processWebhook(String providerTransactionId, String webhookPayload, 
                                   String transactionNo, PaymentStatus status) {
            // Step 1: Look up payment by provider_transaction_id to get the actual idempotency_key
            PaymentRequest record = repository.findByProviderTransactionId(providerTransactionId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Payment not found for providerTransactionId: " + providerTransactionId));
            
            String idempotencyKey = record.getIdempotencyKey();
            
            // Step 2: Calculate webhook payload hash (for potential future validation)
            // Note: Currently we rely on status check for idempotency, but hash is calculated
            // for consistency with processPayment() pattern
            String webhookHash = DigestUtils.sha256Hex(webhookPayload);
            log.debug("Webhook hash calculated: idempotencyKey={}, hash={}", idempotencyKey, webhookHash);
            
            // Step 3 & 4: Check for existing record and validate duplicate webhooks
            // For webhooks, we validate that the payment is in PROCESSING status
            // If already COMPLETED/FAILED, this is a duplicate webhook
            if (record.getProcessingStatus() == PaymentRequest.ProcessingStatus.COMPLETED) {
                log.info("Webhook ignored - payment already completed: idempotencyKey={}, providerTransactionId={}",
                        idempotencyKey, providerTransactionId);
                return;  // Already processed, ignore duplicate webhook
            }
            
            if (record.getProcessingStatus() == PaymentRequest.ProcessingStatus.FAILED) {
                log.info("Webhook ignored - payment already failed: idempotencyKey={}, providerTransactionId={}",
                        idempotencyKey, providerTransactionId);
                return;  // Already processed, ignore duplicate webhook
            }
            
            // Step 5: Validate that payment is in PROCESSING status (expected for async payments)
            if (record.getProcessingStatus() != PaymentRequest.ProcessingStatus.PROCESSING) {
                log.warn("Webhook ignored - payment not in PROCESSING status: idempotencyKey={}, currentStatus={}, providerTransactionId={}",
                        idempotencyKey, record.getProcessingStatus(), providerTransactionId);
                return;
            }
            
            // Step 6: Update payment status
            if (status == PaymentStatus.COMPLETED) {
                record.setProcessingStatus(PaymentRequest.ProcessingStatus.COMPLETED);
                record.setTransactionNo(transactionNo);
                record.setPaymentStatus(PaymentStatus.COMPLETED.getValue());
                record.setResponseStatus(HttpStatus.OK.value());
                
                // Update response body with final status
                PaymentResponseDTO finalResponse = PaymentResponseDTO.of(
                        transactionNo,
                        PaymentStatus.COMPLETED,
                        record.getAmount(),
                        record.getPaymentMethod(),
                        record.getDescription(),
                        LocalDateTime.now(),
                        record.getPaymentProvider()
                );
                record.setResponseBody(jsonSerializationService.serializeResponse(finalResponse));
                
                log.info("Webhook: Payment completed - idempotencyKey={}, transactionNo={}, providerTransactionId={}",
                        idempotencyKey, transactionNo, providerTransactionId);
            } else if (status == PaymentStatus.FAILED) {
                record.setProcessingStatus(PaymentRequest.ProcessingStatus.FAILED);
                record.setPaymentStatus(PaymentStatus.FAILED.getValue());
                record.setResponseStatus(HttpStatus.OK.value()); // 200 OK (gracefully handled)
                
                // Update response body with failure status
                PaymentResponseDTO failedResponse = PaymentResponseDTO.failed(
                        record.getAmount(),
                        record.getPaymentMethod(),
                        record.getDescription(),
                        LocalDateTime.now(),
                        record.getPaymentProvider(),
                        ErrorCode.PAYMENT_FAILED.getCode(),
                        "Payment processing failed"
                );
                record.setResponseBody(jsonSerializationService.serializeResponse(failedResponse));
                
                log.info("Webhook: Payment failed - idempotencyKey={}, providerTransactionId={}",
                        idempotencyKey, providerTransactionId);
            } else {
                log.warn("Webhook: Unexpected status {} for providerTransactionId={}", status, providerTransactionId);
                return;
            }

            repository.save(record);
            log.debug("Webhook update completed for providerTransactionId={}", providerTransactionId);
        }
        
        /**
         * @deprecated Use processWebhook() instead. This method is kept for backward compatibility.
         * Updates payment status from webhook callback (legacy method without idempotency hash validation).
         */
        @Deprecated
        @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
        )
        @Transactional
        public void updatePaymentFromWebhook(String providerTransactionId, String transactionNo, PaymentStatus status) {
            // Delegate to processWebhook with empty payload (no hash validation)
            processWebhook(providerTransactionId, "", transactionNo, status);
        }
}