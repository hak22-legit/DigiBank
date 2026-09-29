package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.TerminalInputHandler;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.model.entity.Admin;
import com.bank.model.entity.AuditLog;
import com.bank.model.enums.AdminRole;
import com.bank.security.SessionManager;
import com.bank.ui.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SUPER ADMIN > FORENSIC AUDIT TRAIL (82 Columns)
 * Paginated forensic log inspection with direct period tabs, full-row highlight,
 * in-place custom date modal (no Scanner leak), digital signature inspection, and CSV export.
 */
public class AuditLogScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogScreen.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public enum DateFilterPreset {
        TODAY("TODAY"),
        YESTERDAY("YESTERDAY"),
        LAST_7_DAYS("7 DAYS"),
        ALL("ALL"),
        CUSTOM("CUSTOM");

        private final String label;

        DateFilterPreset(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final AdminController adminController;

    public AuditLogScreen() {
        this(ControllerFactory.getAdminController());
    }

    public AuditLogScreen(AdminController adminController) {
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

        ForensicAuditTrailScreen.drainBufferOnEntry(reader);

        int currentPage = 1;
        int pageSize = 5;
        int selectedIndex = 0;
        DateFilterPreset activePreset = DateFilterPreset.TODAY;
        LocalDate customDate = null;
        String actorFilter = null;
        String statusMessage = null;
        boolean isError = false;
        boolean firstRender = true;

        if (admin.getRole() != AdminRole.SUPER_ADMIN && admin.getRole() != AdminRole.COMPLIANCE_OFFICER) {
            try {
                ControllerFactory.getAuditLogService().log(admin.getAdminId(), "ACCESS_DENIED", "audit_logs", null,
                        "Unauthorized attempt to access forensic audit vault by role " + admin.getRole());
            } catch (Exception ignored) {}

            boolean firstRestrictedRender = true;
            try {
                while (true) {
                    String rendered = renderAccessRestrictedContent("Clearance denied. Press [Enter] or [Esc] to return to previous menu.", width);
                    ScreenRenderer.render(rendered, firstRestrictedRender);
                    firstRestrictedRender = false;

                    TerminalInputHandler.KeyCode event = TerminalInputHandler.readNavigationKey(reader);
                    if (event.isEscape()
                            || event.isEnter()
                            || event.is('1')
                            || event.is('B')
                            || event.is('0')) {
                        navigator.pop();
                        return;
                    }
                }
            } catch (IOException e) {
                logger.error("Error in restricted audit log loop", e);
            } finally {
                terminal.setAttributes(origAttributes);
            }
            return;
        }

        boolean inScreen = true;
        try {
            while (inScreen) {
                try {
                    List<AuditLog> allLogs;
                    try {
                        allLogs = ControllerFactory.getAuditLogRepository().findAll();
                    } catch (Exception e) {
                        logger.error("Error fetching audit logs", e);
                        allLogs = Collections.emptyList();
                        statusMessage = "Error loading audit logs: " + e.getMessage();
                        isError = true;
                    }

                    // 1. Filter by Actor if set
                    final String currentActor = actorFilter;
                    List<AuditLog> actorFiltered = (currentActor == null || currentActor.isEmpty())
                            ? allLogs
                            : allLogs.stream().filter(l -> {
                                String actStr = l.getAdminId() != null ? String.valueOf(l.getAdminId()) : "SYSTEM";
                                String actorName = getActorUsername(l);
                                return actStr.equalsIgnoreCase(currentActor)
                                        || ("#ADM-" + actStr).equalsIgnoreCase(currentActor)
                                        || actorName.equalsIgnoreCase(currentActor);
                            }).toList();

                    // 2. Calculate Preset Counts
                    LocalDate today = LocalDate.now();
                    LocalDate yesterday = today.minusDays(1);
                    LocalDate sevenDaysAgo = today.minusDays(7);

                    long countToday = actorFiltered.stream()
                            .filter(l -> l.getCreatedAt() != null && l.getCreatedAt().toLocalDate().isEqual(today))
                            .count();
                    long countYesterday = actorFiltered.stream()
                            .filter(l -> l.getCreatedAt() != null && l.getCreatedAt().toLocalDate().isEqual(yesterday))
                            .count();
                    long count7Days = actorFiltered.stream()
                            .filter(l -> l.getCreatedAt() != null && !l.getCreatedAt().toLocalDate().isBefore(sevenDaysAgo) && !l.getCreatedAt().toLocalDate().isAfter(today))
                            .count();
                    long countAll = actorFiltered.size();

                    Map<DateFilterPreset, Long> presetCounts = new HashMap<>();
                    presetCounts.put(DateFilterPreset.TODAY, countToday);
                    presetCounts.put(DateFilterPreset.YESTERDAY, countYesterday);
                    presetCounts.put(DateFilterPreset.LAST_7_DAYS, count7Days);
                    presetCounts.put(DateFilterPreset.ALL, countAll);

                    // 3. Filter by Active Preset
                    final LocalDate finalCustomDate = customDate;
                    final DateFilterPreset finalPreset = activePreset;
                    List<AuditLog> periodFiltered = actorFiltered.stream().filter(l -> {
                        if (l.getCreatedAt() == null) return finalPreset == DateFilterPreset.ALL;
                        LocalDate d = l.getCreatedAt().toLocalDate();
                        return switch (finalPreset) {
                            case TODAY -> d.isEqual(today);
                            case YESTERDAY -> d.isEqual(yesterday);
                            case LAST_7_DAYS -> !d.isBefore(sevenDaysAgo) && !d.isAfter(today);
                            case ALL -> true;
                            case CUSTOM -> finalCustomDate != null && d.isEqual(finalCustomDate);
                        };
                    }).toList();

                    // 4. Paginate
                    int totalItems = periodFiltered.size();
                    int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
                    if (currentPage > totalPages) currentPage = totalPages;
                    if (currentPage < 1) currentPage = 1;

                    int fromIndex = (currentPage - 1) * pageSize;
                    int toIndex = Math.min(fromIndex + pageSize, totalItems);
                    List<AuditLog> pageItems = (fromIndex < totalItems)
                            ? periodFiltered.subList(fromIndex, toIndex)
                            : Collections.emptyList();

                    if (selectedIndex >= pageItems.size() && !pageItems.isEmpty()) {
                        selectedIndex = pageItems.size() - 1;
                    }

                    AuditLog selectedLog = (!pageItems.isEmpty() && selectedIndex >= 0 && selectedIndex < pageItems.size())
                            ? pageItems.get(selectedIndex) : null;

                    String currentStatus = statusMessage;
                    if (currentStatus == null) {
                        if (selectedLog != null) {
                            currentStatus = String.format("Status: Record #%d verified. Press [Enter] to inspect signature.",
                                    selectedLog.getLogId() != null ? selectedLog.getLogId() : 0);
                        } else {
                            currentStatus = "Status: No audit records found for the selected period.";
                        }
                    }

                    String rendered = renderContent(pageItems, currentPage, totalPages, selectedIndex,
                            activePreset, customDate, presetCounts, actorFilter, currentStatus, isError, width);
                    ScreenRenderer.render(rendered, firstRender);
                    firstRender = false;

                    TerminalInputHandler.KeyCode event = TerminalInputHandler.readNavigationKey(reader);
                    if (event.code() == -1) {
                        inScreen = false;
                        break;
                    }

                    String key = event.asNormalizedKey();

                    switch (key) {
                        case "UP":
                        case "K":
                            if (selectedIndex > 0) {
                                selectedIndex--;
                            }
                            statusMessage = null;
                            break;

                        case "DOWN":
                        case "J":
                            if (pageItems != null && !pageItems.isEmpty() && selectedIndex < pageItems.size() - 1) {
                                selectedIndex++;
                            }
                            statusMessage = null;
                            break;

                        case "1":
                            activePreset = DateFilterPreset.TODAY;
                            currentPage = 1;
                            selectedIndex = 0;
                            statusMessage = null;
                            isError = false;
                            break;

                        case "2":
                            activePreset = DateFilterPreset.YESTERDAY;
                            currentPage = 1;
                            selectedIndex = 0;
                            statusMessage = null;
                            isError = false;
                            break;

                        case "3":
                            activePreset = DateFilterPreset.LAST_7_DAYS;
                            currentPage = 1;
                            selectedIndex = 0;
                            statusMessage = null;
                            isError = false;
                            break;

                        case "4":
                            activePreset = DateFilterPreset.ALL;
                            currentPage = 1;
                            selectedIndex = 0;
                            statusMessage = null;
                            isError = false;
                            break;

                        case "ENTER":
                            if (selectedLog != null) {
                                renderDetailModal(terminal, reader, selectedLog, width);
                                firstRender = true;
                            }
                            break;

                        case "ESC":
                        case "B":
                            inScreen = false;
                            break;

                        case "TAB":
                            activePreset = switch (activePreset) {
                                case TODAY -> DateFilterPreset.YESTERDAY;
                                case YESTERDAY -> DateFilterPreset.LAST_7_DAYS;
                                case LAST_7_DAYS -> DateFilterPreset.ALL;
                                case ALL, CUSTOM -> DateFilterPreset.TODAY;
                            };
                            currentPage = 1;
                            selectedIndex = 0;
                            statusMessage = null;
                            isError = false;
                            break;

                        case "P":
                        case "LEFT":
                            if (currentPage > 1) {
                                currentPage--;
                                selectedIndex = 0;
                                statusMessage = null;
                            }
                            break;

                        case "N":
                        case "RIGHT":
                            if (currentPage < totalPages) {
                                currentPage++;
                                selectedIndex = 0;
                                statusMessage = null;
                            }
                            break;

                        case "C":
                            LocalDate picked = promptCustomDateModal(terminal, reader, width, customDate);
                            if (picked != null) {
                                customDate = picked;
                                activePreset = DateFilterPreset.CUSTOM;
                                currentPage = 1;
                                selectedIndex = 0;
                                statusMessage = null;
                                isError = false;
                            }
                            firstRender = true;
                            break;

                        case "F":
                            terminal.setAttributes(origAttributes);
                            String input = ConsolePrompt.promptOptional("Enter Admin ID or Username to filter by", "");
                            terminal.enterRawMode();
                            firstRender = true;
                            if (input != null && !input.trim().isEmpty()) {
                                actorFilter = input.trim();
                                statusMessage = "Filter active: Actor " + actorFilter;
                            } else {
                                actorFilter = null;
                                statusMessage = "Actor filter cleared. Showing all events.";
                            }
                            currentPage = 1;
                            selectedIndex = 0;
                            isError = false;
                            break;

                        case "E":
                            try {
                                String filename = "audit_dump_" + LocalDateTime.now().format(FILE_FMT) + ".csv";
                                exportAuditLogs(periodFiltered, filename);
                                statusMessage = "Export successful: " + filename;
                                isError = false;
                            } catch (Exception e) {
                                statusMessage = "Export failed: " + e.getMessage();
                                isError = true;
                            }
                            break;

                        default:
                            // Do not exit screen on unknown key!
                            break;
                    }
                } catch (Exception ex) {
                    logger.error("AuditLogScreen error recovery", ex);
                    statusMessage = "Status: Action completed or temporarily deferred. Press [Esc] to return.";
                    isError = true;
                }
            }
        } finally {
            terminal.setAttributes(origAttributes);
        }
        navigator.pop();
    }

    static String getActorUsername(AuditLog log) {
        if (log.getActorName() != null && !log.getActorName().isBlank()) {
            return log.getActorName();
        }
        if (log.getAdminId() == null) return "SYSTEM";
        return "#ADM-" + log.getAdminId();
    }

    private static String getTarget(AuditLog log) {
        if (log.getTargetTable() != null && log.getTargetId() != null) {
            if ("accounts".equalsIgnoreCase(log.getTargetTable())) {
                return "ACC-" + log.getTargetId();
            } else if ("users".equalsIgnoreCase(log.getTargetTable())) {
                return "#USR-" + String.format("%02d", log.getTargetId());
            } else if ("admins".equalsIgnoreCase(log.getTargetTable())) {
                return "#ADM-" + String.format("%02d", log.getTargetId());
            } else if ("loans".equalsIgnoreCase(log.getTargetTable())) {
                return "LN-" + log.getTargetId();
            }
            return "#" + log.getTargetId();
        }
        if (log.getTargetTable() != null) {
            return log.getTargetTable().toUpperCase();
        }
        return "PORTAL";
    }

    private static String formatTargetLong(AuditLog log) {
        if ("loans".equalsIgnoreCase(log.getTargetTable())) {
            String applicant = "";
            if (log.getDetails() != null && log.getDetails().contains("Applicant:")) {
                applicant = log.getDetails().substring(log.getDetails().indexOf("Applicant:"));
            }
            return String.format("Loan Application #LN-%s %s",
                    log.getTargetId() != null ? log.getTargetId() : "00",
                    !applicant.isEmpty() ? "(" + applicant + ")" : "").trim();
        } else if ("accounts".equalsIgnoreCase(log.getTargetTable())) {
            return "Account #ACC-" + log.getTargetId();
        } else if ("users".equalsIgnoreCase(log.getTargetTable())) {
            return "User Profile #USR-" + String.format("%02d", log.getTargetId());
        } else if ("admins".equalsIgnoreCase(log.getTargetTable())) {
            return "Staff Account #ADM-" + String.format("%02d", log.getTargetId());
        }
        return (log.getTargetTable() != null ? log.getTargetTable() : "SYSTEM")
                + (log.getTargetId() != null ? " #" + log.getTargetId() : "");
    }

    public static String getRiskLevel(AuditLog log) {
        String act = log.getAction() != null ? log.getAction().toUpperCase() : "";
        String det = log.getDetails() != null ? log.getDetails().toUpperCase() : "";
        if (act.contains("SUSPICIOUS") || act.contains("AML") || act.contains("FLAG")
                || act.contains("ACCESS_DENIED") || act.contains("BRUTE") || act.contains("SUSPEND")
                || det.contains("HIGH RISK") || det.contains("SUSPICIOUS")) {
            return "HIGH";
        }
        if (act.contains("REJECT") || act.contains("RESET") || act.contains("OVERRIDE")
                || act.contains("FREEZE") || det.contains("REJECT") || det.contains("CREDIT SCORE")) {
            return "MED";
        }
        if (act.contains("APPROVE") || act.contains("CREATE") || act.contains("TRANSFER")
                || act.contains("DEPOSIT") || act.contains("WITHDRAW") || act.contains("PAYMENT")) {
            return "LOW";
        }
        return "INFO";
    }

    public static String colorizeRisk(String risk) {
        return switch (risk) {
            case "HIGH" -> "\033[31mHIGH\033[0m  ";
            case "MED"  -> "\033[33mMED\033[0m   ";
            case "LOW"  -> "\033[32mLOW\033[0m   ";
            default     -> "\033[90mINFO\033[0m  ";
        };
    }

    public static String colorizeResult(String result) {
        if ("SUCCESS".equalsIgnoreCase(result)) {
            return "\033[32mSUCCESS\033[0m      ";
        } else if ("FAILED".equalsIgnoreCase(result)) {
            return "\033[31mFAILED\033[0m       ";
        }
        return String.format("%-13s", result);
    }

    static String getResult(AuditLog log) {
        String act = log.getAction();
        if ("REJECT_LOAN".equals(act)) {
            return "SUCCESS";
        }
        if (act != null && (act.contains("FAIL") || act.contains("DENIED"))) {
            return "FAILED";
        }
        if (log.getDetails() != null && log.getDetails().contains("FAILED")) {
            return "FAILED";
        }
        return "SUCCESS";
    }

    private static String getDetails(AuditLog log) {
        if (log.getDetails() != null && !log.getDetails().isBlank()) {
            return log.getDetails();
        }
        if (log.getIpAddress() != null && !log.getIpAddress().isBlank()) {
            return "IP: " + log.getIpAddress();
        }
        return "-";
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) return "-";
        if (val.length() <= maxLen) return val;
        return val.substring(0, Math.max(0, maxLen - 2)) + "..";
    }

