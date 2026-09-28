package com.demo.upimesh.dto.response;

import java.math.BigDecimal;

public record DashboardMetricPointResponse(String bucket, BigDecimal credits, BigDecimal debits,
        BigDecimal netMovement, long settledCount, long rejectedCount, long failedCount,
        BigDecimal settledAmount) {}
