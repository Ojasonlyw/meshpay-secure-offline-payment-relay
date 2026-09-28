package com.demo.upimesh.repository;

import com.demo.upimesh.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findTop20ByOrderByIdDesc();
    boolean existsByPacketHash(String packetHash);
    long countByStatus(Transaction.Status status);
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") Transaction.Status status);
    @Query("select t.id from Transaction t where t.payment.id = :paymentId")
    Optional<Long> findIdByPaymentId(@Param("paymentId") Long paymentId);
}
