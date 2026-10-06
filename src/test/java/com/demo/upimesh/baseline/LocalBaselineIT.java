package com.demo.upimesh.baseline;

import com.demo.upimesh.service.DemoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in local measurements, never a latency gate or a production benchmark. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(SqlCounterConfiguration.class)
@Testcontainers
class LocalBaselineIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static final Path KEY_DIRECTORY = temporaryKeys();
    static final int WARMUPS = 5;
    static final int SAMPLES = 20;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("upi.mesh.demo-key-dir", KEY_DIRECTORY::toString);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoService demo;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    final Map<String, List<Sample>> measurements = new LinkedHashMap<>();
    record Sample(double milliseconds, long sqlExecutionCalls, int httpStatus) {}
    record Reply(JsonNode body, Sample sample) {}

    @Test
    void recordBaseline() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("recordedAt", Instant.now().toString());
        report.put("java", System.getProperty("java.runtime.version"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        report.put("architecture", System.getProperty("os.arch"));
        report.put("cpu", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "See host CPU information"));
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        report.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean extended)
            report.put("hostMemoryBytes", extended.getTotalMemorySize());
        report.put("postgresImage", POSTGRES.getDockerImageName());
        report.put("redisImage", REDIS.getDockerImageName());
        report.put("warmups", WARMUPS);
        report.put("samples", SAMPLES);
        report.put("memoryAtReady", memory());

        for (int iteration = 0; iteration < WARMUPS + SAMPLES; iteration++) {
            boolean record = iteration >= WARMUPS;
            request("POST", "/api/mesh/reset", null, 200);
            timed("demo-send", "POST", "/api/demo/send", Map.of("senderVpa", "alice@demo",
                    "receiverVpa", "bob@demo", "amount", 1, "ttl", 5, "startDevice", "phone-alice"), 200, record);
            for (int round = 0; round < 4; round++)
                timed("gossip-round", "POST", "/api/mesh/gossip", null, 200, record);
            Reply flush = timed("bridge-flush-to-settlement", "POST", "/api/mesh/flush", null, 200, record);
            assertEquals(1, flush.body().path("uploadsAttempted").asInt());
            assertEquals("SETTLED", flush.body().at("/results/0/outcome").asText());
            long transactionId = flush.body().at("/results/0/transactionId").asLong();
            verifyCommitted(transactionId);
            if (iteration == 0) report.put("memoryAfterFirstFlow", memory());
            long entriesBefore = count("ledger_entries");
            BigDecimal balanceBefore = balance("alice@demo");
            Reply duplicate = timed("duplicate-flush", "POST", "/api/mesh/flush", null, 200, record);
            assertEquals("DUPLICATE", duplicate.body().at("/results/0/outcome").asText());
            assertEquals(entriesBefore, count("ledger_entries"));
            assertEquals(balanceBefore, balance("alice@demo"));

            // Generate a valid packet outside the HTTP ingestion timing window.
            var packet = demo.createPacket("alice@demo", "bob@demo", BigDecimal.ONE, null, 5);
            Reply ingest = timed("bridge-ingest-to-settlement", "POST", "/api/bridge/ingest", packet, 200, record);
            assertEquals("SETTLED", ingest.body().path("outcome").asText());
            verifyCommitted(ingest.body().path("transactionId").asLong());
            timed("duplicate-ingest", "POST", "/api/bridge/ingest", packet, 409, record);
            assertEquals(entriesBefore + 2, count("ledger_entries"));
        }

        String[] endpoints = {"/api/accounts", "/api/payments", "/api/transactions", "/api/dashboard/summary",
                "/api/mesh/state", "/api/mesh/routes", "/api/devices", "/api/reconciliation"};
        for (String endpoint : endpoints)
            for (int iteration = 0; iteration < WARMUPS + SAMPLES; iteration++)
                timed(endpoint, "GET", endpoint, null, 200, iteration >= WARMUPS);
        assertEquals(50, count("payments"));
        assertEquals(50, count("transactions"));
        assertEquals(100, count("ledger_entries"));
        assertEquals(0, new BigDecimal("4950.00").compareTo(balance("alice@demo")));
        assertTrue(request("GET", "/api/reconciliation", null, 200).body().path("balanced").asBoolean());
        report.put("duplicateInvariants", "50 unique payments; 50 transactions; 100 ledger entries; Alice debited 50.00; reconciliation balanced");
        report.put("measurementNotes", "Sequential local HTTP; JDBC execution calls including direct JDBC; batches count once; database-internal trigger statements excluded; no forced GC; memory includes test harness; HTTP settlement timing ends after committed response");
        report.put("rawSamples", measurements);
        Path output = Path.of("target", "baseline");
        Files.createDirectories(output);
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("results.json").toFile(), report);
        StringBuilder table = new StringBuilder("| Operation | Samples | Median ms | p95 ms | Median JDBC calls |\n|---|---:|---:|---:|---:|\n");
        measurements.forEach((name, samples) -> {
            var times = samples.stream().mapToDouble(Sample::milliseconds).sorted().toArray();
            var queries = samples.stream().mapToLong(Sample::sqlExecutionCalls).sorted().toArray();
            table.append(String.format(Locale.ROOT, "| %s | %d | %.2f | %.2f | %d |%n", name,
                    samples.size(), (times[(times.length - 1) / 2] + times[times.length / 2]) / 2,
                    times[(int) Math.ceil(times.length * .95) - 1],
                    queries[(queries.length - 1) / 2]));
        });
        Files.writeString(output.resolve("summary.md"), table.toString());
        System.out.println("Local baseline written to " + output.toAbsolutePath());
    }

    private Reply timed(String name, String method, String path, Object body, int status, boolean record) throws Exception {
        Reply reply = request(method, path, body, status);
        if (record) measurements.computeIfAbsent(name, ignored -> new ArrayList<>()).add(reply.sample());
        return reply;
    }

    private Reply request(String method, String path, Object body, int status) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
        if (path.equals("/api/bridge/ingest")) builder.header("X-Bridge-Node-Id", "phone-bridge").header("X-Hop-Count", "4");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        long sqlBefore = SqlCounterConfiguration.EXECUTIONS.get();
        long start = System.nanoTime();
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Sample sample = new Sample((System.nanoTime() - start) / 1_000_000.0,
                SqlCounterConfiguration.EXECUTIONS.get() - sqlBefore, response.statusCode());
        assertEquals(status, response.statusCode(), path + ": " + response.body());
        return new Reply(json.readTree(response.body()), sample);
    }

    private void verifyCommitted(long transactionId) {
        assertEquals("SETTLED", jdbc.queryForObject("SELECT status FROM transactions WHERE id=?", String.class, transactionId));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE transaction_id=?", Integer.class, transactionId));
        assertEquals("SETTLED", jdbc.queryForObject("SELECT p.status FROM payments p JOIN transactions t ON t.payment_id=p.id WHERE t.id=?", String.class, transactionId));
    }

    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private BigDecimal balance(String account) { return jdbc.queryForObject("SELECT balance FROM accounts WHERE vpa=?", BigDecimal.class, account); }
    private static Map<String, Long> memory() {
        var bean = ManagementFactory.getMemoryMXBean();
        return Map.of("heapUsedBytes", bean.getHeapMemoryUsage().getUsed(), "nonHeapUsedBytes", bean.getNonHeapMemoryUsage().getUsed());
    }
    private static Path temporaryKeys() {
        try { return Files.createTempDirectory("meshpay-baseline-keys-"); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    @AfterAll
    static void removeTemporaryKeys() throws java.io.IOException {
        // Delete only immediate files in this run's freshly generated directory.
        try (var files = Files.list(KEY_DIRECTORY)) {
            for (Path file : files.toList()) Files.delete(file);
        }
        Files.delete(KEY_DIRECTORY);
    }
}
