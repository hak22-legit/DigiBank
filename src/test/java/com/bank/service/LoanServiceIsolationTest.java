package com.bank.service;

import com.bank.model.entity.Loan;
import com.bank.model.entity.LoanPayment;
import com.bank.model.entity.User;
import com.bank.model.enums.LoanPaymentStatus;
import com.bank.model.enums.LoanStatus;
import com.bank.model.repository.LoanPaymentRepository;
import com.bank.model.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceIsolationTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private RiskAssessmentService riskAssessmentService;

    @Mock
    private LoanPaymentRepository loanPaymentRepository;

    private LoanService loanService;
    private User testUser;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, riskAssessmentService, loanPaymentRepository);
        testUser = User.builder().userId(10L).username("fresh_user").build();
    }

    @Test
    @DisplayName("Fresh account with no loans returns empty repayment schedule without calling payment repository")
    void testFreshAccountReturnsEmptySchedule() {
        when(loanRepository.findActiveLoanByUserId(10L)).thenReturn(Optional.empty());

        List<LoanPayment> schedule = loanService.getRepaymentSchedule(testUser);

        assertNotNull(schedule);
        assertTrue(schedule.isEmpty());
        verify(loanPaymentRepository, never()).findByLoanId(anyLong());
    }

    @Test
    @DisplayName("Account with only PENDING or REJECTED loans returns empty repayment schedule")
    void testPendingLoanReturnsEmptySchedule() {
        when(loanRepository.findActiveLoanByUserId(10L)).thenReturn(Optional.empty());

        List<LoanPayment> schedule = loanService.getRepaymentSchedule(testUser);

        assertNotNull(schedule);
        assertTrue(schedule.isEmpty());
        verify(loanPaymentRepository, never()).findByLoanId(anyLong());
    }

    @Test
    @DisplayName("User with ACTIVE loan returns payment schedule")
    void testActiveLoanReturnsSchedule() {
        Loan activeLoan = Loan.builder()
                .loanId(50L)
                .userId(10L)
                .status(LoanStatus.ACTIVE)
                .build();

        LoanPayment payment = LoanPayment.builder()
                .paymentId(101L)
                .loanId(50L)
                .amount(new BigDecimal("50.00"))
                .status(LoanPaymentStatus.COMPLETED)
                .dueDate(LocalDate.now())
                .build();

        when(loanRepository.findActiveLoanByUserId(10L)).thenReturn(Optional.of(activeLoan));
        when(loanPaymentRepository.findByLoanId(50L)).thenReturn(List.of(payment));

        List<LoanPayment> schedule = loanService.getRepaymentSchedule(testUser);

        assertNotNull(schedule);
        assertEquals(1, schedule.size());
        assertEquals(new BigDecimal("50.00"), schedule.get(0).getAmount());
        verify(loanPaymentRepository).findByLoanId(50L);
    }
}
