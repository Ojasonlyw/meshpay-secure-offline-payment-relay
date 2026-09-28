package com.demo.upimesh.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(@NotBlank String senderVpa, @NotBlank String receiverVpa,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        String pin, int ttl, String startDevice) {}
