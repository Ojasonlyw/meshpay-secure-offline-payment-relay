package com.demo.upimesh.dto.request;

import com.demo.upimesh.model.MeshConnection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MeshConnectionRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$") String sourceDeviceId,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$") String targetDeviceId,
        MeshConnection.Status status, @Size(max = 32) String linkType) {}
