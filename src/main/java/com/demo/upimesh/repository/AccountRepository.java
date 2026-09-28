package com.demo.upimesh.repository;

import com.demo.upimesh.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;

public interface AccountRepository extends JpaRepository<Account, String> {
    @Query("select coalesce(sum(a.balance), 0) from Account a")
    BigDecimal totalBalance();
}
