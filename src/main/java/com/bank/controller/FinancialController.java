package com.bank.controller;

import com.bank.model.FinancialDashboard;
import com.bank.model.FinancialInsights;
import com.bank.model.entity.User;
import com.bank.service.DashboardService;
import com.bank.service.FinancialInsightsService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FinancialController {
    private final FinancialInsightsService insightsService;
    private final DashboardService dashboardService;

    public FinancialInsights getInsights(User user) {
        return insightsService.getCurrentMonthInsights(user);
    }

    public FinancialDashboard getDashboard(User user) {
        return dashboardService.buildDashboard(user);
    }
}
