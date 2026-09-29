package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.model.PagedResult;
import com.bank.model.dto.GlobalLedgerItem;
import com.bank.model.entity.Admin;
import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.Currency;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SUPER ADMIN > GLOBAL LEDGER & VAULT MONITOR (82 Columns)
 * Master ledger showing vault balances, account risk tier, direct currency tab filtering, and clean selection highlight.
 */
public class GlobalLedgerScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(GlobalLedgerScreen.class);

    public enum CurrencyFilter {
        ALL("ALL"),
        USD("USD"),
        KHR("KHR");

        private final String label;

        CurrencyFilter(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public Currency toCurrency() {
            return switch (this) {
                case USD -> Currency.USD;
                case KHR -> Currency.KHR;
                case ALL -> null;
            };
        }

        public static CurrencyFilter fromCurrency(Currency c) {
            if (c == Currency.USD) return USD;
            if (c == Currency.KHR) return KHR;
            return ALL;
        }
    }

    private final AdminController adminController;

    public GlobalLedgerScreen() {
        this(ControllerFactory.getAdminController());
    }

    public GlobalLedgerScreen(AdminController adminController) {
        this.adminController = adminController;
    }

    private static String formatCurrencyBalance(String currency, double balance) {
        if ("KHR".equalsIgnoreCase(currency)) {
            DecimalFormat khrFmt = new DecimalFormat("#,##0");
            return String.format("៛ %12s", khrFmt.format((long) balance));
        }
        DecimalFormat usdFmt = new DecimalFormat("#,##0.00");
        return String.format("$ %12s", usdFmt.format(balance));
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
        NonBlockingReader reader = terminal.reader();        int currentPage = 1;
        int pageSize = 5;
        int selectedIndex = 0;
        CurrencyFilter activeCcy = CurrencyFilter.ALL;
        String searchTerm = null;
        boolean isSearchMode = false;
        StringBuilder searchBuf = new StringBuilder();
        boolean firstRender = true;

        try {
            while (true) {
                PagedResult<GlobalLedgerItem> paged;
                Map<Currency, BigDecimal> vaultTotals;
                Map<CurrencyFilter, Long> tabCounts = new HashMap<>();
                try {
                    paged = adminController.getGlobalLedger(admin, currentPage, pageSize, searchTerm, activeCcy.toCurrency());
                    vaultTotals = adminController.getVaultTotals(admin);
                    long countAll = ControllerFactory.getAccountRepository().countAccounts(searchTerm, null);
                    long countUsd = ControllerFactory.getAccountRepository().countAccounts(searchTerm, Currency.USD);
                    long countKhr = ControllerFactory.getAccountRepository().countAccounts(searchTerm, Currency.KHR);
                    tabCounts.put(CurrencyFilter.ALL, countAll);
                    tabCounts.put(CurrencyFilter.USD, countUsd);
                    tabCounts.put(CurrencyFilter.KHR, countKhr);
                } catch (Exception e) {
                    logger.error("Error fetching global ledger data", e);
                    navigator.pop();
                    return;
                }

                List<GlobalLedgerItem> items = paged.getItems();
                int totalPages = paged.getTotalPages();
                if (selectedIndex >= items.size() && !items.isEmpty()) {
                    selectedIndex = items.size() - 1;
                }
                if (selectedIndex < 0 && !items.isEmpty()) {
                    selectedIndex = 0;
                }

                String statusMessage;
                if (searchTerm != null && !searchTerm.isBlank()) {
                    long matchCount = tabCounts.getOrDefault(activeCcy, (long) items.size());
                    statusMessage = String.format("Status: Found %d accounts matching \"%s\". Press [X] to clear search.",
                            matchCount, searchTerm);
                } else if (!items.isEmpty() && selectedIndex >= 0 && selectedIndex < items.size()) {
                    GlobalLedgerItem sel = items.get(selectedIndex);
                    String accNum = sel.getAccountNumber() != null ? sel.getAccountNumber() : "DGB-000000000";
                    statusMessage = "Status: Account " + accNum + " selected. Press [Enter] for Statement Ledger.";
                } else {
                    statusMessage = "Status: No accounts found matching current criteria.";
                }

                String rendered = renderContent(items, vaultTotals, currentPage, totalPages, selectedIndex,
                        activeCcy, searchTerm, tabCounts, isSearchMode, searchBuf.toString(), statusMessage, false, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);

                if (isSearchMode) {
                    if (event.action() == KeyAction.ESCAPE) {
                        isSearchMode = false;
                        searchBuf.setLength(0);
                    } else if (event.action() == KeyAction.BACKSPACE) {
                        if (searchBuf.length() > 0) {
                            searchBuf.deleteCharAt(searchBuf.length() - 1);
                        }
                    } else if (event.action() == KeyAction.ENTER) {
                        String q = searchBuf.toString().trim();
                        searchTerm = q.isEmpty() ? null : q;
                        isSearchMode = false;
                        currentPage = 1;
                        selectedIndex = 0;
                    } else if (event.action() == KeyAction.CHAR || event.action() == KeyAction.DIGIT) {
                        char ch = event.ch();
                        if (searchBuf.length() < 60 && ch >= 32 && ch <= 126) {
                            searchBuf.append(ch);
                        }
                    }
                } else {
                    if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && (event.ch() == 'b' || event.ch() == 'B'))) {
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.TAB) {
                        activeCcy = switch (activeCcy) {
                            case ALL -> CurrencyFilter.USD;
                            case USD -> CurrencyFilter.KHR;
                            case KHR -> CurrencyFilter.ALL;
                        };
                        currentPage = 1;
                        selectedIndex = 0;
                    } else if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                        if (selectedIndex > 0) {
                            selectedIndex--;
                        } else if (currentPage > 1) {
                            currentPage--;
                            selectedIndex = pageSize - 1;
                        }
                    } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                        if (selectedIndex < items.size() - 1) {
                            selectedIndex++;
                        } else if (currentPage < totalPages) {
                            currentPage++;
                            selectedIndex = 0;
                        }
                    } else if (event.action() == KeyAction.LEFT || (event.action() == KeyAction.CHAR && (event.ch() == 'h' || event.ch() == 'H'))) {
                        if (currentPage > 1) {
                            currentPage--;
                            selectedIndex = 0;
                        }
                    } else if (event.action() == KeyAction.RIGHT) {
                        if (currentPage < totalPages) {
                            currentPage++;
                            selectedIndex = 0;
                        }
                    } else if (event.action() == KeyAction.CHAR && (event.ch() == 'x' || event.ch() == 'X') && searchTerm != null) {
                        searchTerm = null;
                        searchBuf.setLength(0);
                        currentPage = 1;
                        selectedIndex = 0;
                    } else if (event.action() == KeyAction.CHAR && (event.ch() == '/' || event.ch() == 'f' || event.ch() == 'F')) {
                        isSearchMode = true;
                        searchBuf.setLength(0);
                    } else if (event.action() == KeyAction.CHAR && (event.ch() == 'a' || event.ch() == 'A')) {
                        terminal.setAttributes(origAttributes);
                        navigator.push(new AuditLogScreen(adminController));
                        return;
                    } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                        char c = event.ch();
                        if (c == '1') {
                            activeCcy = CurrencyFilter.ALL;
                            currentPage = 1;
                            selectedIndex = 0;
                        } else if (c == '2') {
                            activeCcy = CurrencyFilter.USD;
                            currentPage = 1;
                            selectedIndex = 0;
                        } else if (c == '3') {
                            activeCcy = CurrencyFilter.KHR;
                            currentPage = 1;
                            selectedIndex = 0;
                        } else if (c == 'l' || c == 'L') {
                            inspectLedger(navigator, terminal, origAttributes, items, selectedIndex);
                            return;
                        }
                    } else if (event.action() == KeyAction.ENTER) {
                        inspectLedger(navigator, terminal, origAttributes, items, selectedIndex);
                        return;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in GlobalLedgerScreen loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void inspectLedger(ScreenNavigator navigator, Terminal terminal, Attributes origAttributes,
                               List<GlobalLedgerItem> items, int selectedIndex) {
        terminal.setAttributes(origAttributes);
        if (items != null && selectedIndex >= 0 && selectedIndex < items.size()) {
            GlobalLedgerItem selected = items.get(selectedIndex);
            navigator.push(new AccountStatementLedgerScreen(adminController, selected));
        }
    }

    public static String renderContent(List<GlobalLedgerItem> items, Map<Currency, BigDecimal> vaultTotals,
                                       int currentPage, int totalPages, int selectedIndex,
                                       CurrencyFilter activeCcy, String searchTerm,
                                       Map<CurrencyFilter, Long> tabCounts,
                                       boolean isSearchMode, String searchBuf,
                                       String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat usdDf = new DecimalFormat("#,##0.00");
        DecimalFormat khrDf = new DecimalFormat("#,##0");

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > GLOBAL LEDGER & VAULT MONITOR"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Vault Summary Compartment
        BigDecimal totalUsd = vaultTotals != null ? vaultTotals.getOrDefault(Currency.USD, BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal totalKhr = vaultTotals != null ? vaultTotals.getOrDefault(Currency.KHR, BigDecimal.ZERO) : BigDecimal.ZERO;
        String vaultLine = String.format("TOTAL VAULT ASSETS : $ %s USD   │   ៛ %s KHR",
                usdDf.format(totalUsd), khrDf.format(totalKhr));
        sb.append(TUIBox.line(ConsoleTheme.bold(vaultLine), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // CCY Direct Tabs Row
        long countAll = 0, countUsd = 0, countKhr = 0;
        if (tabCounts != null) {
            countAll = tabCounts.getOrDefault(CurrencyFilter.ALL, 0L);
            countUsd = tabCounts.getOrDefault(CurrencyFilter.USD, 0L);
            countKhr = tabCounts.getOrDefault(CurrencyFilter.KHR, 0L);
        } else if (items != null) {
            countAll = items.size();
            countUsd = items.stream().filter(i -> i.getCurrency() == Currency.USD).count();
            countKhr = items.stream().filter(i -> i.getCurrency() == Currency.KHR).count();
        }

        String tab1 = (activeCcy == CurrencyFilter.ALL) ? "▸ [1] ALL (" + countAll + ")" : "  [1] ALL (" + countAll + ")";
        String tab2 = (activeCcy == CurrencyFilter.USD) ? "▸ [2] USD (" + countUsd + ")" : "  [2] USD (" + countUsd + ")";
        String tab3 = (activeCcy == CurrencyFilter.KHR) ? "▸ [3] KHR (" + countKhr + ")" : "  [3] KHR (" + countKhr + ")";

        String tab1Padded = String.format("%-15s", tab1);
        if (tab1Padded.length() > 15) tab1Padded = tab1Padded.substring(0, 15);
        String tab2Padded = String.format("%-15s", tab2);
        if (tab2Padded.length() > 15) tab2Padded = tab2Padded.substring(0, 15);
        String tab3Padded = String.format("%-14s", tab3);
        if (tab3Padded.length() > 14) tab3Padded = tab3Padded.substring(0, 14);

        String h1 = (activeCcy == CurrencyFilter.ALL) ? ConsoleTheme.inlineHighlight(tab1Padded) : tab1Padded;
        String h2 = (activeCcy == CurrencyFilter.USD) ? ConsoleTheme.inlineHighlight(tab2Padded) : tab2Padded;
        String h3 = (activeCcy == CurrencyFilter.KHR) ? ConsoleTheme.inlineHighlight(tab3Padded) : tab3Padded;
        String tabs = h1 + " " + h2 + " " + h3; // visible: 15 + 1 + 15 + 1 + 14 = 46 chars

        String filterDisplay = (searchTerm != null && !searchTerm.trim().isEmpty())
                ? "\"" + searchTerm.trim().toUpperCase() + "\""
                : "NONE";
        if (filterDisplay.length() > 9) {
            filterDisplay = filterDisplay.substring(0, 7) + "..\"";
        }
        String filterStatus = String.format("%-10s", filterDisplay);

        String innerContent = " CCY VIEW: "
                + tabs
                + ConsoleTheme.border(" │ ")
                + "FILTER: "
                + filterStatus
                + "  "; // 11 + 46 + 3 + 8 + 10 + 2 = 80 chars

        int innerVis = TUIBox.visibleLength(innerContent);
        if (innerVis < 80) {
            innerContent = innerContent + " ".repeat(80 - innerVis);
        } else if (innerVis > 80) {
            // Trim any excess padding before border
            int excess = innerVis - 80;
            if (innerContent.endsWith(" ".repeat(excess))) {
                innerContent = innerContent.substring(0, innerContent.length() - excess);
            }
        }

        String ccyLine = ConsoleTheme.border("│") + innerContent + ConsoleTheme.border("│");
        sb.append(ccyLine).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Table Header: 78 chars visible inside TUIBox.line
        String th = String.format(" %-14s %-16s %-9s %-4s %14s %-6s %-8s",
                "ACC NUMBER", "OWNER NAME", "TYPE", "CCY", "BALANCE", "RISK", "STATUS");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("─".repeat(78), width)).append("\n");

        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                GlobalLedgerItem item = items.get(i);
                boolean isSelected = (i == selectedIndex);

                String prefix = isSelected ? "▸" : " ";

                String accNo = (item.getAccountNumber() != null && !item.getAccountNumber().isBlank())
                        ? item.getAccountNumber().trim()
                        : "DGB-000000000";
                if (accNo.length() > 14) accNo = accNo.substring(0, 14);

                String owner = (item.getOwnerName() != null && !item.getOwnerName().isBlank())
                        ? item.getOwnerName().trim()
                        : "(Unassigned)";
                if (owner.length() > 16) owner = owner.substring(0, 13) + "...";

                String type = item.getAccountType() != null ? item.getAccountType().name() : "CHECKING";
                if (type.length() > 9) type = type.substring(0, 9);

                String ccy = item.getCurrency() != null ? item.getCurrency().name() : "USD";
                if (ccy.length() > 4) ccy = ccy.substring(0, 4);

                BigDecimal bal = item.getBalance() != null ? item.getBalance() : BigDecimal.ZERO;
                String balStr;
                if ("KHR".equalsIgnoreCase(ccy)) {
                    balStr = String.format("៛ %11s", khrDf.format(bal));
                } else {
                    balStr = String.format("$ %11s", usdDf.format(bal));
                }

                String risk = item.getRiskLevel() != null ? item.getRiskLevel() : "LOW";
                if (risk.length() > 6) risk = risk.substring(0, 6);

                String st = item.getStatus() != null ? item.getStatus().name() : "ACTIVE";
                if (st.length() > 8) st = st.substring(0, 8);

                String plainRow = String.format("%s%-14s %-16s %-9s %-4s %14s %-6s %-8s",
                        prefix, accNo, owner, type, ccy, balStr, risk, st);

                if (isSelected) {
                    sb.append(TUIBox.line("\033[7m" + plainRow + "\033[0m", width)).append("\n");
                } else {
                    String coloredRiskCell;
                    String riskPadded = String.format("%-6s", risk);
                    if ("HIGH".equalsIgnoreCase(risk)) {
                        coloredRiskCell = "\033[31m" + riskPadded + "\033[0m";
                    } else if ("MED".equalsIgnoreCase(risk)) {
                        coloredRiskCell = "\033[33m" + riskPadded + "\033[0m";
                    } else {
                        coloredRiskCell = riskPadded;
                    }

                    String stPadded = String.format("%-8s", st);
                    String coloredStatusCell;
                    if ("ACTIVE".equalsIgnoreCase(st)) {
                        coloredStatusCell = "\033[32m" + stPadded + "\033[0m";
                    } else {
                        coloredStatusCell = "\033[31m" + stPadded + "\033[0m";
                    }

                    String coloredRow = String.format("%s%-14s %-16s %-9s %-4s %14s ",
                            prefix, accNo, owner, type, ccy, balStr) + coloredRiskCell + " " + coloredStatusCell;
                    sb.append(TUIBox.line(coloredRow, width)).append("\n");
                }
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No accounts found matching current criteria."), width)).append("\n");
        }

        int remaining = Math.max(1, 5 - (items != null ? items.size() : 0));
        for (int i = 0; i < remaining; i++) {
            sb.append(TUIBox.emptyLine(width)).append("\n");
        }

        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line or Search Prompt inside box
        if (isSearchMode) {
            String inputDisp = searchBuf + "_";
            if (inputDisp.length() > 66) inputDisp = inputDisp.substring(0, 66);
            String searchPrompt = String.format("SEARCH: [ %-66s ]", inputDisp);
            sb.append(TUIBox.line(searchPrompt, width)).append("\n");
            sb.append(TUIBox.bottom(width)).append("\n");
            sb.append(Ansi.keyGuide("[Enter] Apply Search  •  [Backspace] Delete  •  [Esc] Cancel Search")).append("\n");
        } else {
            String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : statusMessage;
            if (TUIBox.visibleLength(statusDisplay) > 76) {
                statusDisplay = statusDisplay.substring(0, 73) + "...";
            }
            sb.append(TUIBox.line(statusDisplay, width)).append("\n");
            sb.append(TUIBox.bottom(width)).append("\n");

            if (searchTerm != null && !searchTerm.isBlank()) {
                sb.append(Ansi.keyGuide("[↑/↓] Select  •  [Enter] Ledger  •  [/] New Search  •  [X] Clear Filter  •  [Esc] Back")).append("\n");
            } else {
                sb.append(Ansi.keyGuide("[↑/↓] Select  •  [1-3/Tab] CCY  •  [Enter] Ledger  •  [A] Audit  •  [/] Find  •  [Esc] Back")).append("\n");
            }
        }

        return sb.toString();
    }

    public static String renderContent(List<GlobalLedgerItem> items, Map<Currency, BigDecimal> vaultTotals,
                                       int currentPage, int totalPages, int selectedIndex,
                                       CurrencyFilter activeCcy, String searchTerm,
                                       Map<CurrencyFilter, Long> tabCounts,
                                       String statusMessage, boolean isError, int width) {
        return renderContent(items, vaultTotals, currentPage, totalPages, selectedIndex,
                activeCcy, searchTerm, tabCounts, false, "", statusMessage, isError, width);
    }

    public static String renderContent(List<GlobalLedgerItem> items, Map<Currency, BigDecimal> vaultTotals,
                                       int currentPage, int totalPages, int selectedIndex,
                                       Currency filterCurrency, String searchTerm,
                                       String statusMessage, boolean isError, int width) {
        CurrencyFilter activeCcy = CurrencyFilter.fromCurrency(filterCurrency);
        return renderContent(items, vaultTotals, currentPage, totalPages, selectedIndex,
                activeCcy, searchTerm, null, false, "", statusMessage, isError, width);
    }
}

