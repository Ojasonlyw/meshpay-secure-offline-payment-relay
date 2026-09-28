package com.demo.upimesh.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceRegistrationRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$") String deviceId,
        @NotBlank @Size(max = 255) String userVpa,
        @NotBlank @Size(max = 255) String deviceName,
        @NotNull Boolean internetCapability,
        @NotBlank @Size(max = 1024) String publicKey,
        @NotBlank String algorithm) {}
