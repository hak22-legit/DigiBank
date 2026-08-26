package com.bank.model;

import com.bank.enums.TransactionDirection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TransactionView {
    private final Transaction transaction;
    private final TransactionDirection direction;
}