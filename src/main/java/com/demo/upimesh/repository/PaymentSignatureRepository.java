package com.demo.upimesh.repository;

import com.demo.upimesh.model.PaymentSignature;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentSignatureRepository extends JpaRepository<PaymentSignature, Long> {
    Optional<PaymentSignature> findByPayment_Id(Long paymentId);
    List<PaymentSignature> findTop20ByOrderByVerifiedAtDescIdDesc();
    List<PaymentSignature> findTop20ByVerificationStatusOrderByVerifiedAtDescIdDesc(
            PaymentSignature.VerificationStatus status);
    long countByVerificationStatus(PaymentSignature.VerificationStatus status);
    long countByFailureCode(String failureCode);
    @org.springframework.data.jpa.repository.Query("select count(s) from PaymentSignature s where s.failureCode like '%DEVICE%' or s.failureCode like '%KEY%'")
    long countDeviceAuthorizationFailures();
}
