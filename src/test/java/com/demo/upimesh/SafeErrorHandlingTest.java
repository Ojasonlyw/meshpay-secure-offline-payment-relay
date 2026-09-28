package com.demo.upimesh;

import com.demo.upimesh.controller.BridgeController;
import com.demo.upimesh.controller.MeshController;
import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.exception.*;
import com.demo.upimesh.model.*;
import com.demo.upimesh.service.*;
import com.demo.upimesh.mapper.BridgeMapper;
import com.demo.upimesh.mapper.MeshMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SafeErrorHandlingTest {
    private static final String SECRET = "java.sql.SQLException: SELECT secret FROM accounts; RedisException\n at crypto.decrypt(Crypto.java:42)";
    private final ObjectMapper json = new ObjectMapper();
    private HybridCryptoService crypto;
    private IdempotencyService idempotency;
    private SettlementService settlement;
    private DeviceSignatureService signatures;
    private MeshTopologyService topology;
    private BridgeIngestionService bridge;
    private MeshSimulatorService mesh;
    private MockMvc mvc;
    private ValidatorFactory validators;
    private MeshPacket packet;
    private PaymentInstruction instruction;
    private final String hash = "a".repeat(64);

    @BeforeEach
    void setup() throws Exception {
        crypto = mock(HybridCryptoService.class);
        idempotency = mock(IdempotencyService.class);
        settlement = mock(SettlementService.class);
        signatures = mock(DeviceSignatureService.class);
        topology = mock(MeshTopologyService.class);
        mesh = mock(MeshSimulatorService.class);
        validators = Validation.buildDefaultValidatorFactory();
        bridge = new BridgeIngestionService(crypto, idempotency, settlement,
                validators.getValidator(), lifecycle(), signatures, topology, 86400L, 10, 10, 16384);
        BridgeController bridgeController = new BridgeController(new BridgeService(bridge, new BridgeMapper()));
        MeshController meshController = new MeshController(
                new MeshService(mesh, bridge, idempotency, new MeshMapper(), topology));
        mvc = MockMvcBuilders.standaloneSetup(bridgeController, meshController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter()).build();
        packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setCiphertext("encrypted");
        packet.setCreatedAt(System.currentTimeMillis());
        packet.setTtl(5);
        instruction = new PaymentInstruction(UUID.randomUUID().toString(), "alice@demo", "bob@demo",
                new BigDecimal("1.00"), UUID.randomUUID().toString(), System.currentTimeMillis(), "phone-alice");
        when(crypto.hashCiphertext(anyString())).thenReturn(hash);
        when(crypto.decrypt(anyString())).thenReturn(instruction);
        when(idempotency.claimProcessing(hash)).thenReturn(
                new IdempotencyService.ClaimResult(true, IdempotencyService.PacketState.PROCESSING));
        when(signatures.verifyAndRecord(anyLong(), any())).thenReturn(
                new DeviceSignatureService.Verification(true, null));
    }

    private PaymentLifecycleService lifecycle() {
        PaymentLifecycleService lifecycle = mock(PaymentLifecycleService.class);
        Payment payment = new Payment();
        payment.setId(1L);
        when(lifecycle.register(any(), anyString(), anyString(), anyInt())).thenReturn(payment);
        return lifecycle;
    }

    @AfterEach
    void cleanup() {
        validators.close();
        MDC.clear();
    }

    private MvcResult ingest() throws Exception {
        return mvc.perform(post("/api/bridge/ingest").contentType("application/json")
                .header("X-Trace-Id", "test-trace").content(json.writeValueAsString(packet))).andReturn();
    }

    private void error(MvcResult result, int status, String code) throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals(status, body.get("status").asInt());
        assertEquals(code, body.get("errorCode").asText());
        for (String key : List.of("timestamp", "message", "path", "traceId")) {
            assertTrue(body.hasNonNull(key), key);
        }
        java.time.Instant.parse(body.get("timestamp").asText());
        assertEquals(result.getRequest().getRequestURI(), body.get("path").asText());
        assertEquals(body.get("traceId").asText(), result.getResponse().getHeader("X-Trace-Id"));
        safe(result);
        assertNull(MDC.get("traceId"));
    }

    private void safe(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        for (String forbidden : List.of("java.sql", "SELECT secret", "RedisException",
                "Crypto.java", "crypto.decrypt", "SQLException", "stackTrace")) {
            assertFalse(body.contains(forbidden), body);
        }
    }

    @Test void internalExceptionReturnsSafeFailedResult() throws Exception {
        when(idempotency.claimProcessing(hash)).thenThrow(new RuntimeException(SECRET));
        MvcResult result = ingest();
        assertEquals(200, result.getResponse().getStatus());
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals("FAILED", body.get("outcome").asText());
        assertEquals("PAYMENT_PROCESSING_FAILED", body.get("reason").asText());
        assertTrue(body.get("transactionId").isNull());
        safe(result);
        verify(idempotency, never()).markFailedRetryable(anyString());
    }

    @Test void invalidPacket() throws Exception {
        packet.setTtl(11);
        error(ingest(), 400, "INVALID_PACKET");
        verifyNoInteractions(idempotency);
    }

    @Test void invalidSignatureAndSecondaryRedisFailure() throws Exception {
        when(crypto.decrypt(anyString())).thenThrow(new Exception(SECRET));
        doThrow(new RuntimeException(SECRET)).when(idempotency).markRejected(hash);
        error(ingest(), 400, "INVALID_SIGNATURE");
        verify(idempotency, never()).markFailedRetryable(anyString());
    }

    @Test void expiredPacket() throws Exception {
        instruction.setSignedAt(System.currentTimeMillis() - 86401000L);
        error(ingest(), 400, "PACKET_EXPIRED");
        verify(idempotency).markExpired(hash);
    }

    @Test void finalizedDuplicates() throws Exception {
        for (var state : List.of(IdempotencyService.PacketState.SETTLED,
                IdempotencyService.PacketState.REJECTED, IdempotencyService.PacketState.EXPIRED)) {
            when(idempotency.claimProcessing(hash)).thenReturn(new IdempotencyService.ClaimResult(false, state));
            error(ingest(), 409, "DUPLICATE_PAYMENT");
        }
        verify(idempotency, never()).markFailedRetryable(anyString());
        verifyNoInteractions(settlement);
    }

    @Test void inFlightDuplicatePreservesResult() throws Exception {
        when(idempotency.claimProcessing(hash)).thenReturn(
                new IdempotencyService.ClaimResult(false, IdempotencyService.PacketState.PROCESSING));
        assertEquals("DUPLICATE", json.readTree(ingest().getResponse().getContentAsString()).get("outcome").asText());
    }

    @Test void settlementDomainMappings() throws Exception {
        for (DomainException e : List.of(new AccountNotFoundException(SECRET),
                new PaymentNotFoundException(SECRET), new InsufficientBalanceException(SECRET),
                new SettlementException(SECRET))) {
            doThrow(e).when(settlement).settle(any(), anyString(), anyString(), anyInt());
            error(ingest(), e.getStatus(), e.getErrorCode());
        }
    }

    @Test void settlementFailureWithSecondaryRedisFailure() throws Exception {
        when(settlement.settle(any(), anyString(), anyString(), anyInt())).thenThrow(new RuntimeException(SECRET));
        doThrow(new RuntimeException(SECRET)).when(idempotency).markFailedRetryable(hash);
        error(ingest(), 500, "SETTLEMENT_FAILED");
    }

    @Test void successfulPaymentSurvivesRedisFinalizationFailure() throws Exception {
        Transaction tx = new Transaction();
        tx.setStatus(Transaction.Status.SETTLED);
        when(settlement.settle(any(), anyString(), anyString(), anyInt())).thenReturn(tx);
        doThrow(new RuntimeException(SECRET)).when(idempotency).markSettled(hash);
        var body = json.readTree(ingest().getResponse().getContentAsString());
        assertEquals("SETTLED", body.get("outcome").asText());
        assertTrue(body.get("reason").isNull());
        verify(idempotency, never()).markFailedRetryable(anyString());
    }

    @Test void requestValidationAndMalformedJson() throws Exception {
        packet.setPacketId("bad");
        error(ingest(), 400, "VALIDATION_FAILED");
        error(mvc.perform(post("/api/bridge/ingest").contentType("application/json").content("{"))
                .andReturn(), 400, "VALIDATION_FAILED");
        error(mvc.perform(post("/api/bridge/ingest").header("X-Hop-Count", "not-an-int")
                .contentType("application/json").content(json.writeValueAsString(packet)))
                .andReturn(), 400, "VALIDATION_FAILED");
    }

    @Test void fallbackAndGeneratedTrace() throws Exception {
        when(mesh.collectBridgeUploads()).thenThrow(new RuntimeException(SECRET));
        MvcResult result = mvc.perform(post("/api/mesh/flush")).andReturn();
        error(result, 500, "INTERNAL_SERVER_ERROR");
        UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("traceId").asText());
    }

    @Test void traceIsIncludedInLogsAndPreviousContextIsRestored() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(BridgeIngestionService.class);
        var events = new ArrayList<ch.qos.logback.classic.spi.ILoggingEvent>();
        var appender = new ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                events.add(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        MDC.put("traceId", "previous-context");
        try {
            when(idempotency.claimProcessing(hash)).thenThrow(new RuntimeException(SECRET));
            MvcResult result = ingest();
            assertEquals("test-trace", result.getResponse().getHeader("X-Trace-Id"));
            assertEquals("previous-context", MDC.get("traceId"));
            assertTrue(events.stream().anyMatch(event ->
                    "test-trace".equals(event.getMDCPropertyMap().get("traceId"))
                    && event.getFormattedMessage().contains(hash)
                    && event.getThrowableProxy() != null
                    && SECRET.equals(event.getThrowableProxy().getMessage())));
            safe(result);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test void unsafeTraceHeaderIsReplaced() throws Exception {
        when(mesh.collectBridgeUploads()).thenThrow(new RuntimeException(SECRET));
        MvcResult result = mvc.perform(post("/api/mesh/flush").header("X-Trace-Id", "bad trace " + "x".repeat(200)))
                .andReturn();
        error(result, 500, "INTERNAL_SERVER_ERROR");
        UUID.fromString(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test void successfulResponseIsUnchanged() throws Exception {
        Transaction tx = new Transaction();
        tx.setId(42L);
        tx.setStatus(Transaction.Status.SETTLED);
        when(settlement.settle(any(), anyString(), anyString(), anyInt())).thenReturn(tx);
        MvcResult result = ingest();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(json.readTree("{\"outcome\":\"SETTLED\",\"packetHash\":\"" + hash
                + "\",\"reason\":null,\"transactionId\":42}"),
                json.readTree(result.getResponse().getContentAsString()));
    }

    @Test void meshBatchContinuesAndPropagatesTrace() throws Exception {
        BridgeIngestionService mockBridge = mock(BridgeIngestionService.class);
        MeshController controller = new MeshController(
                new MeshService(mesh, mockBridge, idempotency, new MeshMapper(), topology));
        when(mesh.collectBridgeUploads()).thenReturn(List.of(
                new MeshSimulatorService.BridgeUpload("good", packet, 1),
                new MeshSimulatorService.BridgeUpload("bad", packet, 1)));
        when(mockBridge.ingest(any(), anyString(), anyInt())).thenAnswer(call -> {
            assertEquals("batch-trace", MDC.get("traceId"));
            if ("bad".equals(call.getArgument(1))) throw new InvalidSignatureException(SECRET);
            return new BridgeIngestionService.IngestResult("SETTLED", hash, null, 1L);
        });
        MockMvc batchMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new TraceIdFilter()).build();
        MvcResult result = batchMvc.perform(post("/api/mesh/flush").header("X-Trace-Id", "batch-trace"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uploadsAttempted").value(2))
                .andExpect(jsonPath("$.results.length()").value(2)).andReturn();
        safe(result);
        assertTrue(result.getResponse().getContentAsString().contains("INVALID_SIGNATURE"));
        assertNull(MDC.get("traceId"));
    }
}
