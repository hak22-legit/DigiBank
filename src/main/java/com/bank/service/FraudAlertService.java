package com.bank.service;

import com.bank.model.entity.Account;
import com.bank.model.entity.Admin;
import com.bank.model.entity.FraudAlert;

import java.util.List;

/**
 * Service interface for Fraud Alert lifecycle and mitigation workflows.
 */
public interface FraudAlertService {
    List<FraudAlert> getOpenAlerts(Admin complianceOfficer);
    List<FraudAlert> getAllAlerts(Admin complianceOfficer);
    FraudAlert investigateAlert(Admin complianceOfficer, Long alertId);
    FraudAlert resolveAlert(Admin complianceOfficer, Long alertId, String resolutionNotes, boolean confirmedFraud);
    Account freezeAccount(Admin complianceOfficer, Long accountId, String reason);
    Account unfreezeAccount(Admin complianceOfficer, Long accountId);
}
