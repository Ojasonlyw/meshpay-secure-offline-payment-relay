package com.demo.upimesh.repository;

import com.demo.upimesh.model.LedgerEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByPaymentIdOrderById(Long paymentId);
    long countByPaymentId(Long paymentId);
    List<LedgerEntry> findTop20ByOrderByCreatedAtDescIdDesc();
    @Query("select coalesce(sum(l.amount), 0) from LedgerEntry l where l.direction = :direction and (:vpa is null or l.accountVpa = :vpa)")
    BigDecimal totalByDirection(@Param("direction") LedgerEntry.Direction direction, @Param("vpa") String vpa);
    @Query(value = "select to_char(created_at at time zone 'UTC', 'YYYY-MM-DD') as bucket, sum(case when direction='CREDIT' then amount else 0 end) as credits, sum(case when direction='DEBIT' then amount else 0 end) as debits from ledger_entries where (:vpa is null or account_vpa=:vpa) and created_at >= :since group by 1 order by 1", nativeQuery = true)
    List<CashflowBucket> cashflowBuckets(@Param("vpa") String vpa, @Param("since") Instant since);
    interface CashflowBucket { String getBucket(); BigDecimal getCredits(); BigDecimal getDebits(); }

    // One statement snapshot includes a timestamp row even when there are no accounts.
    @Query(value = """
            SELECT a.vpa, a.balance AS "storedBalance", a.opening_balance AS "openingBalance",
                   COALESCE(l.credits, 0) AS "ledgerCredits",
                   COALESCE(l.debits, 0) AS "ledgerDebits", stamp.checked_at AS "checkedAt"
            FROM (SELECT statement_timestamp() AS checked_at) stamp
            LEFT JOIN accounts a ON true
            LEFT JOIN (
                SELECT account_vpa,
                       sum(CASE WHEN direction='CREDIT' THEN amount ELSE 0 END) AS credits,
                       sum(CASE WHEN direction='DEBIT' THEN amount ELSE 0 END) AS debits
                FROM ledger_entries GROUP BY account_vpa
            ) l ON l.account_vpa=a.vpa
            ORDER BY a.vpa
            """, nativeQuery = true)
    List<ReconciliationRow> reconcileAccounts();

    interface ReconciliationRow {
        String getVpa();
        BigDecimal getStoredBalance();
        BigDecimal getOpeningBalance();
        BigDecimal getLedgerCredits();
        BigDecimal getLedgerDebits();
        Instant getCheckedAt();
    }
}
