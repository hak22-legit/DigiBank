package com.bank.service;

import com.bank.database.DatabaseConnection;
import com.bank.exception.LoanNotFoundException;
import com.bank.exception.LoanStateException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.entity.Admin;
import com.bank.model.entity.Loan;
import com.bank.model.enums.AdminRole;
import com.bank.model.enums.LoanStatus;
import com.bank.model.repository.AccountRepository;
import com.bank.model.repository.LoanRepository;
import com.bank.model.repository.NotificationRepository;
import com.bank.model.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanApprovalServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationRepository notificationRepository;

    private LoanApprovalService loanApprovalService;
    private Admin loanOfficer;
    private Admin complianceOfficer;

    @BeforeEach
    void setUp() {
        loanApprovalService = new LoanApprovalService(
                loanRepository,
                accountRepository,
                transactionRepository,
                auditLogService,
                notificationRepository
        );

        loanOfficer = Admin.builder()
                .adminId(2L)
                .username("loan_officer")
                .role(AdminRole.LOAN_OFFICER)
                .build();

        complianceOfficer = Admin.builder()
                .adminId(3L)
                .username("compliance")
                .role(AdminRole.COMPLIANCE_OFFICER)
                .build();
    }

    @Test
    @DisplayName("getPendingLoans invokes findPendingLoans on loan repository")
    void testGetPendingLoansSuccess() {
        Loan pendingLoan = Loan.builder()
                .loanId(101L)
                .userId(15L)
                .status(LoanStatus.PENDING)
                .requestedAmount(new BigDecimal("5000.00"))
                .build();

        when(loanRepository.findPendingLoans()).thenReturn(List.of(pendingLoan));

        List<Loan> result = loanApprovalService.getPendingLoans(loanOfficer);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getLoanId());
        verify(loanRepository).findPendingLoans();
    }

    @Test
    @DisplayName("getPendingLoans throws UnauthorizedException for unauthorized roles")
    void testGetPendingLoansUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> loanApprovalService.getPendingLoans(complianceOfficer));
        verify(loanRepository, never()).findPendingLoans();
    }

    @Test
    @DisplayName("rejectLoan updates status to REJECTED, inserts notification, commits transaction, and logs audit")
    void testRejectLoanSuccess() throws SQLException {
        Loan loan = Loan.builder()
                .loanId(10L)
                .userId(3L)
                .status(LoanStatus.PENDING)
                .requestedAmount(new BigDecimal("5000.00"))
                .build();

        Connection mockConn = mock(Connection.class);

        try (MockedStatic<DatabaseConnection> dbMock = mockStatic(DatabaseConnection.class)) {
            dbMock.when(DatabaseConnection::getConnection).thenReturn(mockConn);
            when(loanRepository.findByIdForUpdate(mockConn, 10L)).thenReturn(Optional.of(loan));

            Loan rejected = loanApprovalService.rejectLoan(loanOfficer, 10L, "Credit criteria not met (Score < 600, DTI > 40%)");

            assertNotNull(rejected);
            assertEquals(LoanStatus.REJECTED, rejected.getStatus());
            assertEquals(2L, rejected.getApprovedBy());
            assertEquals("Credit criteria not met (Score < 600, DTI > 40%)", rejected.getRejectionReason());

            verify(loanRepository).updateWithConnection(mockConn, loan);
            verify(notificationRepository).saveWithConnection(
                    eq(mockConn),
                    eq(3L),
                    eq("Loan Application Update"),
                    contains("Your loan request #010 was rejected due to credit criteria."),
                    eq("LOAN_REJECTED")
            );
            verify(mockConn).commit();
            verify(auditLogService).log(eq(2L), eq("REJECT_LOAN"), eq("loans"), eq(10L), contains("Credit criteria not met"));
        }
    }

    @Test
    @DisplayName("rejectLoan throws LoanStateException when loan is not in PENDING status")
    void testRejectLoanNotPendingThrows() throws SQLException {
        Loan activeLoan = Loan.builder()
                .loanId(11L)
                .userId(3L)
                .status(LoanStatus.ACTIVE)
                .build();

        Connection mockConn = mock(Connection.class);

        try (MockedStatic<DatabaseConnection> dbMock = mockStatic(DatabaseConnection.class)) {
            dbMock.when(DatabaseConnection::getConnection).thenReturn(mockConn);
            when(loanRepository.findByIdForUpdate(mockConn, 11L)).thenReturn(Optional.of(activeLoan));

            assertThrows(LoanStateException.class, () ->
                    loanApprovalService.rejectLoan(loanOfficer, 11L, "Adverse action"));

            verify(loanRepository, never()).updateWithConnection(any(), any());
            verify(notificationRepository, never()).saveWithConnection(any(), anyLong(), anyString(), anyString(), anyString());
            verify(mockConn, never()).commit();
        }
    }

    @Test
    @DisplayName("rejectLoan throws LoanNotFoundException when loan ID does not exist")
    void testRejectLoanNotFoundThrows() throws SQLException {
        Connection mockConn = mock(Connection.class);

        try (MockedStatic<DatabaseConnection> dbMock = mockStatic(DatabaseConnection.class)) {
            dbMock.when(DatabaseConnection::getConnection).thenReturn(mockConn);
            when(loanRepository.findByIdForUpdate(mockConn, 999L)).thenReturn(Optional.empty());

            assertThrows(LoanNotFoundException.class, () ->
                    loanApprovalService.rejectLoan(loanOfficer, 999L, "Reason"));

            verify(mockConn, never()).commit();
        }
    }
}
