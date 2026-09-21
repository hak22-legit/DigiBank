package com.bank.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnbudgetedCategory {
    private Long categoryId;
    private String name;
    private String classification;
    private BigDecimal totalSpent;
}