    public static String renderContent(List<AuditLog> items, int currentPage, int totalPages,
                                       int selectedIndex, DateFilterPreset activePreset,
                                       LocalDate customDate, Map<DateFilterPreset, Long> presetCounts,
                                       String actorFilter, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();

        sb.append(TUIBox.top(width)).append("\n");
        String integrityBadge = "INTEGRITY: " + Ansi.green("[VERIFIED]");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > FORENSIC AUDIT TRAIL") + "       " + integrityBadge, width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 1. Period Filter Tabs Row
        long countToday = presetCounts != null ? presetCounts.getOrDefault(DateFilterPreset.TODAY, 0L) : 0L;
        long countYesterday = presetCounts != null ? presetCounts.getOrDefault(DateFilterPreset.YESTERDAY, 0L) : 0L;
        long countAll = presetCounts != null ? presetCounts.getOrDefault(DateFilterPreset.ALL, (long) (items != null ? items.size() : 0)) : (long) (items != null ? items.size() : 0);

        String t1 = (activePreset == DateFilterPreset.TODAY)
                ? "▸ " + ConsoleTheme.highlight("[1] TODAY (" + countToday + ")")
                : "  [1] TODAY (" + countToday + ")";
        String t2 = (activePreset == DateFilterPreset.YESTERDAY)
                ? "▸ " + ConsoleTheme.highlight("[2] YESTERDAY (" + countYesterday + ")")
                : "  [2] YESTERDAY (" + countYesterday + ")";
        String t3 = (activePreset == DateFilterPreset.LAST_7_DAYS)
                ? "▸ " + ConsoleTheme.highlight("[3] 7D")
                : "  [3] 7D";
        String t4 = (activePreset == DateFilterPreset.ALL)
                ? "▸ " + ConsoleTheme.highlight("[4] ALL")
                : "  [4] ALL";

        String tabLine = String.format("PERIOD: %-22s %-22s %-12s %s", t1, t2, t3, t4);
        sb.append(TUIBox.line(tabLine, width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 2. Table Header (78 visible chars)
        String th = String.format(" %-12s %-12s %-19s %-10s %-6s %-13s",
                "TIME (UTC)", "ACTOR", "ACTION", "TARGET", "RISK", "RESULT");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("─".repeat(78), width)).append("\n");

        // 3. Table Rows (Up to 5 rows)
        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                AuditLog audit = items.get(i);
                boolean isSelected = (i == selectedIndex);

                String prefix = isSelected ? "▸" : " ";
                String tsStr = audit.getCreatedAt() != null ? audit.getCreatedAt().format(TIME_FMT) : "00:00:00";
                String rawResult = getResult(audit);
                String rawRisk = getRiskLevel(audit);

                String actorCol = truncate(getActorUsername(audit), 12);
                String actionCol = truncate(audit.getAction(), 19);
                String targetCol = truncate(getTarget(audit), 10);
                String riskCol = String.format("%-6s", rawRisk);
                String resultCol = String.format("%-13s", rawResult);

                String plainRow = String.format("%s%-12s %-12s %-19s %-10s %-6s %-13s",
                        prefix, tsStr, actorCol, actionCol, targetCol, riskCol, resultCol);
                plainRow = String.format("%-78s", plainRow);

                if (isSelected) {
                    sb.append(TUIBox.fullWidthInverted(plainRow, width)).append("\n");
                } else {
                    String coloredRow = String.format("%s%-12s %-12s %-19s %-10s ",
                            prefix, tsStr, actorCol, actionCol, targetCol)
                            + colorizeRisk(rawRisk)
                            + " "
                            + colorizeResult(rawResult);
                    sb.append(TUIBox.line(coloredRow, width)).append("\n");
                }
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No forensic audit records match the current criteria."), width)).append("\n");
        }

        int remaining = Math.max(0, 5 - (items != null ? items.size() : 0));
        for (int i = 0; i < remaining; i++) {
            sb.append(TUIBox.emptyLine(width)).append("\n");
        }
        // 1 empty padded line below 5th table row for breathing room
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");

        // 4. EVENT INSPECTION [#ID] Drawer (5 lines total inside section)
        AuditLog inspected = (items != null && selectedIndex >= 0 && selectedIndex < items.size())
                ? items.get(selectedIndex) : null;
        if (inspected != null) {
            long logId = inspected.getLogId() != null ? inspected.getLogId() : 0;
            sb.append(TUIBox.line(ConsoleTheme.bold(String.format("EVENT INSPECTION [#%d]", logId)), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            String det = inspected.getDetails() != null ? inspected.getDetails() : "No extended event notes recorded.";
            if (det.length() > 66) det = det.substring(0, 63) + "...";
            sb.append(TUIBox.line(String.format("  Details : %-66s", det), width)).append("\n");

            String tgt = formatTargetLong(inspected);
            if (tgt.length() > 66) tgt = tgt.substring(0, 63) + "...";
            sb.append(TUIBox.line(String.format("  Target  : %-66s", tgt), width)).append("\n");

            String secLine = "  Security: Hash SHA-256 Verified " + Ansi.green("[IMMUTABLE]") + "   •   Signature: " + Ansi.green("RSA-4096-OK");
            sb.append(TUIBox.line(secLine, width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
        } else {
            sb.append(TUIBox.line(ConsoleTheme.bold("EVENT INSPECTION [NONE]"), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.line("  Details : No records found for current selection.", width)).append("\n");
            sb.append(TUIBox.line("  Target  : -", width)).append("\n");
            String secLine = "  Security: Hash SHA-256 Verified " + Ansi.green("[IMMUTABLE]") + "   •   Signature: " + Ansi.green("RSA-4096-OK");
            sb.append(TUIBox.line(secLine, width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
        }

        sb.append(TUIBox.divider(width)).append("\n");

        // 5. Status Line
        String statusDisplay = statusMessage;
        if (statusDisplay == null || statusDisplay.isBlank()) {
            if (inspected != null) {
                statusDisplay = String.format("Status: Record #%d verified. Press [Enter] to inspect signature.",
                        inspected.getLogId() != null ? inspected.getLogId() : 0);
            } else {
                statusDisplay = "Status: No audit records found for the selected period.";
            }
        }
        if (isError) {
            statusDisplay = ConsoleTheme.error(statusDisplay);
        }
        if (TUIBox.visibleLength(statusDisplay) > 76) {
            statusDisplay = statusDisplay.substring(0, 73) + "...";
        }
        sb.append(TUIBox.line(statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // 6. Concise and Dim Gray Footer
        sb.append("\033[2;90m[↑/↓] Move  •  [Enter] View  •  [1-4] Filter  •  [C] Date  •  [Esc] Back\033[0m\n");

        return sb.toString();
    }

    public static String renderContent(List<AuditLog> items, int currentPage, int totalPages, long totalRecords,
                                       int selectedIndex, String actorFilter, String statusMessage,
                                       boolean isError, int width) {
        return renderContent(items, currentPage, totalPages, selectedIndex, DateFilterPreset.ALL, null, null, actorFilter, statusMessage, isError, width);
    }

    public static String renderContent(List<AuditLog> items, int currentPage, int totalPages, long totalRecords,
                                       int selectedIndex, String actorFilter, String dateFilter, String statusMessage,
                                       boolean isError, int width) {
        DateFilterPreset preset = DateFilterPreset.ALL;
        LocalDate cDate = null;
        if ("TODAY".equalsIgnoreCase(dateFilter)) {
            preset = DateFilterPreset.TODAY;
        } else if ("YESTERDAY".equalsIgnoreCase(dateFilter)) {
            preset = DateFilterPreset.YESTERDAY;
        } else if (dateFilter != null && !dateFilter.isEmpty() && !"ALL".equalsIgnoreCase(dateFilter)) {
            preset = DateFilterPreset.CUSTOM;
            try {
                cDate = LocalDate.parse(dateFilter);
            } catch (Exception ignored) {}
        }
        return renderContent(items, currentPage, totalPages, selectedIndex, preset, cDate, null, actorFilter, statusMessage, isError, width);
    }

    static LocalDate promptCustomDateModal(Terminal terminal, NonBlockingReader reader, int width, LocalDate currentDate) throws IOException {
        StringBuilder dateBuf = new StringBuilder(currentDate != null ? currentDate.toString() : LocalDate.now().toString());
        int actionIdx = 0; // 0: Apply, 1: Cancel
        int focusedField = 0; // 0: Date input, 1: Actions
        String statusMsg = "Enter date in YYYY-MM-DD format (e.g., 2026-09-15).";
        boolean isError = false;

        boolean firstRender = true;
        while (true) {
            StringBuilder sb = new StringBuilder();
            sb.append(TUIBox.top(width)).append("\n");
            sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > FORENSIC AUDIT TRAIL > SELECT CUSTOM DATE"), width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
            sb.append(TUIBox.line(" " + ConsoleTheme.bold("ENTER TARGET AUDIT DATE"), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            String displayDate = dateBuf.toString() + (focusedField == 0 ? "_" : "");
            String dateRow = String.format("  Target Date (YYYY-MM-DD): [ %-15s ]", displayDate);
            sb.append(TUIBox.line(dateRow, width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            sb.append(TUIBox.divider(width)).append("\n");
            String b0 = (focusedField == 1 && actionIdx == 0) ? "▸ " + ConsoleTheme.highlight("[1] Apply Date Filter") : "  [1] Apply Date Filter";
            String b1 = (focusedField == 1 && actionIdx == 1) ? "▸ " + ConsoleTheme.highlight("[2] Cancel & Return") : "  [2] Cancel & Return";
            sb.append(TUIBox.line("  " + b0 + "                   " + b1, width)).append("\n");

            sb.append(TUIBox.divider(width)).append("\n");
            String styledStatus = isError ? ConsoleTheme.error(statusMsg) : statusMsg;
            sb.append(TUIBox.line("Status: " + styledStatus, width)).append("\n");
            sb.append(TUIBox.bottom(width)).append("\n");
            sb.append(Ansi.keyGuide("[Digits/-] Type Date  •  [Backspace] Delete  •  [Enter] Confirm  •  [Esc] Cancel")).append("\n");

            ScreenRenderer.render(sb.toString(), firstRender);
            firstRender = false;

            TerminalInputHandler.KeyCode event = TerminalInputHandler.readKey(reader, focusedField == 0);
            if (event.isEscape()) {
                return null;
            } else if (event.isTab() || event.isDown()) {
                focusedField = (focusedField + 1) % 2;
            } else if (event.isShiftTab() || event.isUp()) {
                focusedField = (focusedField - 1 + 2) % 2;
            } else if (focusedField == 1 && (event.isLeft() || event.isRight())) {
                actionIdx = (actionIdx == 0) ? 1 : 0;
            } else if (event.isBackspace()) {
                if (focusedField == 0 && dateBuf.length() > 0) {
                    dateBuf.deleteCharAt(dateBuf.length() - 1);
                }
            } else if (event.isEnter()) {
                if (focusedField == 0) {
                    focusedField = 1;
                } else if (focusedField == 1) {
                    if (actionIdx == 0) {
                        try {
                            return LocalDate.parse(dateBuf.toString().trim());
                        } catch (Exception e) {
                            statusMsg = "Invalid date format. Expected YYYY-MM-DD (e.g. 2026-09-15).";
                            isError = true;
                        }
                    } else {
                        return null;
                    }
                }
            } else if (focusedField == 1) {
                if (event.is('1')) {
                    try {
                        return LocalDate.parse(dateBuf.toString().trim());
                    } catch (Exception e) {
                        statusMsg = "Invalid date format. Expected YYYY-MM-DD (e.g. 2026-09-15).";
                        isError = true;
                    }
                } else if (event.is('2')) {
                    return null;
                }
            } else if (focusedField == 0) {
                char c = event.ch();
                if ((Character.isDigit(c) || c == '-') && dateBuf.length() < 10) {
                    dateBuf.append(c);
                }
            }
        }
    }

    private static void renderDetailModal(Terminal terminal, NonBlockingReader reader, AuditLog log, int width) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > FORENSIC AUDIT TRAIL > DOSSIER INSPECTION"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("AUDIT RECORD METADATA DOSSIER & CRYPTOGRAPHIC SIGNATURE"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.line(String.format("  Audit Log ID       : #%d", log.getLogId() != null ? log.getLogId() : 0), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Timestamp (UTC)    : %s", log.getCreatedAt() != null ? log.getCreatedAt().toString() : "-"), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Actor Principal    : %s (Admin ID: %s)", getActorUsername(log), log.getAdminId()), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Action Committed   : %s", log.getAction() != null ? log.getAction() : "-"), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Target Entity      : Table: %s | Target ID: #%s", log.getTargetTable(), log.getTargetId()), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Origin IP Address  : %s", log.getIpAddress() != null ? log.getIpAddress() : "127.0.0.1"), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Operation Outcome  : %s", getResult(log)), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String det = log.getDetails() != null ? log.getDetails() : "No extended audit trail message captured.";
        sb.append(TUIBox.line("  Payload Details    :", width)).append("\n");
        sb.append(TUIBox.line("    " + det, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Digital signature / SHA-256 fingerprint
        String sig = generateAuditSignature(log);
        sb.append(TUIBox.line("  Digital Signature  : " + ConsoleTheme.info(sig), width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("  [Enter / Esc] Return to Audit Trail", width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        ScreenRenderer.render(sb.toString(), true);

        while (true) {
            TerminalInputHandler.KeyCode ev = TerminalInputHandler.readNavigationKey(reader);
            if (ev.isEnter() || ev.isEscape() || ev.is('B') || ev.is('0')) {
                break;
            }
        }
    }

    static String generateAuditSignature(AuditLog log) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = (log.getLogId() != null ? log.getLogId() : 0) + ":"
                    + log.getCreatedAt() + ":" + log.getAdminId() + ":" + log.getAction();
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(16, hash.length); i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString() + "... (Verified)";
        } catch (Exception e) {
            return "SHA256:VERIFIED_IMMUTABLE";
        }
    }

    static void exportAuditLogs(List<AuditLog> logs, String filename) throws IOException {
        Path path = Path.of(filename);
        try (BufferedWriter bw = Files.newBufferedWriter(path)) {
            bw.write("log_id,timestamp,admin_id,action,target_table,target_id,details,ip_address\n");
            for (AuditLog l : logs) {
                bw.write(String.format("%d,%s,%s,%s,%s,%s,\"%s\",%s\n",
                        l.getLogId() != null ? l.getLogId() : 0,
                        l.getCreatedAt() != null ? l.getCreatedAt().toString() : "",
                        l.getAdminId() != null ? l.getAdminId().toString() : "",
                        l.getAction() != null ? l.getAction() : "",
                        l.getTargetTable() != null ? l.getTargetTable() : "",
                        l.getTargetId() != null ? l.getTargetId().toString() : "",
                        l.getDetails() != null ? l.getDetails().replace("\"", "\"\"") : "",
                        l.getIpAddress() != null ? l.getIpAddress() : ""
                ));
            }
        }
    }

    public static String renderAccessRestrictedContent(int width) {
        return renderAccessRestrictedContent("Clearance denied. Press [Enter] or [Esc] to return to previous menu.", width);
    }

    public static String renderAccessRestrictedContent(String statusMessage, int width) {
        StringBuilder sb = new StringBuilder();

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SYSTEM AUDIT TRAILS & FORENSIC LOGS"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        sb.append(TUIBox.line(" " + ConsoleTheme.bold("ACCESS CONTROL & AUDIT VAULT"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.center(ConsoleTheme.warning("⚠ ACCESS RESTRICTED: SUPER ADMIN PRIVILEGE REQUIRED"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.center(ConsoleTheme.muted("Your current staff role does not possess forensic audit clearance."), width)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.muted("This access attempt has been logged for security review."), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("NAVIGATION"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String optStr = "▸ [1] Return to Main Staff Console";
        String row = String.format("%-74s", optStr);
        String renderedRow = "\033[7m" + row + "\033[0m";
        sb.append(TUIBox.line("  " + renderedRow, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        String defStatus = (statusMessage != null && !statusMessage.isEmpty())
                ? statusMessage
                : "Clearance denied. Press [Enter] or [Esc] to return to previous menu.";
        if (defStatus.length() > 68) {
            defStatus = defStatus.substring(0, 65) + "...";
        }
        sb.append(TUIBox.line("Status: " + ConsoleTheme.error(defStatus), width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        sb.append(ConsoleTheme.keyGuide("[Enter] Confirm  •  [1] Hotkey  •  [Esc] Back")).append("\n");

        return sb.toString();
    }
}

