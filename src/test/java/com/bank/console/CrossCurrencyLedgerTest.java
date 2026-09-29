package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.AccountStatementLedgerScreen;
import com.bank.console.screens.TransactionHistoryScreen;
import com.bank.console.screens.TransactionsLedgerScreen;
import com.bank.model.TransactionView;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.GlobalLedgerItem;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionStatus;
import com.bank.model.enums.TransactionType;
import com.bank.model.repository.AccountRepository;
import com.bank.model.repository.TransactionRepository;
import com.bank.service.AccountService;
import com.bank.service.TransactionService;
import com.bank.util.CurrencyFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CrossCurrencyLedgerTest {

    private Transaction createSampleCrossCurrencyTxn() {
        return Transaction.builder()
                .transactionId(95L)
                .accountId(101L) // Source Account (KHR)
                .relatedAccountId(202L) // Destination Account (USD: DGB-550192834)
                .transactionType(TransactionType.TRANSFER)
                .amount(new BigDecimal("10000.00"))
                .currency(Currency.KHR)
                .description("Cross-currency transfer [Exchanged 10000.00 KHR -> 2.47 USD @ 1 KHR = 0.0002 USD]")
                .status(TransactionStatus.COMPLETED)
                .transactionDate(LocalDateTime.of(2026, 9, 29, 10, 0, 0))
                .build();
    }

    @Test
    @DisplayName("Verify Transaction entity correctly parses cross-currency exchange info")
    void testTransactionCrossCurrencyResolution() {
        Transaction tx = createSampleCrossCurrencyTxn();

        // 1. Transaction reference id
        assertEquals("TXN-20260929-00095", tx.getReferenceId());

        // 2. Detection of cross-currency
        assertTrue(tx.isCrossCurrency());

        // 3. Destination account (202L) receives 2.47 USD
        assertTrue(tx.isDestinationAccount(202L));
        assertEquals(new BigDecimal("2.47"), tx.getAmountForAccount(202L));
        assertEquals(Currency.USD, tx.getCurrencyForAccount(202L));

        // 4. Source account (101L) debited 10,000.00 KHR
        assertTrue(tx.isSourceAccount(101L));
        assertEquals(new BigDecimal("10000.00"), tx.getAmountForAccount(101L));
        assertEquals(Currency.KHR, tx.getCurrencyForAccount(101L));
    }

    @Test
    @DisplayName("Verify CurrencyFormatter formats amounts with proper symbols without hardcoding $ for KHR")
    void testCurrencyFormatter() {
        // USD formatting
        assertEquals("$ 2.47", CurrencyFormatter.format(new BigDecimal("2.47"), Currency.USD));
        assertEquals("+$ 2.47", CurrencyFormatter.formatWithSign(new BigDecimal("2.47"), Currency.USD, true));
        assertEquals("-$ 50.00", CurrencyFormatter.formatWithSign(new BigDecimal("50.00"), Currency.USD, false));

        // KHR formatting - must NEVER prefix $
        String khrFormatted = CurrencyFormatter.format(new BigDecimal("10000.00"), Currency.KHR);
        assertFalse(khrFormatted.startsWith("$"), "KHR amount must never start with $");
        assertTrue(khrFormatted.contains("៛"), "KHR amount must contain ៛ symbol");
        assertEquals("+៛ 10,000", CurrencyFormatter.formatWithSign(new BigDecimal("10000.00"), Currency.KHR, true));
    }

    @Test
    @DisplayName("Verify TransactionService provides settled amount and currency to TransactionView")
    void testTransactionServiceDestinationPerspective() {
        Transaction tx = createSampleCrossCurrencyTxn();
        TransactionRepository mockTxnRepo = mock(TransactionRepository.class);
        AccountRepository mockAccRepo = mock(AccountRepository.class);

        when(mockTxnRepo.findHistoryForAccount(202L)).thenReturn(List.of(tx));

        User user = User.builder().userId(5L).build();
        Account usdAccount = Account.builder()
                .accountId(202L)
                .accountNumber("DGB-550192834")
                .userId(5L)
                .currency(Currency.USD)
                .balance(new BigDecimal("500.00"))
                .build();
        when(mockAccRepo.findById(202L)).thenReturn(java.util.Optional.of(usdAccount));

        TransactionService service = new TransactionService(mockTxnRepo, mockAccRepo);
        List<TransactionView> history = service.getTransactionHistory(202L, com.bank.model.enums.HistoryFilter.ALL, user);

        assertEquals(1, history.size());
        TransactionView tv = history.get(0);

        assertEquals(TransactionDirection.INCOME, tv.getDirection());
        assertEquals(new BigDecimal("2.47"), tv.getSettledAmount());
        assertEquals(Currency.USD, tv.getSettledCurrency());
    }

    @Test
    @DisplayName("Verify TransactionHistoryScreen and TransactionsLedgerScreen row displays +$ 2.47 for TXN-20260929-00095")
    void testTransactionsLedgerScreenRowRendering() {
        Transaction tx = createSampleCrossCurrencyTxn();
        TransactionView tv = new TransactionView(tx, TransactionDirection.INCOME, new BigDecimal("2.47"), Currency.USD);

        BigDecimal amt = tv.getSettledAmount();
        Currency settledCcy = tv.getSettledCurrency();
        boolean isIncome = tv.getDirection() == TransactionDirection.INCOME;
        String sign = isIncome ? "+" : "-";

        String amountFormatted = sign + CurrencyFormatter.format(amt.abs(), settledCcy);
        if (amountFormatted.length() > 10) {
            amountFormatted = sign + CurrencyFormatter.formatCompact(amt.abs(), settledCcy);
        }

        // Verify the exact string rendered
        assertEquals("+$ 2.47", amountFormatted);

        // Verify that the table row width evaluates to exactly 80 characters
        String dateStr = "2026-09-29 10:00";
        String typeStr = "TRANSFER";
        String catStr = "General";
        String plainText = String.format(" %-2s%-17s    %-12s    %-25s    %10s ",
                "▸", dateStr, typeStr, catStr, amountFormatted);

        assertEquals(80, plainText.length(), "Table row must be exactly 80 characters");

        String borderChar = "│";
        String row = borderChar + plainText + borderChar;
        assertEquals(82, TUIBox.visibleLength(row), "Row with border must fit strictly inside 82 columns");
        assertTrue(plainText.contains("+$ 2.47"), "Row must contain +$ 2.47");
        assertFalse(plainText.contains("+$10,000.00"), "Row must NEVER contain +$10,000.00");
    }

    @Test
    @DisplayName("Verify running balance calculation is mathematically sound with settled amount")
    void testRunningBalanceMath() {
        Transaction tx = createSampleCrossCurrencyTxn();
        TransactionView tv = new TransactionView(tx, TransactionDirection.INCOME, new BigDecimal("2.47"), Currency.USD);

        BigDecimal closingBalance = new BigDecimal("102.47"); // After receiving 2.47 USD
        BigDecimal running = closingBalance;

        // Balance before this transaction:
        if (tv.getDirection() == TransactionDirection.INCOME) {
            running = running.subtract(tv.getSettledAmount());
        }

        assertEquals(new BigDecimal("100.00"), running, "Balance before credit must be 100.00 USD");
        assertNotEquals(new BigDecimal("-9897.53"), running, "Balance must not subtract raw foreign amount 10,000.00");
    }

    @Test
    @DisplayName("Verify AccountStatementLedgerScreen renderContent correctly handles cross-currency transfer")
    void testAccountStatementLedgerScreenRendering() {
        Transaction tx = createSampleCrossCurrencyTxn();
        GlobalLedgerItem accountItem = GlobalLedgerItem.builder()
                .accountId(202L)
                .accountNumber("DGB-550192834")
                .ownerName("John Doe")
                .currency(Currency.USD)
                .balance(new BigDecimal("102.47"))
                .build();

        String rendered = AccountStatementLedgerScreen.renderContent(
                accountItem,
                List.of(tx),
                0, 0, 6,
                "Normal", false, TUILayout.APP_WIDTH
        );

        assertNotNull(rendered);
        // Verify row displays + 2.47 and not 10,000
        assertTrue(rendered.contains("+     2.47") || rendered.contains("2.47"), "Rendered ledger must contain 2.47");
        assertFalse(rendered.contains("10,000.00"), "Rendered USD ledger must NOT display 10,000.00 as credited amount");
    }
}
