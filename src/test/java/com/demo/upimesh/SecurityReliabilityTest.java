package com.demo.upimesh;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real MVC, PostgreSQL and Redis regression coverage for the limited demo fixes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import({SecurityReliabilityTest.FailureConfiguration.class, com.demo.upimesh.baseline.SqlCounterConfiguration.class})
class SecurityReliabilityTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.demo.upimesh.service.DemoService demo;
    @Autowired com.demo.upimesh.crypto.HybridCryptoService crypto;
    @Autowired com.demo.upimesh.crypto.ServerKeyHolder serverKey;
    @Autowired com.demo.upimesh.service.BridgeIngestionService bridge;
    @Autowired com.demo.upimesh.repository.PaymentRepository payments;
    @Autowired com.demo.upimesh.service.MeshTopologyService topology;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private com.demo.upimesh.model.MeshPacket envelope(com.demo.upimesh.model.PaymentInstruction instruction) throws Exception {
        var packet = new com.demo.upimesh.model.MeshPacket();
        packet.setPacketId(java.util.UUID.randomUUID().toString());
        packet.setCreatedAt(System.currentTimeMillis());
        packet.setTtl(5);
        packet.setCiphertext(crypto.encrypt(instruction, serverKey.getPublicKey()));
        return packet;
    }

    @Test void invalidSignatureCannotReserveIdentityOrLinkRoutes() throws Exception {
        var valid = demo.createPacket("alice@demo", "bob@demo", new java.math.BigDecimal("0.01"), "", 5);
        var instruction = crypto.decrypt(valid.getCiphertext());
        String signature = instruction.getSignature();
        instruction.setSignature(java.util.Base64.getEncoder().encodeToString(new byte[64]));
        var invalid = envelope(instruction);
        topology.recordTransfer(invalid.getPacketId(), "phone-alice", "phone-stranger1", 1, 4);
        assertThrows(com.demo.upimesh.exception.InvalidSignatureException.class,
                () -> bridge.ingest(invalid, "phone-bridge", 1));
        assertTrue(payments.findByPaymentId(java.util.UUID.fromString(instruction.getPaymentId())).isEmpty());
        assertEquals(0, jdbc.queryForObject("select count(*) from packet_routes where packet_id=? and payment_id is not null",
                Integer.class, invalid.getPacketId()));
        instruction.setSignature(signature);
        assertEquals("SETTLED", bridge.ingest(valid, "phone-bridge", 1).outcome());
    }

    @Test void modifiedInstructionsAreRejectedAndNonceUniquenessRemains() throws Exception {
        var original = demo.createPacket("alice@demo", "bob@demo", new java.math.BigDecimal("0.01"), "", 5);
        var instruction = crypto.decrypt(original.getCiphertext());
        var altered = json.readValue(json.writeValueAsBytes(instruction), com.demo.upimesh.model.PaymentInstruction.class);
        altered.setAmount(new java.math.BigDecimal("0.02"));
        var amountPacket = envelope(altered);
        assertThrows(com.demo.upimesh.exception.InvalidSignatureException.class,
                () -> bridge.ingest(amountPacket, "phone-bridge", 1));
        altered.setAmount(instruction.getAmount());
        altered.setReceiverVpa("carol@demo");
        var receiverPacket = envelope(altered);
        assertThrows(com.demo.upimesh.exception.InvalidSignatureException.class,
                () -> bridge.ingest(receiverPacket, "phone-bridge", 1));
        assertEquals("SETTLED", bridge.ingest(original, "phone-bridge", 1).outcome());
        var reencryption = envelope(instruction);
        assertThrows(com.demo.upimesh.exception.DuplicatePaymentException.class,
                () -> bridge.ingest(reencryption, "phone-bridge", 1));
        instruction.setPaymentId(java.util.UUID.randomUUID().toString());
        demoKeys.sign(instruction);
        var reusedNonce = envelope(instruction);
        assertThrows(com.demo.upimesh.exception.DuplicatePaymentException.class,
                () -> bridge.ingest(reusedNonce, "phone-bridge", 1));
    }

    @Autowired com.demo.upimesh.service.DemoDeviceKeyService demoKeys;
    @Autowired com.demo.upimesh.service.IdempotencyService idempotency;

    @Test void missingCacheReturnsCommittedReceiptWithoutSecondDebit() throws Exception {
        var packet = demo.createPacket("alice@demo", "bob@demo", new java.math.BigDecimal("0.01"), "", 5);
        var first = bridge.ingest(packet, "phone-bridge", 1);
        var balance = jdbc.queryForObject("select balance from accounts where vpa='alice@demo'", java.math.BigDecimal.class);
        idempotency.clear();
        var recovered = bridge.ingest(packet, "phone-bridge", 1);
        assertEquals("SETTLED", recovered.outcome());
        assertEquals(first.transactionId(), recovered.transactionId());
        assertEquals(balance, jdbc.queryForObject("select balance from accounts where vpa='alice@demo'", java.math.BigDecimal.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from ledger_entries where transaction_id=?", Integer.class, first.transactionId()));
        assertEquals(com.demo.upimesh.service.IdempotencyService.PacketState.SETTLED, idempotency.getState(first.packetHash()));
    }

    @Autowired com.demo.upimesh.service.DeviceSignatureService signatures;
    @Autowired com.demo.upimesh.service.PaymentLifecycleService lifecycle;
    @Autowired com.demo.upimesh.service.SettlementService settlement;
    @Autowired com.demo.upimesh.service.DeviceService deviceService;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    private record Prepared(com.demo.upimesh.model.PaymentInstruction instruction, String hash, Long id, com.demo.upimesh.model.MeshPacket packet) {}
    private Prepared prepare() throws Exception {
        var packet = demo.createPacket("alice@demo", "bob@demo", new java.math.BigDecimal("0.01"), "", 5);
        var instruction = crypto.decrypt(packet.getCiphertext());
        var verification = signatures.verify(instruction);
        assertTrue(verification.valid());
        String hash = crypto.hashCiphertext(packet.getCiphertext());
        var payment = lifecycle.register(instruction, hash, "phone-bridge", 1);
        signatures.recordVerified(payment.getId(), instruction, verification);
        assertEquals(verification.keyFingerprint(), jdbc.queryForObject(
                "select key_fingerprint from payment_signatures where payment_id=?", String.class, payment.getId()));
        return new Prepared(instruction, hash, payment.getId(), packet);
    }

    private void restoreAliceFixture() {
        jdbc.update("update devices set status='ACTIVE',trust_status='TRUSTED' where device_id='phone-alice'");
        jdbc.update("update device_keys set active=true,revoked_at=null where device_id=(select id from devices where device_id='phone-alice')");
    }

    @Test void committedRevocationAfterVerificationBlocksSettlement() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var verified = new java.util.concurrent.CountDownLatch(1);
        var revoked = new java.util.concurrent.CountDownLatch(1);
        var before = jdbc.queryForObject("select balance from accounts where vpa='alice@demo'", java.math.BigDecimal.class);
        try {
            var payer = pool.submit(() -> {
                var prepared = prepare();
                verified.countDown();
                assertTrue(revoked.await(10, java.util.concurrent.TimeUnit.SECONDS));
                assertThrows(com.demo.upimesh.exception.DeviceRevokedException.class,
                        () -> settlement.settle(prepared.instruction(), prepared.hash(), "phone-bridge", 1));
                return prepared;
            });
            var revoker = pool.submit(() -> {
                assertTrue(verified.await(10, java.util.concurrent.TimeUnit.SECONDS));
                deviceService.update("phone-alice", new com.demo.upimesh.dto.request.DeviceStatusUpdateRequest(
                        com.demo.upimesh.model.Device.Status.REVOKED, null));
                revoked.countDown();
                return true;
            });
            revoker.get(15, java.util.concurrent.TimeUnit.SECONDS);
            var prepared = payer.get(15, java.util.concurrent.TimeUnit.SECONDS);
            var payment = payments.findById(prepared.id()).orElseThrow();
            assertEquals(com.demo.upimesh.model.Payment.Status.REJECTED, payment.getStatus());
            assertEquals("DEVICE_REVOKED", payment.getFailureCode());
            assertEquals(before, jdbc.queryForObject("select balance from accounts where vpa='alice@demo'", java.math.BigDecimal.class));
            assertEquals(0, jdbc.queryForObject("select count(*) from ledger_entries where payment_id=?", Integer.class, prepared.id()));
        } finally {
            revoked.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
            restoreAliceFixture();
        }
    }

    @Test void settlementAndRevocationShareCommitLockWithoutDeadlock() throws Exception {
        var prepared = prepare();
        var staged = new java.util.concurrent.CountDownLatch(1);
        var commit = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var payer = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                settlement.settle(prepared.instruction(), prepared.hash(), "phone-bridge", 1);
                staged.countDown();
                try { assertTrue(commit.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return true;
            }));
            assertTrue(staged.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var revoker = pool.submit(() -> deviceService.update("phone-alice",
                    new com.demo.upimesh.dto.request.DeviceStatusUpdateRequest(com.demo.upimesh.model.Device.Status.REVOKED, null)));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                    jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'", Integer.class) > 0);
            assertFalse(revoker.isDone(), "revocation must wait for settlement commit");
            commit.countDown();
            payer.get(10, java.util.concurrent.TimeUnit.SECONDS);
            revoker.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(com.demo.upimesh.model.Payment.Status.SETTLED, payments.findById(prepared.id()).orElseThrow().getStatus());
        } finally {
            commit.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
            restoreAliceFixture();
        }
    }

    @Test void oversizedJsonIsRejectedBeforeIngestion() throws Exception {
        long before = payments.count();
        safeError(mvc.perform(post("/api/bridge/ingest").contentType("application/json")
                .content("x".repeat(32769))).andReturn(), 413, "PAYLOAD_TOO_LARGE");
        assertEquals(before, payments.count());
    }

    @Test void dtoBoundsRejectBeforeDemoSigning() throws Exception {
        for (String payload : List.of(
                "{\"senderVpa\":\"alice@demo\",\"receiverVpa\":\"bob@demo\",\"amount\":1,\"ttl\":11}",
                "{\"senderVpa\":\"" + "a".repeat(256) + "\",\"receiverVpa\":\"bob@demo\",\"amount\":1}",
                "{\"senderVpa\":\"alice@demo\",\"receiverVpa\":\"bob@demo\",\"amount\":1.001}")) {
            safeError(mvc.perform(post("/api/demo/send").contentType("application/json").content(payload)).andReturn(),
                    400, "VALIDATION_FAILED");
        }
    }

    @Autowired com.demo.upimesh.service.MeshSimulatorService meshSimulator;
    @Autowired com.demo.upimesh.service.MeshService meshService;

    @Test void deliveredPacketsAreRemovedFromAllCustody() throws Exception {
        meshSimulator.resetMesh();
        var packet = demo.createPacket("alice@demo", "bob@demo", new java.math.BigDecimal("0.01"), "", 5);
        meshSimulator.inject("phone-alice", packet);
        for (int i = 0; i < 4; i++) meshSimulator.gossipOnce();
        var first = meshService.flush();
        assertEquals(1, first.uploadsAttempted());
        assertEquals("SETTLED", first.results().get(0).outcome());
        assertTrue(meshSimulator.getDevices().stream().allMatch(d -> d.packetCount() == 0));
        assertEquals(0, meshService.flush().uploadsAttempted());
        assertEquals(2, jdbc.queryForObject("select count(*) from ledger_entries where transaction_id=?", Integer.class,
                first.results().get(0).transactionId()));
    }

    @Test void accountAndDeviceListsAreCappedInDatabase() throws Exception {
        try {
            jdbc.update("insert into accounts(vpa,holder_name,balance,opening_balance) select 'limit-' || n || '@demo','Limit fixture',0,0 from generate_series(1,110) n");
            jdbc.update("insert into devices(device_id,user_id,device_name,status,internet_capability,trust_status,registered_at) "
                    + "select 'limit-' || n,(select id from users where vpa='alice@demo'),'Limit fixture','INACTIVE',false,'UNTRUSTED',now() from generate_series(1,110) n");
            assertEquals(100, json.readTree(mvc.perform(get("/api/accounts")).andReturn().getResponse().getContentAsString()).size());
            assertEquals(100, json.readTree(mvc.perform(get("/api/devices")).andReturn().getResponse().getContentAsString()).size());
        } finally {
            jdbc.update("delete from devices where device_id like 'limit-%'");
            jdbc.update("delete from accounts where vpa like 'limit-%'");
        }
    }

    @Autowired com.demo.upimesh.service.PaymentQueryService queries;

    @Test void recentPaymentsAndDeviceKeysUseBatchedQueries() throws Exception {
        for (int i = 0; i < 5; i++) {
            var packet = demo.createPacket("alice@demo", "bob@demo", new java.math.BigDecimal("0.01"), "", 5);
            assertEquals("SETTLED", bridge.ingest(packet, "phone-bridge", 1).outcome());
        }
        com.demo.upimesh.baseline.SqlCounterConfiguration.reset();
        var recent = queries.recent();
        assertEquals(2, com.demo.upimesh.baseline.SqlCounterConfiguration.executions());
        assertTrue(recent.size() >= 5);
        assertTrue(recent.stream().filter(p -> "SETTLED".equals(p.status())).allMatch(p -> p.transactionId() != null));
        assertTrue(recent.get(0).receivedAt().compareTo(recent.get(1).receivedAt()) >= 0);
        com.demo.upimesh.baseline.SqlCounterConfiguration.reset();
        var listed = deviceService.list();
        assertEquals(2, com.demo.upimesh.baseline.SqlCounterConfiguration.executions());
        assertEquals(5, listed.size());
        assertTrue(listed.stream().allMatch(d -> d.keyFingerprint() != null));
    }

    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

    @Test void invalidSettlementInputFailsBeforeLockingOrChangingPayments() {
        assertThrows(com.demo.upimesh.exception.InvalidPacketException.class,
                () -> settlement.settle(null, "not-a-payment", "bridge", 0));
        var instruction = new com.demo.upimesh.model.PaymentInstruction();
        assertThrows(com.demo.upimesh.exception.InvalidPacketException.class,
                () -> settlement.settle(instruction, "not-a-payment", "bridge", 0));
        instruction.setAmount(java.math.BigDecimal.ZERO);
        assertThrows(com.demo.upimesh.exception.InvalidPacketException.class,
                () -> settlement.settle(instruction, "not-a-payment", "bridge", 0));
    }

    @Test void rejectedReceiptSurvivesCacheLossWithoutNewRejectionOrDebit() throws Exception {
        var packet = demo.createPacket("alice@demo", "bob@demo", new java.math.BigDecimal("1000000.00"), "", 5);
        var before = jdbc.queryForObject("select balance from accounts where vpa='alice@demo'", java.math.BigDecimal.class);
        assertThrows(com.demo.upimesh.exception.InsufficientBalanceException.class,
                () -> bridge.ingest(packet, "phone-bridge", 1));
        var payment = payments.findByPacketHash(crypto.hashCiphertext(packet.getCiphertext())).orElseThrow();
        Long transaction = lifecycle.transactionId(payment);
        assertNotNull(transaction);
        idempotency.clear();
        var receipt = bridge.ingest(packet, "phone-bridge", 1);
        assertEquals("REJECTED", receipt.outcome());
        assertEquals("INSUFFICIENT_BALANCE", receipt.reason());
        assertEquals(transaction, receipt.transactionId());
        assertEquals(before, jdbc.queryForObject("select balance from accounts where vpa='alice@demo'", java.math.BigDecimal.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from transactions where payment_id=?", Integer.class, payment.getId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from ledger_entries where payment_id=?", Integer.class, payment.getId()));
    }

    @Test void expiredProcessingLeaseResumesSamePendingIdentity() throws Exception {
        var prepared = prepare();
        var shortLease = new com.demo.upimesh.service.IdempotencyService(redis, 1, 86400);
        var crashed = shortLease.claimProcessing(prepared.hash());
        assertTrue(crashed.claimed());
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                !Boolean.TRUE.equals(redis.hasKey("upi:mesh:idempotency:" + prepared.hash())));
        assertEquals("SETTLED", bridge.ingest(prepared.packet(), "phone-bridge", 1).outcome());
        shortLease.markRejected(prepared.hash(), crashed.token());
        assertEquals(com.demo.upimesh.service.IdempotencyService.PacketState.SETTLED, idempotency.getState(prepared.hash()));
        assertEquals(2, jdbc.queryForObject("select count(*) from ledger_entries where payment_id=?", Integer.class, prepared.id()));
    }

    @Test void staleTerminalCacheIsReconciledWithDatabase() throws Exception {
        var packet = demo.createPacket("alice@demo", "bob@demo", new java.math.BigDecimal("0.01"), "", 5);
        String hash = crypto.hashCiphertext(packet.getCiphertext());
        redis.opsForValue().set("upi:mesh:idempotency:" + hash, "SETTLED");
        assertEquals("SETTLED", bridge.ingest(packet, "phone-bridge", 1).outcome());
        redis.opsForValue().set("upi:mesh:idempotency:" + hash, "REJECTED");
        assertThrows(com.demo.upimesh.exception.DuplicatePaymentException.class, () -> bridge.ingest(packet, "phone-bridge", 1));
        assertEquals(com.demo.upimesh.service.IdempotencyService.PacketState.SETTLED, idempotency.getState(hash));
    }

    @Test void legacyNullFingerprintIsReverifiedBeforeResuming() throws Exception {
        var prepared = prepare();
        jdbc.update("update payment_signatures set key_fingerprint=null where payment_id=?", prepared.id());
        settlement.settle(prepared.instruction(), prepared.hash(), "phone-bridge", 1);
        assertNotNull(jdbc.queryForObject("select key_fingerprint from payment_signatures where payment_id=?", String.class, prepared.id()));
    }

    @Test void ledgerFailureRollsBackAndRetryRetainsIdentity() throws Exception {
        var packet = demo.createPacket("alice@demo", "bob@demo", new java.math.BigDecimal("0.01"), "", 5);
        var instruction = crypto.decrypt(packet.getCiphertext());
        var before = jdbc.queryForObject("select balance from accounts where vpa='alice@demo'", java.math.BigDecimal.class);
        jdbc.execute("create function test_fail_ledger() returns trigger language plpgsql as $$ begin raise exception 'test ledger failure'; end $$");
        try {
            jdbc.execute("create trigger test_ledger_failure before insert on ledger_entries for each row execute function test_fail_ledger()");
            assertThrows(com.demo.upimesh.exception.SettlementException.class, () -> bridge.ingest(packet, "phone-bridge", 1));
            assertEquals(before, jdbc.queryForObject("select balance from accounts where vpa='alice@demo'", java.math.BigDecimal.class));
            var failed = payments.findByPaymentId(java.util.UUID.fromString(instruction.getPaymentId())).orElseThrow();
            assertEquals(com.demo.upimesh.model.Payment.Status.FAILED, failed.getStatus());
            assertEquals(com.demo.upimesh.service.IdempotencyService.PacketState.FAILED_RETRYABLE, idempotency.getState(failed.getPacketHash()));
            assertEquals(0, jdbc.queryForObject("select count(*) from ledger_entries where payment_id=?", Integer.class, failed.getId()));
        } finally {
            jdbc.execute("drop trigger if exists test_ledger_failure on ledger_entries");
            jdbc.execute("drop function test_fail_ledger()");
        }
        assertEquals("SETTLED", bridge.ingest(packet, "phone-bridge", 1).outcome());
        var settled = payments.findByPaymentId(java.util.UUID.fromString(instruction.getPaymentId())).orElseThrow();
        assertEquals(before.subtract(new java.math.BigDecimal("0.01")), jdbc.queryForObject("select balance from accounts where vpa='alice@demo'", java.math.BigDecimal.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from ledger_entries where payment_id=?", Integer.class, settled.getId()));
    }

    @TestConfiguration
    static class FailureConfiguration {
        @Bean FailureController failureController() { return new FailureController(); }
    }

    @RestController
    static class FailureController {
        @GetMapping("/test/unexpected")
        String fail() { throw new IllegalStateException("SELECT secret FROM private_table /internal/file"); }
    }

    private void safeError(MvcResult result, int status, String code) throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        var body = json.readTree(result.getResponse().getContentAsString());
        Set<String> fields = new java.util.HashSet<>();
        body.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("timestamp", "status", "errorCode", "message", "path", "traceId"), fields);
        assertEquals(status, body.get("status").asInt());
        assertEquals(code, body.get("errorCode").asText());
        assertEquals(result.getResponse().getHeader("X-Trace-Id"), body.get("traceId").asText());
        assertEquals(result.getRequest().getRequestURI(), body.get("path").asText());
        java.time.Instant.parse(body.get("timestamp").asText());
        for (String secret : List.of("SELECT", "private_table", "/internal/file", "Exception", "stackTrace"))
            assertFalse(body.toString().contains(secret));
    }

    @Test void framework404HasSafeEnvelope() throws Exception {
        safeError(mvc.perform(get("/no-such-resource")).andReturn(), 404, "NOT_FOUND");
    }

    @Test void framework405PreservesAllow() throws Exception {
        var result = mvc.perform(get("/api/bridge/ingest")).andReturn();
        safeError(result, 405, "METHOD_NOT_ALLOWED");
        assertTrue(result.getResponse().getHeader("Allow").contains("POST"));
    }

    @Test void framework415HasSafeEnvelope() throws Exception {
        safeError(mvc.perform(post("/api/bridge/ingest").contentType("text/plain").content("private"))
                .andReturn(), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test void unexpectedStillReturnsSafe500() throws Exception {
        safeError(mvc.perform(get("/test/unexpected")).andReturn(), 500, "INTERNAL_SERVER_ERROR");
    }
}
