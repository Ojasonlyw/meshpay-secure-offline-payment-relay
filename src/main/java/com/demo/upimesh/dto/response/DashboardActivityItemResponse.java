package com.demo.upimesh.dto.response;

import java.time.Instant;

public record DashboardActivityItemResponse(String type, String title, String description,
        String status, Instant occurredAt, String referenceId) {}
