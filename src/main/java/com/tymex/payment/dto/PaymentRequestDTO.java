package com.tymex.payment.dto;

import com.tymex.payment.enums.PaymentProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentRequestDTO(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    BigDecimal amount,
    
    @NotBlank(message = "Payment method is required")
    String paymentMethod,
    
    String description,
    
    @NotNull(message = "Payment provider is required")
    PaymentProvider paymentProvider
) {}
