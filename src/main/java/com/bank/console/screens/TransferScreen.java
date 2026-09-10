package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.exception.InsufficientBalanceException;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SCREEN 6: FUND TRANSFER (TRANSACTIONS) (82 Columns)
 */
public class TransferScreen implements Screen {
    private final AccountController accountController;

    public TransferScreen() {
        this(ControllerFactory.getAccountController());
    }

    public TransferScreen(AccountController accountController) {
        this.accountController = accountController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        UserDTO userDto = session.getCurrentUser();
        User userEntity = SessionManager.getCurrentUser();
        if (userDto == null || userEntity == null) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        session.clearScreen();

        List<AccountDTO> accounts;
        try {
            accounts = accountController.getAccountsForUser(userEntity);
        } catch (Exception e) {
            System.out.println(" Failed to load accounts: " + e.getMessage());
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        if (accounts.isEmpty()) {
            System.out.println(" No active accounts available to fund transfer.");
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        // 1. Select Source Account
        AccountDTO sourceAccount = accounts.get(0);
        if (accounts.size() > 1) {
            System.out.println(TUIBox.top(width));
            System.out.println(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SELECT SENDER ACCOUNT"), width));
            System.out.println(TUIBox.divider(width));
            for (int i = 0; i < accounts.size(); i++) {
                AccountDTO acc = accounts.get(i);
                System.out.println(TUIBox.line(String.format("  [%d] %s (%s) — Bal: %s %s",
                        i + 1, acc.getAccountNumber(), acc.getAccountType(),
                        ConsoleFormatter.formatCurrency(acc.getBalance()), acc.getCurrency()), width));
            }
            System.out.println(TUIBox.bottom(width));
            String choice = ConsolePrompt.promptOptional("Select Source Account [1-" + accounts.size() + "]", "1");
            try {
                int idx = Integer.parseInt(choice.trim()) - 1;
                if (idx >= 0 && idx < accounts.size()) sourceAccount = accounts.get(idx);
            } catch (Exception ignored) {}
        }

        // 2. Input Destination Account
        session.clearScreen();
        System.out.println(TUIBox.top(width));
        System.out.println(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > MONEY MOVEMENT > TRANSFER"), width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.line("TRANSACTION DETAILS", width));
        System.out.println(TUIBox.emptyLine(width));
        String sourceLabel = String.format("%s (%s - Bal: %s)",
                sourceAccount.getAccountNumber(), sourceAccount.getAccountType(),
                ConsoleFormatter.formatCurrency(sourceAccount.getBalance()) + " " + sourceAccount.getCurrency());
        System.out.println(TUIBox.line(String.format("  Source Account      : [ %-51s ]", sourceLabel), width));
        System.out.println(TUIBox.line("  Destination Account : [                                                ]", width));
        System.out.println(TUIBox.line("  Beneficiary Name    :   Awaiting account lookup...", width));
        System.out.println(TUIBox.line("  Amount              : [                                                ]", width));
        System.out.println(TUIBox.line("  Category            : [ (3) Bills & Utilities                             ]", width));
        System.out.println(TUIBox.line("  Description / Memo  : [                                                ]", width));
        System.out.println(TUIBox.line("  Idempotency Key     :   Auto-generated upon confirmation", width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.bottom(width));
        System.out.println(" Status: Please enter destination account number (or '0' to cancel)");
        System.out.println(TUIBox.rule(width));

        String destAccNum = ConsolePrompt.promptText("Destination Account (TO)");
        if (destAccNum.isEmpty() || "0".equals(destAccNum) || "cancel".equalsIgnoreCase(destAccNum)) {
            navigator.pop();
            return;
        }

        Account destAccount;
        String beneficiaryName = "UNKNOWN BENEFICIARY";
        try {
            destAccount = accountController.findAccountByNumber(destAccNum.trim());
            if (destAccount.getAccountId().equals(sourceAccount.getAccountId())) {
                System.out.println(ConsoleTheme.error(" Error: Source and destination accounts cannot be identical."));
                ConsolePrompt.pause();
                return;
            }
            Optional<User> benUser = ControllerFactory.getUserRepository().findById(destAccount.getUserId());
            if (benUser.isPresent()) {
                beneficiaryName = benUser.get().getFullName();
            }
        } catch (Exception e) {
            System.out.println(ConsoleTheme.error(" Recipient account not found: " + destAccNum));
            ConsolePrompt.pause();
            return;
        }

        // 3. Prompt Amount
        BigDecimal amount = ConsolePrompt.promptAmount("Amount (" + sourceAccount.getCurrency() + ")");

        // 4. Prompt Category
        String catChoice = ConsolePrompt.promptOptional("Category [1: Food, 2: Shopping, 3: Bills & Utilities, 4: Entertainment, 5: Other]", "3");
        String categoryName = switch (catChoice.trim()) {
            case "1" -> "(1) Food & Dining";
            case "2" -> "(2) Shopping";
            case "4" -> "(4) Entertainment";
            case "5" -> "(5) Other";
            default -> "(3) Bills & Utilities";
        };

        // 5. Prompt Description / Memo
        String memo = ConsolePrompt.promptOptional("Description / Memo", "Transfer to " + beneficiaryName);

        // 6. Generate Idempotency Key
        UUID idempotencyKey = UUID.randomUUID();

        // 7. Render Screen 6 Confirmation Box
        session.clearScreen();
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String amountStr = df.format(amount) + " " + sourceAccount.getCurrency();

        System.out.println(TUIBox.top(width));
        System.out.println(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > MONEY MOVEMENT > TRANSFER"), width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.line("TRANSACTION DETAILS", width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.line(String.format("  Source Account      : [ %-51s ]", sourceLabel), width));
        System.out.println(TUIBox.line(String.format("  Destination Account : [ %-51s ]", destAccount.getAccountNumber()), width));
        System.out.println(TUIBox.line(String.format("  Beneficiary Name    :   %s %s", beneficiaryName.toUpperCase(), ConsoleTheme.success("(Resolved ✔)")), width));
        System.out.println(TUIBox.line(String.format("  Amount              : [ %-51s ]", df.format(amount)), width));
        System.out.println(TUIBox.line(String.format("  Category            : [ %-51s ]", categoryName), width));
        System.out.println(TUIBox.line(String.format("  Description / Memo  : [ %-51s ]", memo), width));
        System.out.println(TUIBox.line(String.format("  Idempotency Key     :   %s (Auto)", idempotencyKey), width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.line("SUMMARY BREAKDOWN", width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.line(String.format("  Transfer Amount     : $ %s", amountStr), width));
        System.out.println(TUIBox.line(String.format("  Transaction Fee     : $   0.00 %s", sourceAccount.getCurrency()), width));
        System.out.println(TUIBox.line(String.format("  Total Deducted      : $ %s", amountStr), width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.line("  ► " + ConsoleTheme.highlight("[EXECUTE TRANSACTION]") + "                   " + ConsoleTheme.muted("[CANCEL]"), width));
        System.out.println(TUIBox.bottom(width));
        System.out.println(" Isolation: READ COMMITTED | Automatic rollback on balance deficit.");
        System.out.println(TUIBox.rule(width));

        boolean confirm = ConsolePrompt.promptConfirmation("Execute transaction and debit account?");
        if (!confirm) {
            System.out.println(" Transfer cancelled by user.");
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        // Execute Transfer
        try {
            Transaction txn = ControllerFactory.getAccountService().transfer(
                    sourceAccount.getAccountId(),
                    destAccount.getAccountId(),
                    amount,
                    sourceAccount.getCurrency(),
                    memo,
                    idempotencyKey,
                    userEntity
            );

            session.clearScreen();
            System.out.println(TUIBox.top(width));
            System.out.println(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > TRANSFER COMPLETED"), width));
            System.out.println(TUIBox.divider(width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.center(ConsoleTheme.success("✔ Funds Transferred Successfully!"), width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.line("  Transaction ID : #" + txn.getTransactionId(), width));
            System.out.println(TUIBox.line("  Beneficiary    : " + ConsoleTheme.highlight(beneficiaryName), width));
            System.out.println(TUIBox.line("  Account Debited: " + sourceAccount.getAccountNumber(), width));
            System.out.println(TUIBox.line("  Amount Debited : " + ConsoleTheme.error("-" + ConsoleFormatter.formatCurrency(amount) + " " + sourceAccount.getCurrency()), width));
            System.out.println(TUIBox.line("  Status         : " + ConsoleTheme.success("COMPLETED"), width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.bottom(width));
            System.out.println(" Status: Transfer committed to ledger.");
            System.out.println(TUIBox.rule(width));

        } catch (InsufficientBalanceException e) {
            System.out.println(ConsoleTheme.error(" Transfer Denied: Insufficient balance: " + e.getMessage()));
        } catch (Exception e) {
            System.out.println(ConsoleTheme.error(" Transfer Error: " + e.getMessage()));
        }

        ConsolePrompt.pause("Press Enter to return to Dashboard...");
        navigator.pop();
    }
}

