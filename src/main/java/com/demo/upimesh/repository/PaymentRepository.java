package com.demo.upimesh.repository;

import com.demo.upimesh.model.Payment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentId(UUID paymentId);
    Optional<Payment> findByPacketHash(String packetHash);
    boolean existsByPacketHash(String packetHash);
    List<Payment> findTop20ByOrderByReceivedAtDescIdDesc();
    long countByStatus(Payment.Status status);
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
    BigDecimal totalAmountByStatus(@Param("status") Payment.Status status);
    @Query(value = "select to_char(received_at at time zone 'UTC', 'YYYY-MM-DD') as bucket, sum(case when status='SETTLED' then 1 else 0 end) as settled_count, sum(case when status='REJECTED' then 1 else 0 end) as rejected_count, sum(case when status='FAILED' then 1 else 0 end) as failed_count, sum(case when status='SETTLED' then amount else 0 end) as settled_amount from payments where received_at >= :since group by 1 order by 1", nativeQuery = true)
    List<VolumeBucket> volumeBuckets(@Param("since") Instant since);
    interface VolumeBucket { String getBucket(); long getSettledCount(); long getRejectedCount(); long getFailedCount(); BigDecimal getSettledAmount(); }
    long countByStatusAndReceivedAtGreaterThanEqualAndReceivedAtLessThan(
            Payment.Status status, Instant start, Instant end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.packetHash = :hash")
    Optional<Payment> lockByPacketHash(@Param("hash") String hash);

    @Query("select p.status as status, count(p) as total from Payment p group by p.status")
    List<StatusCount> countStatuses();
    interface StatusCount {
        Payment.Status getStatus();
        long getTotal();
    }
}
