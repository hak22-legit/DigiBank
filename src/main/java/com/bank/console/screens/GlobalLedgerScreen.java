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
        NonBlockingReader reader = terminal.reader();

        int currentPage = 1;
        int pageSize = 5;
        int selectedIndex = 0;
        CurrencyFilter activeCcy = CurrencyFilter.ALL;
        String searchTerm = null;
        String statusMessage = "Showing all multi-currency accounts. Press [/] to search.";
        boolean isError = false;
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

                String rendered = renderContent(items, vaultTotals, currentPage, totalPages, selectedIndex,
                        activeCcy, searchTerm, tabCounts, statusMessage, isError, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && (event.ch() == 'b' || event.ch() == 'B'))) {
                    navigator.pop();
                    return;
                } else if (event.action() == KeyAction.TAB) {
                    // Cycle cleanly across the 3 defined tabs: ALL -> USD -> KHR -> ALL
                    activeCcy = switch (activeCcy) {
                        case ALL -> CurrencyFilter.USD;
                        case USD -> CurrencyFilter.KHR;
                        case KHR -> CurrencyFilter.ALL;
                    };
                    currentPage = 1;
                    selectedIndex = 0;
                    statusMessage = switch (activeCcy) {
                        case ALL -> "Showing all multi-currency accounts. Press [/] to search.";
                        case USD -> "Filtered by USD accounts. Press [/] to search or [1] for ALL.";
                        case KHR -> "Filtered by KHR accounts. Press [/] to search or [1] for ALL.";
                    };
                    isError = false;
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
                } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char c = event.ch();
                    if (c == '1') {
                        activeCcy = CurrencyFilter.ALL;
                        currentPage = 1;
                        selectedIndex = 0;
                        statusMessage = "Showing all multi-currency accounts. Press [/] to search.";
                        isError = false;
                    } else if (c == '2') {
                        activeCcy = CurrencyFilter.USD;
                        currentPage = 1;
                        selectedIndex = 0;
                        statusMessage = "Filtered by USD accounts. Press [/] to search or [1] for ALL.";
                        isError = false;
                    } else if (c == '3') {
                        activeCcy = CurrencyFilter.KHR;
                        currentPage = 1;
                        selectedIndex = 0;
                        statusMessage = "Filtered by KHR accounts. Press [/] to search or [1] for ALL.";
                        isError = false;
                    } else if (c == '/' || c == 's' || c == 'S') {
                        terminal.setAttributes(origAttributes);
                        String input = ConsolePrompt.promptOptional("Enter search query (Account No / Owner Name)", searchTerm != null ? searchTerm : "");
                        terminal.enterRawMode();
                        firstRender = true;
                        if (input != null && !input.trim().isEmpty()) {
                            searchTerm = input.trim();
                            statusMessage = "Filter: \"" + searchTerm + "\". Press [/] to change.";
                        } else {
                            searchTerm = null;
                            statusMessage = "Showing all multi-currency accounts. Press [/] to search.";
                        }
                        currentPage = 1;
                        selectedIndex = 0;
                        isError = false;
                    } else if (c == 'l' || c == 'L') {
                        inspectLedger(navigator, terminal, origAttributes, items, selectedIndex);
                        return;
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    inspectLedger(navigator, terminal, origAttributes, items, selectedIndex);
                    return;
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
        navigator.push(new AuditLogScreen(adminController));
    }

    public static String renderContent(List<GlobalLedgerItem> items, Map<Currency, BigDecimal> vaultTotals,
                                       int currentPage, int totalPages, int selectedIndex,
                                       CurrencyFilter activeCcy, String searchTerm,
                                       Map<CurrencyFilter, Long> tabCounts,
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
        String vaultLine = String.format("TOTAL VAULT ASSETS : $ %s USD  |  ៛ %s KHR",
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

        String t1 = (activeCcy == CurrencyFilter.ALL)
                ? "▸ " + ConsoleTheme.highlight("[1] ALL (" + countAll + ")")
                : "  [1] ALL (" + countAll + ")";
        String t2 = (activeCcy == CurrencyFilter.USD)
                ? "▸ " + ConsoleTheme.highlight("[2] USD (" + countUsd + ")")
                : "  [2] USD (" + countUsd + ")";
        String t3 = (activeCcy == CurrencyFilter.KHR)
                ? "▸ " + ConsoleTheme.highlight("[3] KHR (" + countKhr + ")")
                : "  [3] KHR (" + countKhr + ")";

        String filterDisplay = (searchTerm != null && !searchTerm.trim().isEmpty())
                ? "\"" + searchTerm.trim() + "\""
                : "None";
        if (filterDisplay.length() > 10) {
            filterDisplay = filterDisplay.substring(0, 8) + "..\"";
        }

        String tabLine = String.format("CCY: %s      %s      %s     | Filter: %s",
                t1, t2, t3, filterDisplay);
        sb.append(TUIBox.line(tabLine, width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Table Header: 78 chars visible
        String th = String.format("  %-13s  %-14s %-9s %-4s %14s   %-6s %-7s",
                "ACC NO.", "OWNER", "TYPE", "CCY", "BALANCE", "RISK", "STATUS");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(76), width)).append("\n");

        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                GlobalLedgerItem item = items.get(i);
                boolean isSelected = (i == selectedIndex);

                String prefix = isSelected ? "▸ " : "  ";

                String accNo = item.getAccountNumber() != null ? item.getAccountNumber() : "DGB-000000000";
                if (accNo.length() > 13) accNo = accNo.substring(0, 13);

                String owner = item.getOwnerName() != null ? item.getOwnerName() : "-";
                if (owner.length() > 14) owner = owner.substring(0, 11) + "...";

                String type = item.getAccountType() != null ? item.getAccountType().name() : "CHECKING";
                if (type.length() > 9) type = type.substring(0, 9);

                String ccy = item.getCurrency() != null ? item.getCurrency().name() : "USD";
                if (ccy.length() > 4) ccy = ccy.substring(0, 4);

                BigDecimal bal = item.getBalance() != null ? item.getBalance() : BigDecimal.ZERO;
                String balStr = formatCurrencyBalance(ccy, bal.doubleValue());

                String risk = item.getRiskLevel() != null ? item.getRiskLevel() : "LOW";
                if (risk.length() > 6) risk = risk.substring(0, 6);

                String st = item.getStatus() != null ? item.getStatus().name() : "ACTIVE";
                if (st.length() > 7) st = st.substring(0, 7);

                String stColor = "ACTIVE".equalsIgnoreCase(st) ? Ansi.green(st) : Ansi.red(st);
                String riskColor = "HIGH".equalsIgnoreCase(risk) ? Ansi.red(risk)
                        : "MED".equalsIgnoreCase(risk) ? Ansi.yellow(risk) : risk;

                String plainRow = String.format("%s%-13s  %-14s %-9s %-4s %14s   %-6s %-7s",
                        prefix, accNo, owner, type, ccy, balStr, risk, st);

                if (plainRow.length() > 78) {
                    plainRow = plainRow.substring(0, 78);
                } else {
                    plainRow = String.format("%-78s", plainRow);
                }

                if (isSelected) {
                    sb.append(TUIBox.line("\033[7m" + plainRow + "\033[0m", width)).append("\n");
                } else {
                    String coloredRow = plainRow;
                    if (st != null && !st.isEmpty()) coloredRow = coloredRow.replace(st, stColor);
                    if (risk != null && !risk.isEmpty() && !"LOW".equalsIgnoreCase(risk)) coloredRow = coloredRow.replace(risk, riskColor);
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

        // Status Line inside box
        String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : statusMessage;
        if (TUIBox.visibleLength(statusDisplay) > 70) {
            statusDisplay = statusDisplay.substring(0, 67) + "...";
        }
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(Ansi.keyGuide("[↑/↓] Select  •  [1-3/Tab] Switch CCY  •  [Enter] Ledger  •  [/] Find  •  [Esc] Back")).append("\n");

        return sb.toString();
    }

    public static String renderContent(List<GlobalLedgerItem> items, Map<Currency, BigDecimal> vaultTotals,
                                       int currentPage, int totalPages, int selectedIndex,
                                       Currency filterCurrency, String searchTerm,
                                       String statusMessage, boolean isError, int width) {
        CurrencyFilter activeCcy = CurrencyFilter.fromCurrency(filterCurrency);
        return renderContent(items, vaultTotals, currentPage, totalPages, selectedIndex,
                activeCcy, searchTerm, null, statusMessage, isError, width);
    }
}

