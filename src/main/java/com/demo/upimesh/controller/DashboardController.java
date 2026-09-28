package com.demo.upimesh.controller;

import com.demo.upimesh.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("summary", dashboard.summary());
        return "dashboard";
    }

    @GetMapping("/api/dashboard/summary")
    @org.springframework.web.bind.annotation.ResponseBody
    public com.demo.upimesh.dto.response.DashboardSummaryResponse summary() {
        return dashboard.summary();
    }

    @GetMapping("/api/dashboard/cashflow")
    @ResponseBody
    public com.demo.upimesh.dto.response.DashboardCashflowResponse cashflow(
            @RequestParam(required = false) String accountVpa) { return dashboard.cashflow(accountVpa); }

    @GetMapping("/api/dashboard/activity")
    @ResponseBody
    public com.demo.upimesh.dto.response.DashboardActivityResponse activity() { return dashboard.activity(); }

    @GetMapping("/api/dashboard/network-stats")
    @ResponseBody
    public com.demo.upimesh.dto.response.DashboardNetworkStatsResponse networkStats() {
        return dashboard.networkStats();
    }

    @GetMapping("/api/dashboard/transaction-volume")
    @ResponseBody
    public com.demo.upimesh.dto.response.DashboardTransactionVolumeResponse transactionVolume() {
        return dashboard.transactionVolume();
    }

    @GetMapping("/api/dashboard/security-events")
    @ResponseBody
    public com.demo.upimesh.dto.response.DashboardSecurityEventsResponse securityEvents() {
        return dashboard.securityEvents();
    }
}
