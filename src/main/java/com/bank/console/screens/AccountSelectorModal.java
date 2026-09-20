package com.bank.console.screens;

import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.Account;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.util.List;

/**
 * Interactive 82-column Account Selector Modal for selecting target/source bank accounts.
 * Supports arrow navigation, instant numeric hotkeys, Enter confirmation, and Esc cancellation.
 * Features raw terminal mode and multi-byte ANSI escape sequence parsing.
 */
public class AccountSelectorModal {

    /**
     * Standalone interactive prompt for selecting an Account entity with raw terminal mode
     * and multi-byte escape sequence decoding.
     */
    public static Account promptSelection(List<Account> accounts, String modalTitle) throws Exception {
        if (accounts == null || accounts.isEmpty()) {
            return null;
        }

        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .jna(true)
                .build();
        terminal.enterRawMode(); // Disable line buffering and echo
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;

        try {
            while (true) {
                renderModal(accounts, selectedIndex, modalTitle);

                int ch = reader.read();

                // Handle ANSI Escape Sequences (Arrow Keys, Esc)
                if (ch == 27) { // 0x1B / ESC
                    int next1 = reader.read(30); // 30ms lookahead for sequence bytes
                    if (next1 == '[' || next1 == 'O') {
                        int next2 = reader.read(30);
                        if (next2 == 'A') { // Up Arrow
                            selectedIndex = (selectedIndex > 0) ? selectedIndex - 1 : accounts.size() - 1;
                        } else if (next2 == 'B') { // Down Arrow
                            selectedIndex = (selectedIndex < accounts.size() - 1) ? selectedIndex + 1 : 0;
                        }
                    } else if (next1 == -2 || next1 == -1 || next1 == 'b' || next1 == 'B') {
                        // Bare ESC key -> Cancel / Return null
                        return null;
                    }
                }
                // Direct 1-based index numeric hotkeys ('1', '2', '3'...)
                else if (ch >= '1' && ch < '1' + Math.min(accounts.size(), 9)) {
                    return accounts.get(ch - '1');
                }
                // Enter key (\r on macOS/Unix or \n)
                else if (ch == '\r' || ch == '\n') {
                    return accounts.get(selectedIndex);
                }
                // Tab key cycling
                else if (ch == '\t') {
                    selectedIndex = (selectedIndex + 1) % accounts.size();
                }
                // 'b' or 'B' hotkey for back/cancel
                else if (ch == 'b' || ch == 'B') {
                    return null;
                }
            }
        } finally {
            terminal.close();
        }
    }

    public static Account promptSelection(List<Account> accounts) throws Exception {
        return promptSelection(accounts, "DIGIBANK CORE > CASH OPERATIONS > SELECT DEPOSIT ACCOUNT");
    }

