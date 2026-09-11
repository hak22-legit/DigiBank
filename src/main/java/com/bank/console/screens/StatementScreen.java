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
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Screen for generating and exporting JasperReports PDF bank statements.
 * Pure keyboard navigation with zero trailing prompts.
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

        int width = TUILayout.APP_WIDTH;
        session.clearScreen();

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
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        // 1. Select Account via Raw Arrow-Key & Hotkey Navigation
        AccountDTO targetAccount = accounts.get(0);
        if (accounts.size() > 1) {
            int selectedAccIdx = 0;
            Terminal terminal = session.getTerminal();
            Attributes origAttr = terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();
            boolean firstRender = true;
            try {
                while (true) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > STATEMENTS > SELECT ACCOUNT"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    for (int i = 0; i < accounts.size(); i++) {
                        AccountDTO acc = accounts.get(i);
                        String row = String.format("[%d] %s (%s) — Balance: %s %s",
                                i + 1, acc.getAccountNumber(), acc.getAccountType(),
                                ConsoleFormatter.formatCurrency(acc.getBalance()), acc.getCurrency());
                        if (i == selectedAccIdx) {
                            sb.append(TUIBox.line("  ► " + ConsoleTheme.highlight(row), width)).append("\n");
                        } else {
                            sb.append(TUIBox.line("    " + row, width)).append("\n");
                        }
                    }

                    sb.append(TUIBox.emptyLine(width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");
                    String hotkeyRange = "1-" + Math.min(accounts.size(), 9);
                    sb.append(ConsoleTheme.muted(String.format(" [↑/↓] Navigate  •  [Enter] Select  •  [%s] Hotkey  •  [Esc] Back", hotkeyRange))).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    int ch = reader.read();
                    if (ch == 27) { // ESC
                        int next = reader.read(60);
                        if (next == -2 || next == -1) {
                            terminal.setAttributes(origAttr);
                            navigator.pop();
                            return;
                        }
                        if (next == '[' || next == 'O') {
                            int code = reader.read();
                            if (code == 'A') {
                                selectedAccIdx = (selectedAccIdx - 1 + accounts.size()) % accounts.size();
                            } else if (code == 'B') {
                                selectedAccIdx = (selectedAccIdx + 1) % accounts.size();
                            }
                        }
                    } else if (ch == '\r' || ch == '\n') {
                        targetAccount = accounts.get(selectedAccIdx);
                        break;
                    } else if (ch >= '1' && ch <= '0' + Math.min(accounts.size(), 9)) {
                        targetAccount = accounts.get(ch - '1');
                        break;
                    } else if (ch == 'b' || ch == 'B') {
                        terminal.setAttributes(origAttr);
                        navigator.pop();
                        return;
                    } else if (ch == 3) {
                        System.exit(0);
                    }
                }
            } catch (Exception e) {
                targetAccount = accounts.get(0);
            } finally {
                terminal.setAttributes(origAttr);
            }
        }

        // 2. Select Date Range
        session.clearScreen();
        LocalDate now = LocalDate.now();
        LocalDate defaultStart = now.minusDays(30);
        String defaultStartStr = defaultStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String defaultEndStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        StringBuilder dateSb = new StringBuilder();
        dateSb.append(TUIBox.top(width)).append("\n");
        dateSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > STATEMENTS > DATE RANGE"), width)).append("\n");
        dateSb.append(TUIBox.divider(width)).append("\n");
        dateSb.append(TUIBox.line("  Selected Account: " + ConsoleTheme.highlight(targetAccount.getAccountNumber() + " (" + targetAccount.getCurrency() + ")"), width)).append("\n");
        dateSb.append(TUIBox.line(ConsoleTheme.muted("  Specify statement date period (format: yyyy-MM-dd)"), width)).append("\n");
        dateSb.append(TUIBox.bottom(width)).append("\n");
        ScreenRenderer.render(dateSb.toString());

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

        // 3. Review Box with Pure Keystroke Execution
        StringBuilder revSb = new StringBuilder();
        revSb.append(TUIBox.top(width)).append("\n");
        revSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > STATEMENTS > REVIEW PARAMETERS"), width)).append("\n");
        revSb.append(TUIBox.divider(width)).append("\n");
        revSb.append(TUIBox.emptyLine(width)).append("\n");
        revSb.append(TUIBox.line("  Account        : " + ConsoleTheme.highlight(targetAccount.getAccountNumber() + " (" + targetAccount.getAccountType() + " - " + targetAccount.getCurrency() + ")"), width)).append("\n");
        revSb.append(TUIBox.line("  Cardholder     : " + userEntity.getFullName(), width)).append("\n");
        revSb.append(TUIBox.line("  Period         : " + startDate + " to " + endDate, width)).append("\n");
        revSb.append(TUIBox.line("  Output Format  : Official PDF (Compiled JasperReports JRXML)", width)).append("\n");
        revSb.append(TUIBox.emptyLine(width)).append("\n");
        revSb.append(TUIBox.line("  ► " + ConsoleTheme.highlight("[GENERATE PDF STATEMENT]") + "                   " + ConsoleTheme.muted("[CANCEL]"), width)).append("\n");
        revSb.append(TUIBox.bottom(width)).append("\n");
        revSb.append(ConsoleTheme.muted(" [Enter] Generate PDF  •  [Esc/B] Cancel")).append("\n");
        ScreenRenderer.render(revSb.toString());

        Terminal terminal = session.getTerminal();
        Attributes origAttr = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        boolean confirm = false;
        try {
            while (true) {
                int ch = reader.read();
                if (ch == 27) { // ESC
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        confirm = false;
                        break;
                    }
                } else if (ch == '\r' || ch == '\n' || ch == 'y' || ch == 'Y' || ch == '1') {
                    confirm = true;
                    break;
                } else if (ch == 'b' || ch == 'B' || ch == 'n' || ch == 'N' || ch == '2') {
                    confirm = false;
                    break;
                } else if (ch == 3) {
                    System.exit(0);
                }
            }
        } catch (Exception e) {
            confirm = false;
        } finally {
            terminal.setAttributes(origAttr);
        }

        if (!confirm) {
            this.statusMessage = "Statement generation cancelled.";
            this.isErrorStatus = false;
            return;
        }

        // 4. Trigger Statement Generation
        try {
            String outputPath = reportController.generateStatement(userEntity, targetAccount, fromDateTime, toDateTime);
            StringBuilder exportSb = new StringBuilder();
            exportSb.append(TUIBox.top(width)).append("\n");
            exportSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > STATEMENTS > EXPORT READY"), width)).append("\n");
            exportSb.append(TUIBox.divider(width)).append("\n");
            exportSb.append(TUIBox.emptyLine(width)).append("\n");
            exportSb.append(TUIBox.center(ConsoleTheme.success("✔ PDF bank statement generated successfully!"), width)).append("\n");
            exportSb.append(TUIBox.emptyLine(width)).append("\n");
            exportSb.append(TUIBox.center(ConsoleTheme.highlight("File Path: " + outputPath), width)).append("\n");
            exportSb.append(TUIBox.emptyLine(width)).append("\n");
            exportSb.append(TUIBox.center(ConsoleTheme.muted("Document is ready for printing, archiving, or auditing."), width)).append("\n");
            exportSb.append(TUIBox.emptyLine(width)).append("\n");
            exportSb.append(TUIBox.bottom(width)).append("\n");
            exportSb.append(ConsoleTheme.muted(" [Enter] Return to Main Menu  •  [Esc] Back")).append("\n");
            ScreenRenderer.render(exportSb.toString());

            Attributes postAttr = terminal.enterRawMode();
            try {
                while (true) {
                    int ch = reader.read();
                    if (ch == 27 || ch == '\r' || ch == '\n' || ch == 'b' || ch == 'B') {
                        break;
                    } else if (ch == 3) {
                        System.exit(0);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                terminal.setAttributes(postAttr);
            }

        } catch (Exception e) {
            StringBuilder errSb = new StringBuilder();
            errSb.append(TUIBox.top(width)).append("\n");
            errSb.append(TUIBox.line(ConsoleTheme.error(" Failed to generate statement: " + e.getMessage()), width)).append("\n");
            errSb.append(TUIBox.bottom(width)).append("\n");
            ScreenRenderer.render(errSb.toString());
            ConsolePrompt.pause("Press Enter to return...");
        }

        navigator.pop();
    }
}
