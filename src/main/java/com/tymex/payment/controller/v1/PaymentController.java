package com.tymex.payment.controller.v1;

import com.tymex.payment.dto.ErrorResponseDTO;
import com.tymex.payment.dto.PaymentRequestDTO;
import com.tymex.payment.dto.PaymentResponseDTO;
import com.tymex.payment.enums.ErrorCode;
import com.tymex.payment.exception.IdempotencyKeyConflictException;
import com.tymex.payment.exception.PaymentException;
import com.tymex.payment.exception.RequestInProgressException;
import com.tymex.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/${payment.api-version:v1}/payments")
public class PaymentController {
    
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    
    private final PaymentService paymentService;
    
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
    
    @PostMapping
    public ResponseEntity<?> processPayment(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequestDTO request) {
        
        // Validate idempotency key presence
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDTO.of(
                    ErrorCode.BAD_REQUEST,
                    "Idempotency-Key header is required"
                ));
        }
        
        try {
            PaymentService.ProcessPaymentResult result = paymentService.processPayment(idempotencyKey, request);
            
            // Add metadata to response
            PaymentResponseDTO response = result.getResponse().withMetadata(
                idempotencyKey,
                result.isCached()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (IdempotencyKeyConflictException e) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseDTO.of(
                    ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    e.getMessage(),
                    idempotencyKey
                ));
                
        } catch (RequestInProgressException e) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseDTO.of(
                    ErrorCode.REQUEST_IN_PROGRESS,
                    e.getMessage(),
                    idempotencyKey
                ));
                
        } catch (PaymentException e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDTO.of(
                    ErrorCode.PAYMENT_FAILED,
                    e.getMessage(),
                    idempotencyKey
                ));
                
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDTO.of(
                    ErrorCode.BAD_REQUEST,
                    e.getMessage(),
                    idempotencyKey
                ));
        }
    }
}
