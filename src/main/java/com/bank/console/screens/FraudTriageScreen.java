package com.bank.console.screens;

import com.bank.controller.AdminController;
import com.bank.model.entity.FraudAlert;

/**
 * Fraud Threat Triage Console Screen.
 * Alias for FraudInvestigationScreen providing unified threat triage operations.
 */
public class FraudTriageScreen extends FraudInvestigationScreen {
    public FraudTriageScreen() {
        super();
    }

    public FraudTriageScreen(FraudAlert alert) {
        super(alert);
    }

    public FraudTriageScreen(AdminController adminController, FraudAlert alert) {
        super(adminController, alert);
    }
}
