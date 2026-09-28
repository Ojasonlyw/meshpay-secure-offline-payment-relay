package com.demo.upimesh.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DashboardTransactionVolumeResponse(long settledCount, long rejectedCount,
        long failedCount, BigDecimal settledAmount, List<DashboardMetricPointResponse> points) {}
