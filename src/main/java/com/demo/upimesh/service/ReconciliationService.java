package com.demo.upimesh.service;

import com.demo.upimesh.dto.response.AccountReconciliationResponse;
import com.demo.upimesh.dto.response.ReconciliationResponse;
import com.demo.upimesh.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {
    private final LedgerEntryRepository ledger;

    public ReconciliationService(LedgerEntryRepository ledger) {
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse reconcile() {
        var rows = ledger.reconcileAccounts();
        var accounts = new ArrayList<AccountReconciliationResponse>();
        for (var row : rows) {
            if (row.getVpa() == null) continue;
            BigDecimal calculated = row.getOpeningBalance().add(row.getLedgerCredits()).subtract(row.getLedgerDebits());
            BigDecimal difference = row.getStoredBalance().subtract(calculated);
            accounts.add(new AccountReconciliationResponse(row.getVpa(), row.getStoredBalance(),
                    row.getOpeningBalance(), row.getLedgerCredits(), row.getLedgerDebits(),
                    calculated, difference, difference.signum() == 0));
        }
        return new ReconciliationResponse(accounts.stream().allMatch(AccountReconciliationResponse::balanced),
                rows.get(0).getCheckedAt(), List.copyOf(accounts));
    }
}
