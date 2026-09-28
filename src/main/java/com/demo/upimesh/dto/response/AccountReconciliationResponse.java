package com.demo.upimesh.dto.response;

import java.math.BigDecimal;

public record AccountReconciliationResponse(String vpa, BigDecimal storedBalance,
        BigDecimal openingBalance, BigDecimal ledgerCredits, BigDecimal ledgerDebits,
        BigDecimal calculatedBalance, BigDecimal difference, boolean balanced) {}
