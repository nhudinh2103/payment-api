package com.tymex.payment.service;

import com.tymex.payment.dto.PaymentRequestDTO;
import com.tymex.payment.dto.PaymentResponseDTO;
import com.tymex.payment.entity.PaymentRequest;
import com.tymex.payment.enums.ErrorCode;
import com.tymex.payment.exception.IdempotencyKeyConflictException;
import com.tymex.payment.exception.PaymentException;
import com.tymex.payment.exception.RequestInProgressException;
import com.tymex.payment.repository.PaymentRequestRepository;
import com.tymex.payment.util.HashUtil;
import com.tymex.payment.util.IdempotencyKeyValidator;
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
            String requestHash = HashUtil.calculateSha256(requestBody);

            // Transaction 1: Create PENDING record (SHORT - 10ms)
            PaymentRequest record = createPendingRecord(idempotencyKey, requestHash, requestBody, request);

            // Check if this is a cached response (record was already COMPLETED)
            boolean isCached = record.getProcessingStatus() == PaymentRequest.ProcessingStatus.COMPLETED;
            if (isCached) {
                PaymentResponseDTO cachedResponse = jsonSerializationService
                        .deserializeResponse(record.getResponseBody());
                return new ProcessPaymentResult(cachedResponse, true);
            }

            // NO TRANSACTION: Call external provider (LONG - 10s)
            PaymentResponseDTO response;
            try {
                response = externalPaymentProvider.charge(request);

            } catch (PaymentException e) {
                // Transaction 2: Update to FAILED (SHORT - 10ms)
                updateRecordFailed(record, e);
                throw e;
            }

            // Transaction 3: Update to COMPLETED (SHORT - 10ms)
            updateRecordCompleted(record, response);

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

        PaymentRequestResetInfo existingWithReset = readAndCheckExpiration(idempotencyKey, requestHash, request);
        PaymentRequest existing = existingWithReset.getRecord();
        
        // Validate request hash (even if record was reset, we should validate)
        // This ensures we catch any hash mismatches before proceeding
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(
                "Idempotency key already used with different request body.",
                idempotencyKey
            );
        }

        // If record was reset (expired and updated), return it immediately to allow processing to continue
        // The record is already in PROCESSING status with updated fields
        if (existingWithReset.isReset()) {
            return existing;
        }

        // Re-read only if NOT reset (to get fresh data in case of concurrent updates)
        // This ensures we get the latest status, especially if another thread just completed
        // the payment between our initial read and this status check
        existingWithReset = readAndCheckExpiration(idempotencyKey, requestHash, request);
        existing = existingWithReset.getRecord();
        
        // If record was reset after re-read (expired between initial check and re-read), return it
        // This handles the case where record became expired between initial check and re-read
        if (existingWithReset.isReset()) {
            return existing;
        }
        
        // Handle based on status (using fresh data)
        switch (existing.getProcessingStatus()) {
            case PROCESSING:
                throw new RequestInProgressException(
                    "Payment is being processed. Please retry later.",
                    idempotencyKey
                );
                
            case COMPLETED:
                // Return cached response - no need to process again
                // The record is already COMPLETED, so we'll return it as-is
                return existing;
                
            case FAILED:
                // Allow retry - update to PROCESSING and process again
                existing.setProcessingStatus(PaymentRequest.ProcessingStatus.PROCESSING);
                existing.setRequestHash(requestHash);
                existing.setRequestBody(jsonSerializationService.serializeRequest(request));
                existing.setAmount(request.amount());
                existing.setPaymentMethod(request.paymentMethod());
                existing.setDescription(request.description());
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
                existing.setExpiresAt(LocalDateTime.now().plusHours(24));
                // Clear previous response data
                existing.setResponseStatus(null);
                existing.setResponseBody(null);
                existing.setTransactionNo(null);
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
        private void updateRecordCompleted(PaymentRequest record, PaymentResponseDTO response) {
            record.setProcessingStatus(PaymentRequest.ProcessingStatus.COMPLETED);
            record.setResponseBody(jsonSerializationService.serializeResponse(response));
            record.setResponseStatus(HttpStatus.OK.value());
            record.setTransactionNo(response.transactionNo());
            record.setPaymentStatus(response.status().getValue());
            repository.save(record); // UPDATE
            log.debug("Updated record to COMPLETED for idempotency key: {}", record.getIdempotencyKey());
            // COMMIT here - lock released in ~10ms!
        }

        @Transactional
        private void updateRecordFailed(PaymentRequest record, PaymentException e) {
            record.setProcessingStatus(PaymentRequest.ProcessingStatus.FAILED);
            record.setResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            // Create a simple error response JSON
            String errorResponse = String.format("{\"error\":\"%s\",\"message\":\"%s\"}",
                    ErrorCode.PAYMENT_FAILED.getCode(), e.getMessage());
            record.setResponseBody(errorResponse);
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
}
