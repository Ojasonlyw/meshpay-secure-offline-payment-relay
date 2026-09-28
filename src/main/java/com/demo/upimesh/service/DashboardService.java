package com.demo.upimesh.service;

import com.demo.upimesh.dto.response.*;
import com.demo.upimesh.mapper.DashboardMapper;
import com.demo.upimesh.exception.AccountNotFoundException;
import com.demo.upimesh.model.*;
import com.demo.upimesh.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final PaymentRepository payments;
    private final LedgerEntryRepository ledger;
    private final DeviceRepository devices;
    private final MeshConnectionRepository connections;
    private final PacketRouteRepository routes;
    private final PaymentSignatureRepository signatures;
    private final ReconciliationService reconciliation;
    private final IdempotencyService idempotency;
    private final DashboardMapper mapper;

    public DashboardService(AccountRepository accounts, TransactionRepository transactions,
            PaymentRepository payments, LedgerEntryRepository ledger, DeviceRepository devices,
            MeshConnectionRepository connections, PacketRouteRepository routes,
            PaymentSignatureRepository signatures, ReconciliationService reconciliation,
            IdempotencyService idempotency, DashboardMapper mapper) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.payments = payments;
        this.ledger = ledger;
        this.devices = devices;
        this.connections = connections;
        this.routes = routes;
        this.signatures = signatures;
        this.reconciliation = reconciliation;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    public DashboardSummaryResponse summary() {
        var counts = new LinkedHashMap<String, Long>();
        for (Payment.Status status : Payment.Status.values()) counts.put(status.name(), 0L);
        payments.countStatuses().forEach(count -> counts.put(count.getStatus().name(), count.getTotal()));
        return mapper.toResponse(accounts.count(), transactions.count(),
                transactions.countByStatus(Transaction.Status.SETTLED),
                transactions.countByStatus(Transaction.Status.REJECTED),
                transactions.sumAmountByStatus(Transaction.Status.SETTLED), counts,
                reconciliation.reconcile(), accounts.totalBalance(),
                devices.countByStatus(Device.Status.ACTIVE),
                devices.countByStatusAndInternetCapabilityTrue(Device.Status.ACTIVE),
                devices.countByStatusAndInternetCapabilityFalse(Device.Status.ACTIVE),
                connections.countByStatus(MeshConnection.Status.ACTIVE),
                routes.averageHopCount(), idempotency.size());
    }

    public DashboardCashflowResponse cashflow(String accountVpa) {
        String vpa = accountVpa == null || accountVpa.isBlank() ? null : accountVpa;
        if (vpa != null && !accounts.existsById(vpa)) throw new AccountNotFoundException();
        BigDecimal credits = ledger.totalByDirection(LedgerEntry.Direction.CREDIT, vpa);
        BigDecimal debits = ledger.totalByDirection(LedgerEntry.Direction.DEBIT, vpa);
        Map<String, LedgerEntryRepository.CashflowBucket> buckets = new LinkedHashMap<>();
        ledger.cashflowBuckets(vpa, daysAgo(14)).forEach(row -> buckets.put(row.getBucket(), row));
        List<DashboardMetricPointResponse> points = new ArrayList<>();
        for (int days = 14; days >= 0; days--) {
            String day = LocalDate.now(ZoneOffset.UTC).minusDays(days).toString();
            var row = buckets.get(day);
            BigDecimal credit = row == null ? BigDecimal.ZERO : row.getCredits();
            BigDecimal debit = row == null ? BigDecimal.ZERO : row.getDebits();
            points.add(new DashboardMetricPointResponse(day, credit, debit, credit.subtract(debit),
                    0, 0, 0, BigDecimal.ZERO));
        }
        return new DashboardCashflowResponse(vpa, credits, debits,
                payments.totalAmountByStatus(Payment.Status.SETTLED),
                payments.totalAmountByStatus(Payment.Status.REJECTED), credits.subtract(debits), points);
    }

    public DashboardActivityResponse activity() {
        List<DashboardActivityItemResponse> items = new ArrayList<>();
        for (Payment p : payments.findTop20ByOrderByReceivedAtDescIdDesc())
            items.add(new DashboardActivityItemResponse("PAYMENT", "Payment " + p.getStatus(),
                    p.getSenderVpa() + " → " + p.getReceiverVpa(), p.getStatus().name(),
                    p.getReceivedAt(), p.getPaymentId().toString()));
        for (Transaction t : transactions.findTop20ByOrderByIdDesc())
            items.add(new DashboardActivityItemResponse("TRANSACTION", "Transaction " + t.getStatus(),
                    t.getSenderVpa() + " → " + t.getReceiverVpa(), t.getStatus().name(),
                    t.getSettledAt(), t.getPayment().getPaymentId().toString()));
        for (LedgerEntry entry : ledger.findTop20ByOrderByCreatedAtDescIdDesc())
            items.add(new DashboardActivityItemResponse("LEDGER", entry.getDirection().name() + " entry",
                    entry.getAccountVpa() + " · " + entry.getAmount(), entry.getDirection().name(),
                    entry.getCreatedAt(), entry.getPayment().getPaymentId().toString()));
        for (PacketRoute route : routes.findTop100ByOrderByReceivedAtDescIdDesc().stream().limit(20).toList())
            items.add(new DashboardActivityItemResponse("ROUTE", "Packet routed",
                    route.getSourceDevice().getDeviceId() + " → " + route.getDestinationDevice().getDeviceId(),
                    "ROUTED", route.getReceivedAt(), route.getPacketId()));
        for (PaymentSignature signature : signatures.findTop20ByOrderByVerifiedAtDescIdDesc())
            items.add(new DashboardActivityItemResponse("SIGNATURE", "Signature " + signature.getVerificationStatus(),
                    signature.getFailureCode() == null ? "Device authorization verified" : signature.getFailureCode(),
                    signature.getVerificationStatus().name(), signature.getVerifiedAt(),
                    signature.getPayment().getPaymentId().toString()));
        items.sort(Comparator.comparing(DashboardActivityItemResponse::occurredAt).reversed());
        return new DashboardActivityResponse(items.stream().limit(40).toList());
    }

    public DashboardNetworkStatsResponse networkStats() {
        return new DashboardNetworkStatsResponse(devices.count(), devices.countByStatus(Device.Status.ACTIVE),
                devices.countByTrustStatus(Device.TrustStatus.TRUSTED),
                devices.countByStatus(Device.Status.REVOKED),
                devices.countByStatusAndInternetCapabilityTrue(Device.Status.ACTIVE),
                devices.countByStatusAndInternetCapabilityFalse(Device.Status.ACTIVE),
                connections.countByStatus(MeshConnection.Status.ACTIVE), routes.count(),
                routes.averageHopCount(), routes.latestReceivedAt(),
                routes.mostActiveBridges().stream().findFirst().orElse(null));
    }

    public DashboardTransactionVolumeResponse transactionVolume() {
        Map<String, PaymentRepository.VolumeBucket> buckets = new LinkedHashMap<>();
        payments.volumeBuckets(daysAgo(6)).forEach(row -> buckets.put(row.getBucket(), row));
        List<DashboardMetricPointResponse> points = new ArrayList<>();
        for (int days = 6; days >= 0; days--) {
            String day = LocalDate.now(ZoneOffset.UTC).minusDays(days).toString();
            var row = buckets.get(day);
            points.add(new DashboardMetricPointResponse(day, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, row == null ? 0 : row.getSettledCount(),
                    row == null ? 0 : row.getRejectedCount(), row == null ? 0 : row.getFailedCount(),
                    row == null ? BigDecimal.ZERO : row.getSettledAmount()));
        }
        return new DashboardTransactionVolumeResponse(
                points.stream().mapToLong(DashboardMetricPointResponse::settledCount).sum(),
                points.stream().mapToLong(DashboardMetricPointResponse::rejectedCount).sum(),
                points.stream().mapToLong(DashboardMetricPointResponse::failedCount).sum(),
                points.stream().map(DashboardMetricPointResponse::settledAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add), points);
    }

    public DashboardSecurityEventsResponse securityEvents() {
        var failed = signatures.findTop20ByVerificationStatusOrderByVerifiedAtDescIdDesc(
                PaymentSignature.VerificationStatus.FAILED);
        var items = failed.stream().map(s -> new DashboardSecurityEventItemResponse(
                s.getFailureCode() == null ? "SIGNATURE_FAILED" : s.getFailureCode(), "HIGH",
                s.getFailureCode() == null ? "Payment authorization failed"
                        : "Payment authorization failed: " + s.getFailureCode(),
                s.getDevice() == null ? null : s.getDevice().getDeviceId(),
                s.getPayment().getPaymentId().toString(), s.getVerifiedAt())).toList();
        return new DashboardSecurityEventsResponse(signatures.countByFailureCode("INVALID_SIGNATURE"),
                signatures.countDeviceAuthorizationFailures(),
                signatures.countByVerificationStatus(PaymentSignature.VerificationStatus.FAILED), items);
    }

    private Instant daysAgo(int days) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(days).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
