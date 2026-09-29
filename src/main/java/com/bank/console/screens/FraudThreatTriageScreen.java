package com.bank.console.screens;

import com.bank.controller.AdminController;
import com.bank.model.entity.FraudAlert;

/**
 * Screen alias for FraudTriageScreen (COMPLIANCE OFFICER > FRAUD THREAT TRIAGE & AML AUDIT).
 */
public class FraudThreatTriageScreen extends FraudTriageScreen {

    public FraudThreatTriageScreen() {
        super();
    }

    public FraudThreatTriageScreen(FraudAlert initialAlert) {
        super(initialAlert);
    }

    public FraudThreatTriageScreen(AdminController adminController) {
        super(adminController);
    }

    public FraudThreatTriageScreen(AdminController adminController, FraudAlert initialAlert) {
        super(adminController, initialAlert);
    }
}
