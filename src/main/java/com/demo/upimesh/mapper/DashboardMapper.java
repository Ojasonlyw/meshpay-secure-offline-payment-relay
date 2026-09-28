package com.demo.upimesh.mapper;

import com.demo.upimesh.dto.response.DashboardSummaryResponse;
import com.demo.upimesh.model.Payment;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DashboardMapper {
    public DashboardSummaryResponse toResponse(long accountCount, long transactionCount,
            long settled, long rejected, BigDecimal volume, Map<String, Long> counts,
            com.demo.upimesh.dto.response.ReconciliationResponse reconciliation,
            BigDecimal balance, long activeDevices, long bridgeDevices, long offlineDevices,
            long activeConnections, double averageHopCount, long cacheSize) {
        long pending = counts.get(Payment.Status.PENDING.name()) + counts.get(Payment.Status.PROCESSING.name());
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long terminal = total - pending;
        return new DashboardSummaryResponse(accountCount, transactionCount, pending, settled,
                rejected, volume, total, Map.copyOf(counts), reconciliation.accounts().size(),
                reconciliation.accounts().stream().filter(account -> !account.balanced()).count(),
                reconciliation.balanced(), balance, counts.get("SETTLED"), counts.get("REJECTED"),
                pending, counts.get("FAILED"), counts.get("EXPIRED"), activeDevices,
                bridgeDevices, offlineDevices, activeConnections, averageHopCount,
                terminal == 0 ? 0 : 100.0 * counts.get("SETTLED") / terminal, cacheSize);
    }
}
