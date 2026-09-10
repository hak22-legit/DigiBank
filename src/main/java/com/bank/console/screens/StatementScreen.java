package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.ReportController;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Screen for generating and exporting JasperReports PDF bank statements.
 */
public class StatementScreen implements Screen {
    private final ReportController reportController;
    private final AccountController accountController;
    private String statusMessage;
    private boolean isErrorStatus;

    public StatementScreen() {
        this(ControllerFactory.getReportController(), ControllerFactory.getAccountController());
    }

    public StatementScreen(ReportController reportController) {
        this(reportController, ControllerFactory.getAccountController());
    }

    public StatementScreen(ReportController reportController, AccountController accountController) {
        this.reportController = reportController;
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

        session.clearScreen();
        TUILayout.printHeader(userDto.getFullName());
        TUILayout.printScreenTitle("Export Account Statement (PDF)");

        if (statusMessage != null) {
            TUILayout.printAlert(statusMessage, isErrorStatus);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            statusMessage = null;
        }

        List<AccountDTO> accounts;
        try {
            accounts = accountController.getAccountsForUser(userEntity);
        } catch (Exception e) {
            TUILayout.printAlert("Failed to load accounts: " + e.getMessage(), true);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        if (accounts.isEmpty()) {
            TUILayout.printAlert("No bank accounts found to export statements.", true);
            TUILayout.printFooter("Press Enter to return");
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        // 1. Select Account
        System.out.println(TUIBox.line(ConsoleTheme.info("Select account to generate statement for:"), TUILayout.APP_WIDTH));
        for (int i = 0; i < accounts.size(); i++) {
            AccountDTO acc = accounts.get(i);
            System.out.println(TUIBox.line(String.format("  [%d] %s (%s) — Balance: %s %s",
                    i + 1, acc.getAccountNumber(), acc.getAccountType(),
                    ConsoleFormatter.formatCurrency(acc.getBalance()), acc.getCurrency()), TUILayout.APP_WIDTH));
        }
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        String choice = ConsolePrompt.promptOptional("Choose Account [1-" + accounts.size() + "]", "1");
        int idx;
        try {
            idx = Integer.parseInt(choice) - 1;
            if (idx < 0 || idx >= accounts.size()) throw new IndexOutOfBoundsException();
        } catch (Exception e) {
            this.statusMessage = "Invalid account selection.";
            this.isErrorStatus = true;
            return;
        }
        AccountDTO targetAccount = accounts.get(idx);

        // 2. Select Date Range
        LocalDate now = LocalDate.now();
        LocalDate defaultStart = now.minusDays(30);
        String defaultStartStr = defaultStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String defaultEndStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line(ConsoleTheme.muted("Specify statement date period (format: yyyy-MM-dd)"), TUILayout.APP_WIDTH));
        String startStr = ConsolePrompt.promptOptional("Start Date", defaultStartStr);
        String endStr = ConsolePrompt.promptOptional("End Date", defaultEndStr);

        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(startStr.trim());
            endDate = LocalDate.parse(endStr.trim());
            if (endDate.isBefore(startDate)) {
                this.statusMessage = "End date cannot be earlier than start date.";
                this.isErrorStatus = true;
                return;
            }
        } catch (Exception e) {
            this.statusMessage = "Invalid date format. Expected yyyy-MM-dd.";
            this.isErrorStatus = true;
            return;
        }

        LocalDateTime fromDateTime = startDate.atStartOfDay();
        LocalDateTime toDateTime = endDate.atTime(LocalTime.MAX);

        // 3. Review Box
        session.clearScreen();
        TUILayout.printHeader("Review Statement");
        TUILayout.printScreenTitle("JasperReports Statement Parameters");

        System.out.println(TUIBox.line("Account:       " + ConsoleTheme.highlight(targetAccount.getAccountNumber() + " (" + targetAccount.getAccountType() + ")"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("Cardholder:    " + userEntity.getFullName(), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("Period:        " + startDate + " to " + endDate, TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("Output Format: Official PDF (Compiled JRXML)", TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        boolean confirm = ConsolePrompt.promptConfirmation("Compile and generate PDF statement now?");
        if (!confirm) {
            this.statusMessage = "Statement generation cancelled.";
            this.isErrorStatus = false;
            return;
        }

        // 4. Trigger Statement Generation
        try {
            String outputPath = reportController.generateStatement(userEntity, targetAccount, fromDateTime, toDateTime);
            session.clearScreen();
            TUILayout.printHeader("Export Complete");
            TUILayout.printScreenTitle("Statement PDF Ready");
            TUILayout.printAlert("PDF bank statement generated successfully!", false);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            System.out.println(TUIBox.center(ConsoleTheme.highlight("File Path: " + outputPath), TUILayout.APP_WIDTH));
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            System.out.println(TUIBox.center(ConsoleTheme.muted("Document is ready for printing, archiving, or auditing."), TUILayout.APP_WIDTH));
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        } catch (Exception e) {
            session.clearScreen();
            TUILayout.printHeader("Export Failed");
            TUILayout.printScreenTitle("JasperReports Error");
            TUILayout.printAlert("Failed to generate statement: " + e.getMessage(), true);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        }

        TUILayout.printFooter("Press Enter to return to Main Menu");
        ConsolePrompt.pause();
        navigator.pop();
    }
}
