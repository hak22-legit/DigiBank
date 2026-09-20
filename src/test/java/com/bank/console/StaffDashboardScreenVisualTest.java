package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.*;
import com.bank.model.dto.AdminDTO;
import com.bank.model.entity.Admin;
import com.bank.model.entity.FraudAlert;
import com.bank.model.entity.Loan;
import com.bank.model.enums.AdminRole;
import com.bank.model.enums.AdminStatus;
import com.bank.model.enums.FraudStatus;
import com.bank.model.enums.RiskLevel;
import com.bank.service.LoanService.LoanPipelineStats;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StaffDashboardScreenVisualTest {

    @BeforeAll
    static void setup() {
        ControllerFactory.init();
    }

    @Test
    @DisplayName("Verify Loan Officer Dashboard strictly adheres to 82 columns with pipeline radar and actions")
    void testLoanOfficerDashboardLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        AdminDTO loanOfficer = AdminDTO.builder()
                .adminId(4L)
                .username("loan1")
                .role(AdminRole.LOAN_OFFICER)
                .status(AdminStatus.ACTIVE)
                .build();

        LoanPipelineStats stats = new LoanPipelineStats(
                7,
                new BigDecimal("34500.00"),
                2,
                new BigDecimal("12000.00"),
                1
        );

        String rendered = StaffDashboardScreen.renderLoanOfficerContent(
                loanOfficer,
                stats,
                0,
                "Ready. 7 credit facilities require underwriting decision.",
                width
        );

        assertNotNull(rendered);
        String[] lines = rendered.split("\n");
        assertTrue(lines.length > 10, "Dashboard should contain multiple rows");

        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i];
            int visibleLen = TUIBox.visibleLength(line);
            assertEquals(82, visibleLen, String.format("Line %d exceeds 82 columns: %s (len=%d)", i + 1, line, visibleLen));
        }

        String stripped = TUIBox.stripAnsi(rendered);
        assertTrue(stripped.contains("ROLE: LOAN_OFFICER"));
        assertTrue(stripped.contains("PENDING UNDERWRITING PIPELINE"));
        assertTrue(stripped.contains("Applications in Queue: 7 Requests"));
        assertTrue(stripped.contains("Total Volume Pending : $ 34,500.00 USD"));
        assertTrue(stripped.contains("Approved Today : 2 ($12,000.00)"));
        assertTrue(stripped.contains("Rejected Today : 1 Application"));
        assertTrue(stripped.contains("Review Loan Underwriting Queue (7 Pending)"));
        assertTrue(stripped.contains("Search Customer Borrowing History & Profiles"));
        assertTrue(stripped.contains("Portfolio Performance & Active Loan Book"));
        assertTrue(stripped.contains("[0] Sign Out & Terminate Session"));
    }

    @Test
    @DisplayName("Verify Compliance Officer Dashboard table does not overflow and stays strictly at 82 columns")
    void testComplianceOfficerDashboardBorderOverflowFix() {
        int width = TUILayout.APP_WIDTH;

        AdminDTO complianceOfficer = AdminDTO.builder()
                .adminId(6L)
                .username("haks")
                .role(AdminRole.COMPLIANCE_OFFICER)
                .status(AdminStatus.ACTIVE)
                .build();

        List<FraudAlert> alerts = new ArrayList<>();
        alerts.add(FraudAlert.builder()
                .alertId(1L)
                .userId(3L)
                .riskLevel(RiskLevel.HIGH)
                .status(FraudStatus.CONFIRMED_FRAUD)
                .description("High-value transfer: $15,000.00 to overseas account")
                .build());

        String rendered = StaffDashboardScreen.renderComplianceOfficerContent(
                complianceOfficer,
                0,
                2,
                1,
                0,
                94,
                alerts,
                0,
                "Surveillance active. 1 confirmed threat record logged.",
                width
        );

        assertNotNull(rendered);
        String[] lines = rendered.split("\n");

        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i];
            int visibleLen = TUIBox.visibleLength(line);
            assertEquals(82, visibleLen, String.format("Compliance dashboard line %d overflowed (len=%d): %s", i + 1, visibleLen, line));
            String stripped = TUIBox.stripAnsi(line);
            assertTrue(stripped.startsWith("│") || stripped.startsWith("┌") || stripped.startsWith("├") || stripped.startsWith("└"),
                    "Each box line must start with proper border character");
            assertTrue(stripped.endsWith("│") || stripped.endsWith("┐") || stripped.endsWith("┤") || stripped.endsWith("┘"),
                    "Each box line must end with proper border character");
        }

        assertTrue(rendered.contains("ROLE: COMPLIANCE_OFFICER"));
        assertTrue(rendered.contains("FRAUD ALERTS & AML SURVEILLANCE RADAR"));
        assertTrue(rendered.contains("#ALT-01"));
        assertTrue(rendered.contains("#USR-03"));
        assertTrue(rendered.contains("CONFIRMED_FRAUD"));
        assertTrue(rendered.contains("Triage Fraud Alerts & Manage Account Holds"));
    }

    @Test
    @DisplayName("Verify Staff Provisioning Form in StaffManagementScreen conforms strictly to 82 columns")
    void testStaffProvisioningFormLayout() {
        int width = TUILayout.APP_WIDTH;

        List<Admin> staffList = new ArrayList<>();
        staffList.add(Admin.builder().adminId(1L).username("superadmin").role(AdminRole.SUPER_ADMIN).status(AdminStatus.ACTIVE).build());
        staffList.add(Admin.builder().adminId(2L).username("compliance1").role(AdminRole.COMPLIANCE_OFFICER).status(AdminStatus.ACTIVE).build());
        staffList.add(Admin.builder().adminId(3L).username("loan1").role(AdminRole.LOAN_OFFICER).status(AdminStatus.ACTIVE).build());
        staffList.add(Admin.builder().adminId(4L).username("loan").role(AdminRole.LOAN_OFFICER).status(AdminStatus.ACTIVE).build());

        String rendered = StaffManagementScreen.renderProvisioningContent(
                staffList,
                "vathanaka_loan",
                true,
                "SecretPass123!",
                "Chan Vathanaka",
                "vathanaka@digibank.com.kh",
                "+855 12 345 678",
                4, // Focused on email
                "Enter corporate email address. Press [Tab] to proceed to Phone.",
                false,
                width
        );

        assertNotNull(rendered);
        String[] lines = rendered.split("\n");

        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i];
            int visibleLen = TUIBox.visibleLength(line);
            assertEquals(82, visibleLen, String.format("Staff provisioning line %d overflowed (len=%d): %s", i + 1, visibleLen, line));
        }

        assertTrue(rendered.contains("PROVISION NEW STAFF CREDENTIALS"));
        assertTrue(rendered.contains("vathanaka_loan"));
        assertTrue(rendered.contains("LOAN_OFFICER"));
        assertTrue(rendered.contains("••••••••••••••")); // Password masked (14 characters)
        assertTrue(rendered.contains("Chan Vathanaka"));
        assertTrue(rendered.contains("vathanaka@digibank.com.kh"));
        assertTrue(rendered.contains("+855 12 345 678"));
        assertTrue(rendered.contains("[Enter] Confirm & Provision Account"));
        assertTrue(rendered.contains("[Esc] Cancel"));
    }

    @Test
    @DisplayName("Verify BorrowingHistoryScreen and ActiveLoanBookScreen strictly conform to 82 columns")
    void testOfficerSubScreensLayout() {
        int width = TUILayout.APP_WIDTH;

        List<Loan> sampleLoans = new ArrayList<>();
        sampleLoans.add(Loan.builder()
                .loanId(101L)
                .userId(3L)
                .requestedAmount(new BigDecimal("10000.00"))
                .approvedAmount(new BigDecimal("10000.00"))
                .outstandingBalance(new BigDecimal("8500.00"))
                .termMonths(12)
                .interestRate(new BigDecimal("8.50"))
                .status(com.bank.model.enums.LoanStatus.ACTIVE)
                .build());

        String renderedHistory = BorrowingHistoryScreen.renderContent(
                "3",
                3L,
                null,
                sampleLoans,
                "Found 1 loan record.",
                false,
                width
        );

        String[] histLines = renderedHistory.split("\n");
        for (int i = 0; i < histLines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(histLines[i]), "BorrowingHistoryScreen line must be 82 cols");
        }

        String renderedBook = ActiveLoanBookScreen.renderContent(
                sampleLoans,
                "Portfolio synchronized.",
                false,
                width
        );

        String[] bookLines = renderedBook.split("\n");
        for (int i = 0; i < bookLines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(bookLines[i]), "ActiveLoanBookScreen line must be 82 cols");
        }
    }
}
