package com.demo.upimesh;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.exception.*;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.Payment;
import com.demo.upimesh.repository.PaymentRepository;
import com.demo.upimesh.repository.LedgerEntryRepository;
import com.demo.upimesh.service.PaymentLifecycleService;
import com.demo.upimesh.service.SettlementService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.repository.TransactionRepository;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoService;
import com.demo.upimesh.service.IdempotencyService;
import com.demo.upimesh.service.DemoDeviceKeyService;
import com.demo.upimesh.service.MeshSimulatorService;
import com.demo.upimesh.service.MeshService;
import com.demo.upimesh.repository.PaymentSignatureRepository;
import com.demo.upimesh.repository.DeviceRepository;
import com.demo.upimesh.repository.PacketRouteRepository;
import com.demo.upimesh.model.Device;
import com.demo.upimesh.model.PaymentSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The killer test: simulates the "three bridges deliver at the same instant"
 * scenario the user explicitly cared about.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/reset-fixtures.sql")
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class IdempotencyConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private final StringRedisTemplate redis;
    private final DemoService demoService;
    private final DemoDeviceKeyService demoKeys;
    private final PaymentSignatureRepository signatureRecords;
    private final DeviceRepository devices;
    private final PacketRouteRepository routes;
    private final MeshSimulatorService mesh;
    private final MeshService meshService;
    private final BridgeIngestionService bridge;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final HybridCryptoService crypto;
    private final ServerKeyHolder serverKey;
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final PaymentRepository payments;
    private final LedgerEntryRepository ledger;
    private final PaymentLifecycleService lifecycle;
    private final SettlementService settlement;
    private final JdbcTemplate jdbc;
    private final com.demo.upimesh.service.ReconciliationService reconciliation;

    IdempotencyConcurrencyTest(StringRedisTemplate redis, DemoService demoService,
                              DemoDeviceKeyService demoKeys,
                              PaymentSignatureRepository signatureRecords, DeviceRepository devices,
                              PacketRouteRepository routes, MeshSimulatorService mesh, MeshService meshService,
                              BridgeIngestionService bridge, IdempotencyService idempotency,
                              AccountRepository accounts, TransactionRepository transactions,
                              HybridCryptoService crypto, ServerKeyHolder serverKey,
                              MockMvc mvc, ObjectMapper json, PaymentRepository payments,
                              LedgerEntryRepository ledger, PaymentLifecycleService lifecycle,
                              SettlementService settlement, JdbcTemplate jdbc,
                              com.demo.upimesh.service.ReconciliationService reconciliation) {
        this.redis = redis;
        this.demoService = demoService;
        this.demoKeys = demoKeys;
        this.signatureRecords = signatureRecords;
        this.devices = devices;
        this.routes = routes;
        this.mesh = mesh;
        this.meshService = meshService;
        this.bridge = bridge;
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.transactions = transactions;
        this.crypto = crypto;
        this.serverKey = serverKey;
        this.mvc = mvc;
        this.json = json;
        this.payments = payments;
        this.ledger = ledger;
        this.lifecycle = lifecycle;
        this.settlement = settlement;
        this.jdbc = jdbc;
        this.reconciliation = reconciliation;
    }

    @BeforeEach
    void clear() {
        idempotency.clear();
        mesh.resetMesh();
    }

    @Test
    void readApisUseMigratedDatabase() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("25.00"), "1234", 5);
        bridge.ingest(packet, "read-api-bridge", 1);
        Payment signedPayment = payments.findByPacketHash(crypto.hashCiphertext(packet.getCiphertext())).orElseThrow();
        assertEquals(PaymentSignature.VerificationStatus.VERIFIED,
                signatureRecords.findByPayment_Id(signedPayment.getId()).orElseThrow().getVerificationStatus());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senderVpa").value("alice@demo"))
                .andExpect(jsonPath("$[0].amount").value(25.00))
                .andExpect(jsonPath("$[0].status").value("SETTLED"));
    }

    @Test
    void claimsAreSharedBetweenServiceInstances() {
        IdempotencyService otherInstance = new IdempotencyService(redis, 86400);
        assertTrue(idempotency.claim("shared-packet"));
        assertFalse(otherInstance.claim("shared-packet"));
        Long ttl = redis.getExpire("upi:mesh:idempotency:shared-packet");
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 86400);
    }

    @Test
    void expiredClaimCanBeClaimedAgain() {
        IdempotencyService shortLived = new IdempotencyService(redis, 1);
        assertTrue(shortLived.claim("expiring-packet"));
        assertFalse(shortLived.claim("expiring-packet"));
        await().atMost(Duration.ofSeconds(5)).until(() ->
                !Boolean.TRUE.equals(redis.hasKey("upi:mesh:idempotency:expiring-packet")));
        assertTrue(shortLived.claim("expiring-packet"));
    }

    @Test
    void resetAndCountOnlyAffectIdempotencyNamespace() {
        String unrelatedKey = "upi:mesh:test:unrelated";
        redis.opsForValue().set(unrelatedKey, "keep", Duration.ofMinutes(1));
        try {
            assertTrue(idempotency.claim("first"));
            assertTrue(idempotency.claim("second"));
            assertEquals(2, idempotency.size());
            idempotency.clear();
            assertEquals(0, idempotency.size());
            assertEquals("keep", redis.opsForValue().get(unrelatedKey));
            assertTrue(idempotency.claim("first"));
        } finally {
            redis.delete(unrelatedKey);
        }
    }

    @Test
    void singlePacketDeliveredByThreeBridgesSettlesExactlyOnce() throws Exception {
        // Capture starting balances
        BigDecimal aliceBefore = accounts.findById("alice@demo").orElseThrow().getBalance();
        BigDecimal bobBefore = accounts.findById("bob@demo").orElseThrow().getBalance();

        // One packet, but we'll deliver it from 3 "bridges" simultaneously
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("100.00"), "1234", 5);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger settled = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();

        Future<?>[] futures = new Future[3];
        for (int i = 0; i < 3; i++) {
            final String node = "bridge-" + i;
            futures[i] = pool.submit(() -> {
                try {
                    start.await();
                    BridgeIngestionService.IngestResult r = bridge.ingest(packet, node, 3);
                    if ("SETTLED".equals(r.outcome())) settled.incrementAndGet();
                    else if ("DUPLICATE".equals(r.outcome())) duplicates.incrementAndGet();
                } catch (DuplicatePaymentException e) {
                    duplicates.incrementAndGet();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        }

        start.countDown(); // release all 3 threads at once
        for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, settled.get(), "exactly one bridge should settle");
        assertEquals(2, duplicates.get(), "the other two should be duplicates");
        assertEquals(1, payments.count());
        assertEquals(1, signatureRecords.count());
        assertEquals(1, transactions.count());
        assertEquals(2, ledger.count());

        // Balance moved exactly once
        assertTrue(reconciliation.reconcile().balanced());
        BigDecimal aliceAfter = accounts.findById("alice@demo").orElseThrow().getBalance();
        BigDecimal bobAfter = accounts.findById("bob@demo").orElseThrow().getBalance();
        assertEquals(aliceBefore.subtract(new BigDecimal("100.00")), aliceAfter);
        assertEquals(bobBefore.add(new BigDecimal("100.00")), bobAfter);
    }

    @Test
    void tamperedCiphertextIsRejected() throws Exception {
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("50.00"), "1234", 5);

        // Flip a byte in the middle of the ciphertext
        char[] chars = packet.getCiphertext().toCharArray();
        chars[chars.length / 2] = chars[chars.length / 2] == 'A' ? 'B' : 'A';
        packet.setCiphertext(new String(chars));
        assertThrows(InvalidSignatureException.class, () -> bridge.ingest(packet, "bridge-x", 1));
        assertEquals(0, payments.count());
        assertEquals(0, ledger.count());
    }

    @Test
    void insufficientBalanceReturnsRejectedOutcome() throws Exception {
        Account dave = accounts.findById("dave@demo").orElseThrow();
        dave.setBalance(new BigDecimal("10.00"));
        accounts.saveAndFlush(dave);

        MeshPacket packet = demoService.createPacket(
                "dave@demo", "bob@demo", new BigDecimal("100.00"), "1234", 5);

        assertThrows(InsufficientBalanceException.class, () -> bridge.ingest(packet, "bridge-low-balance", 1));
        String packetHash = crypto.hashCiphertext(packet.getCiphertext());
        Transaction tx = transactions.findAll().stream()
                .filter(t -> t.getPacketHash().equals(packetHash))
                .findFirst().orElseThrow();
        assertEquals(Transaction.Status.REJECTED, tx.getStatus());
        assertEquals(Payment.Status.REJECTED, payments.findByPacketHash(packetHash).orElseThrow().getStatus());
        assertEquals(0, ledger.count());
        assertEquals(new BigDecimal("10.00"), accounts.findById("dave@demo").orElseThrow().getBalance());
    }

    @Test
    void retryIsAllowedAfterRetryableProcessingFailure() throws Exception {
        PaymentInstruction instruction = signedInstruction(
                "alice@demo", "eve@demo", new BigDecimal("25.00"), System.currentTimeMillis());
        MeshPacket packet = encryptedPacket(instruction, 5);

        assertThrows(AccountNotFoundException.class, () -> bridge.ingest(packet, "bridge-retry", 1));

        accounts.saveAndFlush(new Account("eve@demo", "Eve", new BigDecimal("100.00")));

        BridgeIngestionService.IngestResult second = bridge.ingest(packet, "bridge-retry", 1);
        assertEquals("SETTLED", second.outcome());
        assertEquals(new BigDecimal("125.00"), accounts.findById("eve@demo").orElseThrow().getBalance());
    }

    @Test
    void invalidPacketUuidIsRejected() throws Exception {
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("10.00"), "1234", 5);
        packet.setPacketId("not-a-uuid");

        assertThrows(InvalidPacketException.class, () -> bridge.ingest(packet, "bridge-invalid", 1));
    }

    @Test
    void negativeAmountIsRejected() throws Exception {
        PaymentInstruction instruction = signedInstruction(
                "alice@demo", "bob@demo", new BigDecimal("-1.00"), System.currentTimeMillis());

        assertThrows(InvalidPacketException.class,
                () -> bridge.ingest(encryptedPacket(instruction, 5), "bridge-invalid", 1));
    }

    @Test
    void senderEqualsReceiverIsRejected() throws Exception {
        PaymentInstruction instruction = signedInstruction(
                "alice@demo", "alice@demo", new BigDecimal("1.00"), System.currentTimeMillis());

        assertThrows(InvalidPacketException.class,
                () -> bridge.ingest(encryptedPacket(instruction, 5), "bridge-invalid", 1));
    }

    @Test
    void oversizedCiphertextIsRejectedBeforeDecrypt() {
        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(5);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCiphertext("A".repeat(16385));

        assertThrows(InvalidPacketException.class, () -> bridge.ingest(packet, "bridge-invalid", 1));
    }

    @Test
    void ttlAboveMaxIsRejected() throws Exception {
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("10.00"), "1234", 11);

        assertThrows(InvalidPacketException.class, () -> bridge.ingest(packet, "bridge-invalid", 1));
    }

    @Test
    void hopCountAboveMaxIsRejected() throws Exception {
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("10.00"), "1234", 5);

        assertThrows(InvalidPacketException.class, () -> bridge.ingest(packet, "bridge-invalid", 11));
    }

    @Test
    void encryptDecryptRoundTrip() throws Exception {
        PaymentInstruction original = signedInstruction(
                "alice@demo", "bob@demo", new BigDecimal("123.45"), System.currentTimeMillis());

        String ct = crypto.encrypt(original, serverKey.getPublicKey());
        PaymentInstruction decrypted = crypto.decrypt(ct);

        assertEquals(original.getSenderVpa(), decrypted.getSenderVpa());
        assertEquals(original.getReceiverVpa(), decrypted.getReceiverVpa());
        assertEquals(0, original.getAmount().compareTo(decrypted.getAmount()));
        assertEquals(original.getNonce(), decrypted.getNonce());
    }

    @Test
    void databaseFailureRollsBackBothBalancesAndAllowsRetry() throws Exception {
        BigDecimal aliceBefore = accounts.findById("alice@demo").orElseThrow().getBalance();
        BigDecimal bobBefore = accounts.findById("bob@demo").orElseThrow().getBalance();
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("1.00"), "1234", 5);
        String hash = crypto.hashCiphertext(packet.getCiphertext());

        jdbc.execute("""
                CREATE FUNCTION test_fail_ledger() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected ledger failure'; END $$;
                CREATE TRIGGER test_fail_ledger BEFORE INSERT ON ledger_entries
                FOR EACH ROW EXECUTE FUNCTION test_fail_ledger();
                """);
        try {
            assertThrows(SettlementException.class, () -> bridge.ingest(packet, "valid-bridge", 1));
        } finally {
            jdbc.execute("DROP TRIGGER test_fail_ledger ON ledger_entries; DROP FUNCTION test_fail_ledger()");
        }
        assertEquals(aliceBefore, accounts.findById("alice@demo").orElseThrow().getBalance());
        assertEquals(bobBefore, accounts.findById("bob@demo").orElseThrow().getBalance());
        assertFalse(transactions.existsByPacketHash(hash));
        assertEquals(Payment.Status.FAILED, payments.findByPacketHash(hash).orElseThrow().getStatus());
        assertEquals(0, ledger.count());
        assertEquals(IdempotencyService.PacketState.FAILED_RETRYABLE, idempotency.getState(hash));
        assertTrue(reconciliation.reconcile().balanced());

        assertEquals("SETTLED", bridge.ingest(packet, "valid-bridge", 1).outcome());
        assertEquals(1, payments.count());
        assertEquals(2, ledger.count());
        assertEquals(aliceBefore.subtract(new BigDecimal("1.00")),
                accounts.findById("alice@demo").orElseThrow().getBalance());
        assertEquals(bobBefore.add(new BigDecimal("1.00")),
                accounts.findById("bob@demo").orElseThrow().getBalance());
        assertTrue(reconciliation.reconcile().balanced());
    }

    @Test
    void methodValidationUsesGlobalHandler() throws Exception {
        MeshPacket packet = demoService.createPacket(
                "alice@demo", "bob@demo", new BigDecimal("1.00"), "1234", 5);
        for (String[] header : new String[][] {{"X-Hop-Count", "-1"}, {"X-Bridge-Node-Id", " "}}) {
            mvc.perform(post("/api/bridge/ingest").contentType("application/json")
                            .header(header[0], header[1]).header("X-Trace-Id", "validation-trace")
                            .content(json.writeValueAsString(packet)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.traceId").value("validation-trace"))
                    .andExpect(header().string("X-Trace-Id", "validation-trace"));
        }
        assertNull(idempotency.getState(crypto.hashCiphertext(packet.getCiphertext())));
    }

    @Test
    void insufficientFundsApiKeepsRejectedRecordAndReturnsConflictOnReplay() throws Exception {
        BigDecimal receiverBefore = accounts.findById("bob@demo").orElseThrow().getBalance();
        MeshPacket packet = demoService.createPacket(
                "dave@demo", "bob@demo", new BigDecimal("501.00"), "1234", 5);
        String body = json.writeValueAsString(packet);
        String hash = crypto.hashCiphertext(packet.getCiphertext());
        mvc.perform(post("/api/bridge/ingest").contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"));
        assertTrue(transactions.existsByPacketHash(hash));
        assertEquals(IdempotencyService.PacketState.REJECTED, idempotency.getState(hash));
        assertEquals(receiverBefore, accounts.findById("bob@demo").orElseThrow().getBalance());
        assertTrue(reconciliation.reconcile().balanced());
        mvc.perform(post("/api/bridge/ingest").contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_PAYMENT"));
    }

    @Test
    void pendingPaymentExistsBeforeSettlementAndHasBalancedLedgerAfterward() throws Exception {
        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", new BigDecimal("12.00"), "1234", 5);
        PaymentInstruction instruction = crypto.decrypt(packet.getCiphertext());
        String hash = crypto.hashCiphertext(packet.getCiphertext());
        Payment pending = lifecycle.register(instruction, hash, "bridge", 1);
        assertEquals(Payment.Status.PENDING, payments.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(0, ledger.count());
        assertEquals(0, transactions.count());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.pendingTransactions").value(1))
                .andExpect(jsonPath("$.paymentStatusCounts.PENDING").value(1));
        assertEquals(1, payments.countByStatusAndReceivedAtGreaterThanEqualAndReceivedAtLessThan(
                Payment.Status.PENDING, pending.getReceivedAt(), pending.getReceivedAt().plusSeconds(1)));
        assertEquals(0, payments.countByStatusAndReceivedAtGreaterThanEqualAndReceivedAtLessThan(
                Payment.Status.PENDING, pending.getReceivedAt().minusSeconds(1), pending.getReceivedAt()));
        settlement.settle(instruction, hash, "bridge", 1);
        var entries = ledger.findByPaymentIdOrderById(pending.getId());
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(entry -> entry.getPayment().getId().equals(pending.getId())));
        assertEquals(1, entries.stream().map(entry -> entry.getTransaction().getId()).distinct().count());
        assertEquals("alice@demo", entries.stream()
                .filter(entry -> entry.getDirection() == com.demo.upimesh.model.LedgerEntry.Direction.DEBIT)
                .findFirst().orElseThrow().getAccountVpa());
        assertEquals("bob@demo", entries.stream()
                .filter(entry -> entry.getDirection() == com.demo.upimesh.model.LedgerEntry.Direction.CREDIT)
                .findFirst().orElseThrow().getAccountVpa());
        assertTrue(reconciliation.reconcile().balanced());
        assertEquals(new BigDecimal("5000.00"), accounts.findById("alice@demo").orElseThrow().getOpeningBalance());
        assertEquals(new BigDecimal("12.00"), entries.get(0).getAmount());
        assertEquals(entries.get(0).getAmount(), entries.get(1).getAmount());
        assertEquals(java.util.Set.of("alice@demo", "bob@demo"),
                entries.stream().map(com.demo.upimesh.model.LedgerEntry::getAccountVpa).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, entries.stream().map(com.demo.upimesh.model.LedgerEntry::getDirection).distinct().count());
        assertEquals(Payment.Status.SETTLED, payments.findById(pending.getId()).orElseThrow().getStatus());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/payments/" + pending.getPaymentId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.nonce").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.transactionId").isNumber());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/transactions"))
                .andExpect(jsonPath("$[0].paymentId").value(pending.getPaymentId().toString()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/payments/" + UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void expiryAndFutureDatesHaveDurableStatesWithoutMoneyMovement() throws Exception {
        PaymentInstruction expired = signedInstruction("alice@demo", "bob@demo", BigDecimal.ONE,
                System.currentTimeMillis() - 86401000L);
        MeshPacket packet = encryptedPacket(expired, 5);
        assertThrows(ExpiredPacketException.class, () -> bridge.ingest(packet, "bridge", 1));
        assertEquals(Payment.Status.EXPIRED, payments.findByPacketHash(crypto.hashCiphertext(packet.getCiphertext())).orElseThrow().getStatus());
        expired.setNonce(UUID.randomUUID().toString());
        expired.setPaymentId(UUID.randomUUID().toString());
        expired.setSignedAt(System.currentTimeMillis() + 600000);
        demoKeys.sign(expired);
        MeshPacket future = encryptedPacket(expired, 5);
        assertThrows(InvalidPacketException.class, () -> bridge.ingest(future, "bridge", 1));
        assertEquals(Payment.Status.FAILED, payments.findByPacketHash(crypto.hashCiphertext(future.getCiphertext())).orElseThrow().getStatus());
        assertEquals(0, ledger.count());
        assertEquals(0, transactions.count());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.paymentStatusCounts.EXPIRED").value(1))
                .andExpect(jsonPath("$.paymentStatusCounts.FAILED").value(1))
                .andExpect(jsonPath("$.paymentStatusCounts.PROCESSING").value(0))
                .andExpect(jsonPath("$.totalPayments").value(2));
    }

    @Test
    void redisEvictionOuterIdChangesAndReencryptionCannotSettleAgain() throws Exception {
        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", BigDecimal.ONE, "1234", 5);
        PaymentInstruction instruction = crypto.decrypt(packet.getCiphertext());
        assertEquals("SETTLED", bridge.ingest(packet, "bridge", 1).outcome());
        idempotency.clear();
        packet.setPacketId(UUID.randomUUID().toString());
        assertThrows(DuplicatePaymentException.class, () -> bridge.ingest(packet, "bridge", 1));
        idempotency.clear();
        assertThrows(DuplicatePaymentException.class, () -> bridge.ingest(encryptedPacket(instruction, 5), "bridge", 1));
        instruction.setPaymentId(UUID.randomUUID().toString());
        demoKeys.sign(instruction);
        assertThrows(DuplicatePaymentException.class, () -> bridge.ingest(encryptedPacket(instruction, 5), "bridge", 1));
        assertEquals(1, payments.count());
        assertEquals(1, transactions.count());
        assertEquals(2, ledger.count());
    }

    @Test
    void reconciliationReportsExactDifferencesWithoutRepairingThem() throws Exception {
        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", new BigDecimal("12.34"), "1234", 5);
        bridge.ingest(packet, "bridge", 1);
        var report = reconciliation.reconcile();
        assertTrue(report.balanced());
        var alice = report.accounts().stream().filter(a -> a.vpa().equals("alice@demo")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("5000.00"), alice.openingBalance());
        assertEquals(new BigDecimal("12.34"), alice.ledgerDebits());
        assertEquals(0, alice.ledgerCredits().signum());
        assertEquals(new BigDecimal("4987.66"), alice.calculatedBalance());
        jdbc.update("UPDATE accounts SET balance=balance+0.25 WHERE vpa='alice@demo'");
        jdbc.update("UPDATE accounts SET balance=balance-0.10 WHERE vpa='bob@demo'");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(false))
                .andExpect(jsonPath("$.checkedAt").isString())
                .andExpect(jsonPath("$.accounts[0].vpa").value("alice@demo"))
                .andExpect(jsonPath("$.accounts[0].difference").value(0.25))
                .andExpect(jsonPath("$.accounts[1].difference").value(-0.10));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.reconciliationAccountsChecked").value(5))
                .andExpect(jsonPath("$.reconciliationMismatchedAccounts").value(2))
                .andExpect(jsonPath("$.reconciliationBalanced").value(false));
        assertEquals(new BigDecimal("4987.91"), accounts.findById("alice@demo").orElseThrow().getBalance());
    }

    @Test
    void newAndEmptyAccountsReconcileWithoutLedgerHistory() {
        accounts.saveAndFlush(new Account("new@demo", "New", new BigDecimal("123.45")));
        assertTrue(reconciliation.reconcile().balanced());
        assertEquals(new BigDecimal("123.45"), accounts.findById("new@demo").orElseThrow().getOpeningBalance());
        accounts.deleteAllInBatch();
        var empty = reconciliation.reconcile();
        assertTrue(empty.balanced());
        assertTrue(empty.accounts().isEmpty());
        assertNotNull(empty.checkedAt());
    }

    @Test
    void reconciliationUsesAConsistentSnapshotDuringSettlements() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> writer = pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 12; i++) {
                        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", BigDecimal.ONE, "1234", 5);
                        bridge.ingest(packet, "bridge", 1);
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            start.countDown();
            for (int i = 0; i < 25; i++) assertTrue(reconciliation.reconcile().balanced());
            writer.get(20, TimeUnit.SECONDS);
            assertTrue(reconciliation.reconcile().balanced());
            assertEquals(24, ledger.count());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test
    void invalidPrecisionCannotBeSilentlyRoundedIntoAPayment() throws Exception {
        for (String amount : java.util.List.of("0.001", "100000000000000000.00")) {
            assertThrows(InvalidPacketException.class, () -> demoService.createPacket(
                    "alice@demo", "bob@demo", new BigDecimal(amount), "1234", 5));
        }
        assertEquals(0, payments.count());
        assertEquals(0, ledger.count());
    }

    @Test
    void concurrentFailedRetriesAndStaleFailureCannotOverwriteSettlement() throws Exception {
        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", BigDecimal.ONE, "1234", 5);
        PaymentInstruction instruction = crypto.decrypt(packet.getCiphertext());
        String hash = crypto.hashCiphertext(packet.getCiphertext());
        Payment first = lifecycle.register(instruction, hash, "bridge", 1);
        lifecycle.recordFailure(first.getId(), first.getVersion(), new SettlementException());
        Payment failed = payments.findById(first.getId()).orElseThrow();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger settled = new AtomicInteger();
        try {
            var jobs = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 3; i++) jobs.add(pool.submit(() -> {
                try {
                    start.await();
                    settlement.settle(instruction, hash, "bridge", 1);
                    settled.incrementAndGet();
                } catch (DuplicatePaymentException expected) {
                    // PostgreSQL serializes retries even if Redis admits all of them.
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
            start.countDown();
            for (Future<?> job : jobs) job.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        lifecycle.recordFailure(failed.getId(), failed.getVersion(), new SettlementException());
        assertEquals(1, settled.get());
        assertEquals(Payment.Status.SETTLED, payments.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(1, transactions.count());
        assertEquals(2, ledger.count());
    }

    private MeshPacket encryptedPacket(PaymentInstruction instruction, int ttl) throws Exception {
        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCiphertext(crypto.encrypt(instruction, serverKey.getPublicKey()));
        return packet;
    }

    private PaymentInstruction signedInstruction(String sender, String receiver,
            BigDecimal amount, long signedAt) {
        PaymentInstruction instruction = new PaymentInstruction(UUID.randomUUID().toString(),
                sender, receiver, amount, UUID.randomUUID().toString(), signedAt, "phone-alice");
        demoKeys.sign(instruction);
        return instruction;
    }

    @Test
    void signatureFailuresAreAuditedWithoutMoneyMovement() throws Exception {
        String[] failures = {"MISSING_SIGNATURE", "INVALID_SIGNATURE", "INVALID_SIGNATURE",
                "UNKNOWN_DEVICE", "WRONG_DEVICE_OWNER"};
        for (int i = 0; i < failures.length; i++) {
            PaymentInstruction instruction = crypto.decrypt(demoService.createPacket(
                    "alice@demo", "bob@demo", BigDecimal.ONE, null, 5).getCiphertext());
            switch (i) {
                case 0 -> instruction.setSignature(null);
                case 1 -> instruction.setSignature(java.util.Base64.getEncoder().encodeToString(new byte[64]));
                case 2 -> instruction.setAmount(new BigDecimal("2.00"));
                case 3 -> instruction.setDeviceId("unknown-phone");
                case 4 -> instruction.setSenderVpa("carol@demo");
                default -> throw new AssertionError();
            }
            MeshPacket packet = encryptedPacket(instruction, 5);
            assertThrows(InvalidSignatureException.class, () -> bridge.ingest(packet, "bridge", 1));
            Payment payment = payments.findByPacketHash(crypto.hashCiphertext(packet.getCiphertext())).orElseThrow();
            PaymentSignature audit = signatureRecords.findByPayment_Id(payment.getId()).orElseThrow();
            assertEquals(Payment.Status.REJECTED, payment.getStatus());
            assertEquals(PaymentSignature.VerificationStatus.FAILED, audit.getVerificationStatus());
            assertEquals(failures[i], audit.getFailureCode());
        }
        assertEquals(0, transactions.count());
        assertEquals(0, ledger.count());
        assertEquals(new BigDecimal("5000.00"), accounts.findById("alice@demo").orElseThrow().getBalance());
    }

    @Test
    void revokedAndUntrustedDevicesCannotAuthorize() throws Exception {
        MeshPacket revokedPacket = demoService.createPacket("alice@demo", "bob@demo", BigDecimal.ONE, null, 5);
        MeshPacket untrustedPacket = demoService.createPacket("alice@demo", "bob@demo", BigDecimal.ONE, null, 5);
        Device device = devices.findByDeviceId("phone-alice").orElseThrow();
        device.setStatus(Device.Status.REVOKED);
        device = devices.saveAndFlush(device);
        assertThrows(InvalidSignatureException.class, () -> bridge.ingest(revokedPacket, "bridge", 1));
        device.setStatus(Device.Status.ACTIVE);
        device.setTrustStatus(Device.TrustStatus.UNTRUSTED);
        devices.saveAndFlush(device);
        assertThrows(InvalidSignatureException.class, () -> bridge.ingest(untrustedPacket, "bridge", 1));
        assertEquals(0, ledger.count());
        assertEquals(2, signatureRecords.count());
    }

    @Test
    void gossipUsesDirectedLinksAndPersistsFourHopsBeforeBridgeFlush() throws Exception {
        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", BigDecimal.ONE, null, 5);
        mesh.inject("phone-alice", packet);
        assertEquals(1, meshService.gossip().transfers());
        assertEquals(0, mesh.getDevice("phone-bridge").packetCount());
        for (int i = 0; i < 3; i++) assertEquals(1, meshService.gossip().transfers());
        assertEquals(4, routes.findByPacketId(packet.getPacketId()).size());
        assertEquals(1, mesh.getDevice("phone-bridge").packetCount());
        assertEquals("SETTLED", meshService.flush().results().get(0).outcome());
        assertEquals(4, transactions.findAll().get(0).getHopCount());
        assertTrue(routes.findByPacketId(packet.getPacketId()).stream().allMatch(r -> r.getPayment() != null));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/mesh/routes")
                .param("packetId", packet.getPacketId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void dashboardEndpointsUsePersistedLedgerRoutesAndSignatureEvents() throws Exception {
        var get = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/summary");
        mvc.perform(get).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value(11500.00))
                .andExpect(jsonPath("$.activeDevices").value(5))
                .andExpect(jsonPath("$.activeConnections").value(4))
                .andExpect(jsonPath("$.settlementSuccessRate").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/cashflow")
                .param("accountVpa", "alice@demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.netMovement").value(0))
                .andExpect(jsonPath("$.points.length()").value(15));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/activity"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/security-events"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.failedSignatureVerification").value(0));

        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", new BigDecimal("12.00"), null, 5);
        mesh.inject("phone-alice", packet);
        for (int i = 0; i < 4; i++) meshService.gossip();
        assertEquals("SETTLED", meshService.flush().results().get(0).outcome());
        mvc.perform(get).andExpect(jsonPath("$.settledPayments").value(1))
                .andExpect(jsonPath("$.settlementSuccessRate").value(100))
                .andExpect(jsonPath("$.totalBalance").value(11500.00));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/cashflow")
                .param("accountVpa", "alice@demo"))
                .andExpect(jsonPath("$.totalDebitVolume").value(12.00))
                .andExpect(jsonPath("$.netMovement").value(-12.00));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/transaction-volume"))
                .andExpect(jsonPath("$.settledCount").value(1))
                .andExpect(jsonPath("$.settledAmount").value(12.00))
                .andExpect(jsonPath("$.points.length()").value(7));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/network-stats"))
                .andExpect(jsonPath("$.packetsRouted").value(4))
                .andExpect(jsonPath("$.mostActiveBridgeDevice").value("phone-bridge"));
        String activity = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/activity"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String type : java.util.List.of("PAYMENT", "TRANSACTION", "LEDGER", "ROUTE", "SIGNATURE"))
            assertTrue(activity.contains("\"type\":\"" + type + "\""), type);
        PaymentInstruction unsigned = crypto.decrypt(demoService.createPacket(
                "alice@demo", "bob@demo", BigDecimal.ONE, null, 5).getCiphertext());
        unsigned.setSignature("invalid");
        assertThrows(InvalidSignatureException.class, () -> bridge.ingest(encryptedPacket(unsigned, 5), "bridge", 1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/security-events"))
                .andExpect(jsonPath("$.invalidSignatureEvents").value(1))
                .andExpect(jsonPath("$.items[0].eventType").value("INVALID_SIGNATURE"));
    }

    @Test
    void dashboardEndpointsReturnSafeEmptyHistoryAndEmptyRegistry() throws Exception {
        jdbc.update("DELETE FROM mesh_connections");
        jdbc.update("DELETE FROM devices");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM accounts");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAccounts").value(0))
                .andExpect(jsonPath("$.totalBalance").value(0))
                .andExpect(jsonPath("$.activeDevices").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/cashflow"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.netMovement").value(0))
                .andExpect(jsonPath("$.points.length()").value(15));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/activity"))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/network-stats"))
                .andExpect(jsonPath("$.totalDevices").value(0))
                .andExpect(jsonPath("$.mostActiveBridgeDevice").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/transaction-volume"))
                .andExpect(jsonPath("$.settledAmount").value(0))
                .andExpect(jsonPath("$.points.length()").value(7));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/security-events"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void deviceAndConnectionApisExposeManageablePersistentTopology() throws Exception {
        var key = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var registration = new com.demo.upimesh.dto.request.DeviceRegistrationRequest("phone-new",
                "alice@demo", "New phone", false,
                java.util.Base64.getEncoder().encodeToString(key.getPublic().getEncoded()), "Ed25519");
        mvc.perform(post("/api/devices").contentType("application/json")
                .content(json.writeValueAsString(registration)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.trustStatus").value("UNTRUSTED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                "/api/devices/phone-new/status").contentType("application/json")
                .content("{\"trustStatus\":\"TRUSTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trustStatus").value("TRUSTED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        var connection = new com.demo.upimesh.dto.request.MeshConnectionRequest(
                "phone-alice", "phone-new", null, null);
        mvc.perform(post("/api/mesh/connections").contentType("application/json")
                .content(json.writeValueAsString(connection)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceDeviceId").value("phone-alice"));
        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", BigDecimal.ONE, null, 5);
        mesh.inject("phone-alice", packet);
        assertEquals(2, meshService.gossip().transfers());
        assertEquals(1, mesh.getDevice("phone-new").packetCount());
    }

    @Test
    void inactiveLinkAndBridgeDoNotTransferOrUpload() throws Exception {
        MeshPacket packet = demoService.createPacket("alice@demo", "bob@demo", BigDecimal.ONE, null, 5);
        mesh.inject("phone-alice", packet);
        jdbc.update("UPDATE mesh_connections SET status='INACTIVE' WHERE source_device_id="
                + "(SELECT id FROM devices WHERE device_id='phone-alice')");
        assertEquals(0, meshService.gossip().transfers());
        mesh.inject("phone-bridge", packet);
        Device bridgeDevice = devices.findByDeviceId("phone-bridge").orElseThrow();
        bridgeDevice.setStatus(Device.Status.INACTIVE);
        devices.saveAndFlush(bridgeDevice);
        assertEquals(0, meshService.flush().uploadsAttempted());
        assertEquals(0, payments.count());
    }

    @Test
    void demoSendChoosesBobAndCarolOwnedDevices() throws Exception {
        String[] senders = {"bob@demo", "carol@demo"};
        String[] expectedDevices = {"phone-stranger1", "phone-stranger2"};
        for (int i = 0; i < senders.length; i++) {
            var request = new com.demo.upimesh.dto.request.DemoSendRequest(
                    senders[i], "alice@demo", BigDecimal.ONE, null, 5, null);
            mvc.perform(post("/api/demo/send").contentType("application/json")
                    .content(json.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.injectedAt").value(expectedDevices[i]));
            assertEquals(1, mesh.getDevice(expectedDevices[i]).packetCount());
        }
    }
}
