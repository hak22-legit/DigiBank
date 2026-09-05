package com.bank.model.dto;

import com.bank.model.entity.Loan;

import java.util.List;

public class LoanMapper {
    public static LoanDTO toDTO(Loan loan) {
        return LoanDTO.builder()
                .loanId(loan.getLoanId())
                .requestedAmount(loan.getRequestedAmount())
                .approvedAmount(loan.getApprovedAmount())
                .interestRate(loan.getInterestRate())
                .termMonths(loan.getTermMonths())
                .riskLevel(loan.getRiskLevel())
                .status(loan.getStatus())
                .outstandingBalance(loan.getOutstandingBalance())
                .createdAt(loan.getCreatedAt())
                .build();
    }

    public static List<LoanDTO> toDTOList(List<Loan> loans) {
        return loans.stream().map(LoanMapper::toDTO).toList();
    }
}