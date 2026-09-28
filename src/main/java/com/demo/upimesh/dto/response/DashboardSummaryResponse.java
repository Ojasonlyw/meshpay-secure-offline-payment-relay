package com.demo.upimesh.dto.response;

import java.math.BigDecimal;

public record DashboardSummaryResponse(long totalAccounts, long totalTransactions,
        long pendingTransactions, long settledTransactions, long failedTransactions,
        BigDecimal totalVolume, long totalPayments, java.util.Map<String, Long> paymentStatusCounts,
        long reconciliationAccountsChecked, long reconciliationMismatchedAccounts,
        boolean reconciliationBalanced, BigDecimal totalBalance, long settledPayments,
        long rejectedPayments, long pendingPayments, long failedPayments, long expiredPayments,
        long activeDevices, long bridgeDevices, long offlineDevices, long activeConnections,
        double averageHopCount, double settlementSuccessRate, long idempotencyCacheSize) {}
