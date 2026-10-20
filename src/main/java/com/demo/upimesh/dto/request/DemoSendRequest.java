package com.demo.upimesh.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DemoSendRequest(@NotBlank @Size(max = 255) String senderVpa, @NotBlank @Size(max = 255) String receiverVpa,
        @NotNull @DecimalMin(value = "0.00", inclusive = false)
        @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2)
        @JsonDeserialize(using = DecimalAmountDeserializer.class) BigDecimal amount,
        @Size(max = 64) String pin, @Min(0) @Max(10) Integer ttl, @Size(max = 64) String startDevice) {}
