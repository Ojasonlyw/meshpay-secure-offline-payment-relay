package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.Payment;
import com.demo.upimesh.model.LedgerEntry;
import com.demo.upimesh.repository.PaymentRepository;
import com.demo.upimesh.repository.LedgerEntryRepository;
import com.demo.upimesh.exception.DuplicatePaymentException;
import com.demo.upimesh.exception.PaymentNotFoundException;
import com.demo.upimesh.exception.AccountNotFoundException;
import com.demo.upimesh.exception.InvalidPacketException;
import com.demo.upimesh.exception.DomainException;
import com.demo.upimesh.exception.InsufficientBalanceException;
import com.demo.upimesh.exception.SettlementException;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Where the actual ledger update happens. Wrapped in a DB transaction so either
 * BOTH the debit and credit happen, or neither does.
 *
 * The @Version column on Account gives us optimistic locking — if two threads
 * somehow get past idempotency and both try to debit the same account, the
 * second one will fail with OptimisticLockException rather than corrupting
 * the balance. (In a demo the idempotency layer should always catch this first,
 * but defense in depth.)
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final PaymentRepository payments;
    private final LedgerEntryRepository ledger;

    public SettlementService(AccountRepository accounts, TransactionRepository transactions,
                             PaymentRepository payments, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.payments = payments;
        this.ledger = ledger;
    }

    // Insufficient funds is raised only after recording a rejection, before any balance changes.
    // Commit that audit record; every other failure must roll back the ledger transaction.
    @Transactional(noRollbackFor = InsufficientBalanceException.class)
    public Transaction settle(PaymentInstruction instruction, String packetHash,
                              String bridgeNodeId, int hopCount) {
        try {
            return settleWithinTransaction(instruction, packetHash, bridgeNodeId, hopCount);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new SettlementException("Settlement operation failed", e);
        }
    }

    private Transaction settleWithinTransaction(PaymentInstruction instruction, String packetHash,
                                               String bridgeNodeId, int hopCount) {
        Payment payment = payments.lockByPacketHash(packetHash).orElseThrow(PaymentNotFoundException::new);
        if (!PaymentLifecycleService.canSettle(payment)) throw new DuplicatePaymentException();
        payment.setStatus(Payment.Status.PROCESSING);
        payment.setFailureCode(null);
        payment.setFailureMessage(null);
        if (instruction == null || instruction.getAmount() == null
                || instruction.getAmount().signum() <= 0) {
            throw new InvalidPacketException("Amount must be positive");
        }

        Account sender = accounts.findById(payment.getSenderVpa())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Unknown sender VPA: " + instruction.getSenderVpa()));

        Account receiver = accounts.findById(payment.getReceiverVpa())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Unknown receiver VPA: " + instruction.getReceiverVpa()));

        BigDecimal amount = payment.getAmount();
        if (sender.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance: {} has ₹{}, tried to send ₹{}",
                    sender.getVpa(), sender.getBalance(), amount);
            payment.setStatus(Payment.Status.REJECTED);
            InsufficientBalanceException rejection = new InsufficientBalanceException();
            payment.setFailureCode(rejection.getErrorCode());
            payment.setFailureMessage(rejection.getPublicMessage());
            recordRejected(payment, instruction, packetHash, bridgeNodeId, hopCount);
            throw new InsufficientBalanceException();
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accounts.save(sender);
        accounts.save(receiver);

        Transaction tx = new Transaction();
        tx.setPayment(payment);
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(amount);
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.SETTLED);
        transactions.save(tx);
        ledger.save(new LedgerEntry(payment, tx, payment.getSenderVpa(),
                LedgerEntry.Direction.DEBIT, amount, tx.getSettledAt()));
        ledger.save(new LedgerEntry(payment, tx, payment.getReceiverVpa(),
                LedgerEntry.Direction.CREDIT, amount, tx.getSettledAt()));
        payment.setStatus(Payment.Status.SETTLED);
        payment.setSettledAt(tx.getSettledAt());

        log.info("SETTLED ₹{} from {} to {} (packetHash={}, bridge={}, hops={})",
                amount, sender.getVpa(), receiver.getVpa(),
                packetHash.substring(0, 12) + "...", bridgeNodeId, hopCount);

        return tx;
    }

    private Transaction recordRejected(Payment payment, PaymentInstruction instruction, String packetHash,
                                       String bridgeNodeId, int hopCount) {
        Transaction tx = new Transaction();
        tx.setPayment(payment);
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(instruction.getAmount());
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.REJECTED);
        return transactions.save(tx);
    }
}
