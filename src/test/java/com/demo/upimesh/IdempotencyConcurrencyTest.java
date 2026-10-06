package com.demo.upimesh;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.exception.DuplicatePaymentException;
import com.demo.upimesh.exception.InvalidSignatureException;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoService;
import com.demo.upimesh.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;

/**
 * Verifies Redis claim isolation and exactly-once settlement for concurrent bridge uploads.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
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

    @Autowired private StringRedisTemplate redis;
    @Autowired private DemoService demoService;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private IdempotencyService idempotency;
    @Autowired private AccountRepository accounts;
    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;

    @BeforeEach
    void clear() {
        idempotency.clear();
    }

    @Test
    void claimsAreSharedBetweenServiceInstances() {
        IdempotencyService otherInstance = new IdempotencyService(redis, 86400);
        assertTrue(idempotency.claim("shared-packet"));
        assertFalse(otherInstance.claim("shared-packet"));
        long ttl = assertInstanceOf(Long.class, redis.getExpire("upi:mesh:idempotency:shared-packet"));
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

        try {
            Future<?>[] futures = new Future<?>[3];
            for (int i = 0; i < 3; i++) {
                final String node = "bridge-" + i;
                futures[i] = pool.submit(() -> {
                    try {
                        start.await();
                        BridgeIngestionService.IngestResult result = bridge.ingest(packet, node, 3);
                        if ("SETTLED".equals(result.outcome())) settled.incrementAndGet();
                        else if ("DUPLICATE".equals(result.outcome())) duplicates.incrementAndGet();
                        else throw new AssertionError("Unexpected ingestion outcome: " + result.outcome());
                    } catch (DuplicatePaymentException e) {
                        duplicates.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Bridge upload interrupted", e);
                    }
                });
            }

            start.countDown(); // Release all three bridge uploads together.
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "bridge threads should stop");
        }

        assertEquals(1, settled.get(), "exactly one bridge should settle");
        assertEquals(2, duplicates.get(), "the other two should be duplicates");

        // Balance moved exactly once
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
    }

    @Test
    void encryptDecryptRoundTrip() throws Exception {
        PaymentInstruction original = new PaymentInstruction(
                UUID.randomUUID().toString(), "alice@demo", "bob@demo", new BigDecimal("123.45"),
                UUID.randomUUID().toString(), System.currentTimeMillis(), "device-alice");

        String ct = crypto.encrypt(original, serverKey.getPublicKey());
        PaymentInstruction decrypted = crypto.decrypt(ct);

        assertEquals(original.getSenderVpa(), decrypted.getSenderVpa());
        assertEquals(original.getReceiverVpa(), decrypted.getReceiverVpa());
        assertEquals(0, original.getAmount().compareTo(decrypted.getAmount()));
        assertEquals(original.getNonce(), decrypted.getNonce());
        assertEquals(original.getPaymentId(), decrypted.getPaymentId());
        assertEquals(original.getSignedAt(), decrypted.getSignedAt());
        assertEquals(original.getDeviceId(), decrypted.getDeviceId());
    }
}
