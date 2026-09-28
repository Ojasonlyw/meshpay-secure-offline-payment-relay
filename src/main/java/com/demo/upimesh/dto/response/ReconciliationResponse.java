package com.demo.upimesh.dto.response;

import java.time.Instant;
import java.util.List;

public record ReconciliationResponse(boolean balanced, Instant checkedAt,
        List<AccountReconciliationResponse> accounts) {}