    /**
     * Renders the modal frame for Account entities and prints to terminal.
     */
    public static String renderModal(List<Account> accounts, int selectedIndex, String modalTitle) {
        int width = TUILayout.APP_WIDTH;
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(modalTitle != null ? modalTitle : "DIGIBANK CORE > CASH OPERATIONS > SELECT DEPOSIT ACCOUNT"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("AVAILABLE TARGET ACCOUNTS", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        for (int i = 0; i < accounts.size(); i++) {
            Account acc = accounts.get(i);
            String typeStr = acc.getAccountType() != null ? acc.getAccountType().name() : "CHECKING";
            String typeFormatted = String.format("(%-8s)", typeStr);
            String balFormatted = ConsoleFormatter.formatAlignedBalance(acc.getBalance(), acc.getCurrency());
            String row = String.format("[%d] %-14s  %s ── Bal: %s",
                    i + 1, acc.getAccountNumber(), typeFormatted, balFormatted);

            if (i == selectedIndex) {
                sb.append(TUIBox.line("  ▸ " + ConsoleTheme.highlight(row), width)).append("\n");
            } else {
                sb.append(TUIBox.line("    " + row, width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("Tip: Funds deposited will credit to the selected account immediately.", width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        int maxHotkey = Math.min(accounts.size(), 9);
        String hotkeyRange = maxHotkey > 1 ? "1-" + maxHotkey : "1";
        sb.append(ConsoleTheme.muted(String.format(" [↑/↓] Navigate  •  [Enter] Confirm  •  [%s] Hotkey  •  [Esc] Back", hotkeyRange))).append("\n");

        String rendered = sb.toString();
        System.out.print(rendered);
        System.out.flush();
        return rendered;
    }

    /**
     * Interactive modal runner within an existing screen session.
     */
    public static AccountDTO showModal(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                       List<AccountDTO> accounts, AccountDTO currentSelected, int width,
                                       String title, String compartmentHeader, String balanceLabel,
                                       String tipText, String footerFormat) {
        if (accounts == null || accounts.isEmpty()) {
            return null;
        }

        int selectedIdx = 0;
        if (currentSelected != null) {
            for (int i = 0; i < accounts.size(); i++) {
                if (accounts.get(i).getAccountNumber().equalsIgnoreCase(currentSelected.getAccountNumber())) {
                    selectedIdx = i;
                    break;
                }
            }
        }

        boolean firstRender = true;
        try {
            while (true) {
                String content = renderModalContent(accounts, selectedIdx, width, title,
                        compartmentHeader, balanceLabel, tipText, footerFormat);
                ScreenRenderer.render(content, firstRender);
                firstRender = false;

                int ch = reader.read();

                // Multi-byte escape sequence decoding (30ms lookahead window)
                if (ch == 27) { // 0x1B / ESC
                    int next1 = reader.read(30);
                    if (next1 == '[' || next1 == 'O') {
                        int next2 = reader.read(30);
                        if (next2 == 'A') { // Up Arrow
                            selectedIdx = (selectedIdx > 0) ? selectedIdx - 1 : accounts.size() - 1;
                        } else if (next2 == 'B') { // Down Arrow
                            selectedIdx = (selectedIdx < accounts.size() - 1) ? selectedIdx + 1 : 0;
                        }
                    } else if (next1 == -2 || next1 == -1 || next1 == 'b' || next1 == 'B') {
                        // Bare ESC key -> Cancel / Return null
                        return null;
                    }
                }
                // Direct numeric hotkeys ('1' - '9')
                else if (ch >= '1' && ch < '1' + Math.min(accounts.size(), 9)) {
                    int chosen = ch - '1';
                    return accounts.get(chosen);
                }
                // Enter confirmation (\r or \n)
                else if (ch == '\r' || ch == '\n') {
                    return accounts.get(selectedIdx);
                }
                // Tab key cycling
                else if (ch == '\t') {
                    selectedIdx = (selectedIdx + 1) % accounts.size();
                }
                // 'b' / 'B' hotkey for back/cancel
                else if (ch == 'b' || ch == 'B') {
                    return null;
                }
            }
        } catch (Exception e) {
            return currentSelected != null ? currentSelected : accounts.get(0);
        }
    }

    public static String renderModalContent(List<AccountDTO> accounts, int selectedIdx, int width,
                                            String title, String compartmentHeader, String balanceLabel,
                                            String tipText, String footerFormat) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(title), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(compartmentHeader, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        for (int i = 0; i < accounts.size(); i++) {
            AccountDTO acc = accounts.get(i);
            String typeStr = acc.getAccountType() != null ? acc.getAccountType().name() : "CHECKING";
            String typeFormatted = String.format("(%-8s)", typeStr);
            String balFormatted = ConsoleFormatter.formatAlignedBalance(acc.getBalance(), acc.getCurrency());
            String row = String.format("[%d] %-14s  %s ── %s%s",
                    i + 1, acc.getAccountNumber(), typeFormatted, balanceLabel, balFormatted);

            if (i == selectedIdx) {
                sb.append(TUIBox.line("  ▸ " + ConsoleTheme.highlight(row), width)).append("\n");
            } else {
                sb.append(TUIBox.line("    " + row, width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(tipText, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        int maxHotkey = Math.min(accounts.size(), 9);
        String hotkeyRange = maxHotkey > 1 ? "1-" + maxHotkey : "1";
        String footer = String.format(footerFormat, hotkeyRange);
        sb.append(ConsoleTheme.keyGuide(footer)).append("\n");

        return sb.toString();
    }

    public static AccountDTO selectSenderAccount(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                                 List<AccountDTO> accounts, AccountDTO currentSelected, int width) {
        return showModal(
                terminal, origAttr, reader, accounts, currentSelected, width,
                "DIGIBANK CORE > MONEY MOVEMENT > SELECT SENDER ACCOUNT",
                "AVAILABLE SOURCE ACCOUNTS",
                "Bal: ",
                "Tip: Select an account with sufficient balance for this transfer.",
                " [↑/↓] Navigate  •  [Enter] Confirm Selection  •  [%s] Hotkey  •  [Esc] Cancel"
        );
    }

    public static AccountDTO selectSenderAccount(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                                 List<AccountDTO> accounts, AccountDTO currentSelected) {
        return selectSenderAccount(terminal, origAttr, reader, accounts, currentSelected, 82);
    }
}
