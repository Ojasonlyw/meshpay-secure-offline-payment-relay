package com.demo.upimesh.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DashboardCashflowResponse(String accountVpa, BigDecimal totalCreditVolume,
        BigDecimal totalDebitVolume, BigDecimal settledAmount, BigDecimal rejectedAmount,
        BigDecimal netMovement, List<DashboardMetricPointResponse> points) {}
