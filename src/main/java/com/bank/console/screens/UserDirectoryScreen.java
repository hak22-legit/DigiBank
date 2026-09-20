package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.model.PagedResult;
import com.bank.model.dto.UserDirectoryItem;
import com.bank.model.entity.Admin;
import com.bank.model.enums.UserStatus;
import com.bank.security.SessionManager;
import com.bank.ui.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/**
 * SUPER ADMIN > USER DIRECTORY & PROFILES (82 Columns)
 * Paginated table joining users with aggregate account balances, profile inspection, and freeze control.
 */
public class UserDirectoryScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(UserDirectoryScreen.class);

    private final AdminController adminController;

    public UserDirectoryScreen() {
        this(ControllerFactory.getAdminController());
    }

    public UserDirectoryScreen(AdminController adminController) {
        this.adminController = adminController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin == null) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int currentPage = 1;
        int pageSize = 6;
        int selectedIndex = 0;
        String statusMessage = "Account directory loaded.";
        boolean isError = false;
        boolean firstRender = true;

        try {
            while (true) {
                PagedResult<UserDirectoryItem> paged;
                try {
                    paged = adminController.getUserDirectory(admin, currentPage, pageSize);
                } catch (Exception e) {
                    logger.error("Error fetching user directory", e);
                    navigator.pop();
                    return;
                }

                List<UserDirectoryItem> items = paged.getItems();
                int totalPages = paged.getTotalPages();
                if (selectedIndex >= items.size() && !items.isEmpty()) {
                    selectedIndex = items.size() - 1;
                }

                UserDirectoryItem selectedItem = (!items.isEmpty() && selectedIndex >= 0 && selectedIndex < items.size())
                        ? items.get(selectedIndex) : null;

                if (selectedItem != null && (statusMessage.startsWith("Selected") || statusMessage.equals("Account directory loaded."))) {
                    statusMessage = String.format("Selected user \033[1m%s (#%02d)\033[0m. Press [Enter] to inspect dossier.",
                            selectedItem.getFullName(), selectedItem.getUserId());
                    isError = false;
                }

                String rendered = renderContent(items, currentPage, totalPages, selectedIndex, statusMessage, isError, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && (event.ch() == 'b' || event.ch() == 'B'))) {
                    navigator.pop();
                    return;
                } else if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                    if (!items.isEmpty()) {
                        selectedIndex = (selectedIndex - 1 + items.size()) % items.size();
                    }
                } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                    if (!items.isEmpty()) {
                        selectedIndex = (selectedIndex + 1) % items.size();
                    }
                } else if (event.action() == KeyAction.LEFT || (event.action() == KeyAction.CHAR && (event.ch() == 'h' || event.ch() == 'H'))) {
                    if (currentPage > 1) {
                        currentPage--;
                        selectedIndex = 0;
                    }
                } else if (event.action() == KeyAction.RIGHT || (event.action() == KeyAction.CHAR && (event.ch() == 'l' || event.ch() == 'L'))) {
                    if (currentPage < totalPages) {
                        currentPage++;
                        selectedIndex = 0;
                    }
                } else if (event.action() == KeyAction.CHAR && (event.ch() == 'v' || event.ch() == 'V')) {
                    if (selectedItem != null) {
                        terminal.setAttributes(origAttributes);
                        navigator.push(new UserProfileModal(adminController, selectedItem.getUserId()));
                        return;
                    }
                } else if (event.action() == KeyAction.CHAR && (event.ch() == 'f' || event.ch() == 'F')) {
                    if (selectedItem != null) {
                        try {
                            var updated = adminController.toggleUserFreeze(admin, selectedItem.getUserId());
                            selectedItem.setStatus(updated.getStatus());
                            statusMessage = String.format("Status toggled: %s (#%02d) is now %s.", updated.getFullName(), updated.getUserId(), updated.getStatus());
                            isError = updated.getStatus() == UserStatus.SUSPENDED || updated.getStatus() == UserStatus.FROZEN;
                        } catch (Exception e) {
                            statusMessage = "Freeze toggle failed: " + e.getMessage();
                            isError = true;
                        }
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (selectedItem != null) {
                        terminal.setAttributes(origAttributes);
                        navigator.push(new UserProfileModal(adminController, selectedItem.getUserId()));
                        return;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in UserDirectoryScreen loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderContent(List<UserDirectoryItem> items, int currentPage, int totalPages,
                                       int selectedIndex, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > USER DIRECTORY & PROFILES"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        String pageTitle = String.format("USER DIRECTORY (Page %d/%d)", currentPage, Math.max(1, totalPages));
        sb.append(TUIBox.line(pageTitle, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Table Header: strictly 74 chars inside margin
        String th = String.format("  %-6s %-12s %-20s %15s  %-9s %-7s",
                "ID", "USERNAME", "FULL NAME", "TOTAL BALANCE", "STATUS", "ACCOUNTS");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                UserDirectoryItem item = items.get(i);
                boolean isSelected = (i == selectedIndex);

                String prefix = isSelected ? "▸ " : "  ";
                String idStr = String.format("#%02d", item.getUserId() != null ? item.getUserId() : 0);

                String uname = item.getUsername() != null ? item.getUsername() : "-";
                if (uname.length() > 12) uname = uname.substring(0, 9) + "...";

                String fname = item.getFullName() != null ? item.getFullName() : "-";
                if (fname.length() > 20) fname = fname.substring(0, 17) + "...";

                BigDecimal bal = item.getTotalBalance() != null ? item.getTotalBalance() : BigDecimal.ZERO;
                String balStr = "$ " + String.format("%13s", df.format(bal));

                String st = item.getStatus() != null ? item.getStatus().name() : "ACTIVE";
                int accts = item.getAccountCount();
                String acctStr = accts + (accts == 1 ? " Acct" : " Accts");

                String stColor = "ACTIVE".equalsIgnoreCase(st) ? Ansi.green(st) : Ansi.red(st);

                // Format row with exact column widths matching 74 printable chars:
                // prefix(2) + id(5) + 1 + uname(12) + 1 + fname(20) + 1 + balStr(15) + 2 + st(9) + 1 + acctStr(5) = 74
                String plainRow = String.format("%s%-5s %-12s %-20s %15s  %-9s %-5s",
                        prefix, idStr, uname, fname, balStr, st, acctStr);

                if (plainRow.length() > 74) {
                    plainRow = plainRow.substring(0, 74);
                } else {
                    plainRow = String.format("%-74s", plainRow);
                }

                if (isSelected) {
                    sb.append(TUIBox.line("  \033[7m" + plainRow + "\033[0m", width)).append("\n");
                } else {
                    String coloredRow = plainRow.replace(st, stColor);
                    sb.append(TUIBox.line("  " + coloredRow, width)).append("\n");
                }
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No users registered in the directory."), width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : statusMessage;
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Select  •  [Enter] View Profile  •  [F] Toggle Freeze  •  [Esc] Back")).append("\n");

        return sb.toString();
    }
}
