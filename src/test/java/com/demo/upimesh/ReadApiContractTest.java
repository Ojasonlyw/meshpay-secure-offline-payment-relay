package com.demo.upimesh;

import com.demo.upimesh.controller.AccountController;
import com.demo.upimesh.controller.TransactionController;
import com.demo.upimesh.dto.response.AccountResponse;
import com.demo.upimesh.dto.response.TransactionResponse;
import com.demo.upimesh.service.AccountService;
import com.demo.upimesh.service.TransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadApiContractTest {
    @Test
    void dashboardReadRoutesExposeStableEmptyFields() throws Exception {
        var service = mock(com.demo.upimesh.service.DashboardService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new com.demo.upimesh.controller.DashboardController(service)).build();
        var zero = BigDecimal.ZERO;
        var point = new com.demo.upimesh.dto.response.DashboardMetricPointResponse(
                "2026-01-01", zero, zero, zero, 0, 0, 0, zero);
        when(service.cashflow(null)).thenReturn(new com.demo.upimesh.dto.response.DashboardCashflowResponse(
                null, zero, zero, zero, zero, zero, List.of(point)));
        when(service.activity()).thenReturn(new com.demo.upimesh.dto.response.DashboardActivityResponse(List.of()));
        when(service.networkStats()).thenReturn(new com.demo.upimesh.dto.response.DashboardNetworkStatsResponse(
                0, 0, 0, 0, 0, 0, 0, 0, 0, null, null));
        when(service.transactionVolume()).thenReturn(new com.demo.upimesh.dto.response.DashboardTransactionVolumeResponse(
                0, 0, 0, zero, List.of(point)));
        when(service.securityEvents()).thenReturn(new com.demo.upimesh.dto.response.DashboardSecurityEventsResponse(
                0, 0, 0, List.of()));
        mvc.perform(get("/api/dashboard/cashflow")).andExpect(status().isOk())
                .andExpect(jsonPath("$.netMovement").value(0))
                .andExpect(jsonPath("$.points[0].bucket").value("2026-01-01"));
        mvc.perform(get("/api/dashboard/activity")).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/dashboard/network-stats")).andExpect(jsonPath("$.activeConnections").value(0))
                .andExpect(jsonPath("$.packetsRouted").value(0));
        mvc.perform(get("/api/dashboard/transaction-volume")).andExpect(jsonPath("$.settledAmount").value(0))
                .andExpect(jsonPath("$.points[0].settledCount").value(0));
        mvc.perform(get("/api/dashboard/security-events")).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.failedSignatureVerification").value(0));
    }
    @Test
    void reconciliationReturnsEmptySnapshotAndSafeDatabaseErrors() throws Exception {
        var service = mock(com.demo.upimesh.service.ReconciliationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(
                new com.demo.upimesh.controller.ReconciliationController(service))
                .setControllerAdvice(new com.demo.upimesh.exception.GlobalExceptionHandler()).build();
        when(service.reconcile()).thenReturn(new com.demo.upimesh.dto.response.ReconciliationResponse(
                true, Instant.parse("2026-01-01T00:00:00Z"), List.of()));
        mvc.perform(get("/api/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.accounts").isEmpty())
                .andExpect(jsonPath("$.checkedAt").exists());
        when(service.reconcile()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException(
                "SELECT secret FROM accounts"));
        mvc.perform(get("/api/reconciliation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT secret"))));
    }

    @Test
    void dashboardReadRoutesKeepTheirJsonFields() throws Exception {
        AccountService accounts = mock(AccountService.class);
        TransactionService transactions = mock(TransactionService.class);
        when(accounts.listAccounts()).thenReturn(List.of(
                new AccountResponse("alice@demo", "Alice", new BigDecimal("5000.00"), 0L)));
        when(transactions.listRecentTransactions()).thenReturn(List.of(new TransactionResponse(
                42L, "hash", "alice@demo", "bob@demo", new BigDecimal("25.00"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:01:00Z"),
                "phone-bridge", 1, "SETTLED", java.util.UUID.randomUUID())));
        var mvc = MockMvcBuilders.standaloneSetup(
                new AccountController(accounts), new TransactionController(transactions)).build();

        mvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vpa").value("alice@demo"))
                .andExpect(jsonPath("$[0].holderName").value("Alice"))
                .andExpect(jsonPath("$[0].balance").value(5000.00));
        mvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].senderVpa").value("alice@demo"))
                .andExpect(jsonPath("$[0].receiverVpa").value("bob@demo"))
                .andExpect(jsonPath("$[0].status").value("SETTLED"))
                .andExpect(jsonPath("$[0].bridgeNodeId").value("phone-bridge"));
    }
}
