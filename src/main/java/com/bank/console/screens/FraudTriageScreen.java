package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.TerminalInputHandler;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.model.entity.Account;
import com.bank.model.entity.Admin;
import com.bank.model.entity.FraudAlert;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.FraudStatus;
import com.bank.model.enums.RiskLevel;
import com.bank.model.enums.UserStatus;
import com.bank.security.SessionManager;
import com.bank.ui.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * COMPLIANCE OFFICER > FRAUD THREAT TRIAGE & AML AUDIT (82 Columns)
 * Dedicated incident triage operations with max 6 rows pagination, tabs, and dossier drawer.
 */
public class FraudTriageScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(FraudTriageScreen.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AdminController adminController;
    private final FraudAlert initialAlert;

    public FraudTriageScreen() {
        this(ControllerFactory.getAdminController(), null);
    }

    public FraudTriageScreen(FraudAlert initialAlert) {
        this(ControllerFactory.getAdminController(), initialAlert);
    }

    public FraudTriageScreen(AdminController adminController) {
        this(adminController, null);
    }

    public FraudTriageScreen(AdminController adminController, FraudAlert initialAlert) {
        this.adminController = adminController;
        this.initialAlert = initialAlert;
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
        // Drain any lingering newline characters on entry
        TerminalInputHandler.drainBuffer(reader);

        int selectedIndex = 0;
        int filterIndex = 0; // 0 = UNRESOLVED, 1 = HIGH/CRIT, 2 = ALL
        int page = 1;
        final int pageSize = 6;

        String statusMessage = null;
        boolean isError = false;
        boolean firstRender = true;
        boolean initialAlertPositioned = false;
        boolean inScreen = true;

        try {
            while (inScreen) {
                List<FraudAlert> allAlerts;
                try {
                    allAlerts = adminController.getAllFraudAlerts(admin);
                    if (allAlerts == null) allAlerts = Collections.emptyList();
                } catch (Exception e) {
                    logger.error("Error loading fraud alerts", e);
                    allAlerts = Collections.emptyList();
                }

                // Filter alerts
                List<FraudAlert> filteredAlerts = new ArrayList<>();
                for (FraudAlert a : allAlerts) {
                    boolean unresolved = (a.getStatus() == FraudStatus.OPEN || a.getStatus() == FraudStatus.INVESTIGATING);
                    boolean highCrit = (a.getRiskLevel() == RiskLevel.HIGH);

                    if (filterIndex == 0 && !unresolved) continue;
                    if (filterIndex == 1 && !highCrit) continue;
                    filteredAlerts.add(a);
                }

                // Sort by urgency: HIGH > MEDIUM > LOW, then createdAt desc
                filteredAlerts.sort((a, b) -> {
                    int rA = a.getRiskLevel() == RiskLevel.HIGH ? 3 : (a.getRiskLevel() == RiskLevel.MEDIUM ? 2 : 1);
                    int rB = b.getRiskLevel() == RiskLevel.HIGH ? 3 : (b.getRiskLevel() == RiskLevel.MEDIUM ? 2 : 1);
                    if (rA != rB) return Integer.compare(rB, rA);
                    if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    }
                    return 0;
                });

                if (initialAlert != null && !initialAlertPositioned) {
                    initialAlertPositioned = true;
                    for (int i = 0; i < filteredAlerts.size(); i++) {
                        if (filteredAlerts.get(i).getAlertId().equals(initialAlert.getAlertId())) {
                            page = (i / pageSize) + 1;
                            selectedIndex = i % pageSize;
                            break;
                        }
                    }
                }

                int totalPages = Math.max(1, (int) Math.ceil((double) filteredAlerts.size() / pageSize));
                if (page > totalPages) page = totalPages;
                if (page < 1) page = 1;

                int pageCount = Math.min(pageSize, Math.max(0, filteredAlerts.size() - (page - 1) * pageSize));
                if (selectedIndex >= pageCount && pageCount > 0) {
                    selectedIndex = pageCount - 1;
                }

                int currentGlobalIdx = (page - 1) * pageSize + selectedIndex;
                FraudAlert currentSelectedAlert = (currentGlobalIdx < filteredAlerts.size()) ? filteredAlerts.get(currentGlobalIdx) : null;

                // Dynamic default status message if none set or on default
                if (statusMessage == null || statusMessage.startsWith("Surveillance queue") || statusMessage.startsWith("Status: Account") || statusMessage.startsWith("Status: Incident")) {
                    if (currentSelectedAlert != null) {
                        Account acc = null;
                        if (currentSelectedAlert.getAccountId() != null) {
                            try {
                                acc = adminController.getAccountById(admin, currentSelectedAlert.getAccountId());
                            } catch (Exception ignored) {}
                        }
                        boolean isFrozen = (acc != null && acc.getStatus() == AccountStatus.FROZEN)
                                || (currentSelectedAlert.getStatus() == FraudStatus.RESOLVED && currentSelectedAlert.getResolutionNotes() != null && currentSelectedAlert.getResolutionNotes().toLowerCase().contains("hold"));

                        if (isFrozen) {
                            statusMessage = String.format("Status: Account #ACC-%02d is currently FROZEN. Press [U] to release hold.",
                                    currentSelectedAlert.getAccountId() != null ? currentSelectedAlert.getAccountId() : 0);
                        } else if (currentSelectedAlert.getStatus() == FraudStatus.OPEN || currentSelectedAlert.getStatus() == FraudStatus.INVESTIGATING) {
                            statusMessage = String.format("Status: Incident #ALT-%02d selected. Press [H] to Freeze Account, [D] to Dismiss.",
                                    currentSelectedAlert.getAlertId());
                        } else {
                            statusMessage = String.format("Status: Incident #ALT-%02d selected (%s).",
                                    currentSelectedAlert.getAlertId(), currentSelectedAlert.getStatus());
                        }
                    } else {
                        statusMessage = "Status: Surveillance queue clear. No incidents matching current filter.";
                    }
                }

                String rendered = renderContent(allAlerts, filteredAlerts, selectedIndex, filterIndex, page, totalPages, statusMessage, isError, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                TerminalInputHandler.KeyCode event = TerminalInputHandler.readNavigationKey(reader);
                String key = event.asNormalizedKey();

                switch (key) {
                    case "ESC", "B", "0" -> {
                        inScreen = false;
                        navigator.pop();
                        return;
                    }
                    case "UP", "K" -> {
                        selectedIndex = TerminalInputHandler.moveSelectionClamped(selectedIndex, -1, pageCount);
                        statusMessage = null;
                    }
                    case "DOWN", "J" -> {
                        selectedIndex = TerminalInputHandler.moveSelectionClamped(selectedIndex, 1, pageCount);
                        statusMessage = null;
                    }
                    case "TAB" -> {
                        filterIndex = (filterIndex + 1) % 3;
                        page = 1;
                        selectedIndex = 0;
                        statusMessage = switch (filterIndex) {
                            case 0 -> "Filter: Showing UNRESOLVED incidents.";
                            case 1 -> "Filter: Showing HIGH/CRIT incidents.";
                            default -> "Filter: Showing ALL incidents.";
                        };
                        isError = false;
                    }
                    case "1" -> {
                        filterIndex = 0;
                        page = 1;
                        selectedIndex = 0;
                        statusMessage = "Filter: Showing UNRESOLVED incidents.";
                        isError = false;
                    }
                    case "2" -> {
                        filterIndex = 1;
                        page = 1;
                        selectedIndex = 0;
                        statusMessage = "Filter: Showing HIGH/CRIT incidents.";
                        isError = false;
                    }
                    case "3" -> {
                        filterIndex = 2;
                        page = 1;
                        selectedIndex = 0;
                        statusMessage = "Filter: Showing ALL incidents.";
                        isError = false;
                    }
                    case "N" -> {
                        if (page < totalPages) {
                            page++;
                            selectedIndex = 0;
                            statusMessage = String.format("Page %d/%d. Showing incidents %d-%d of %d.",
                                    page, totalPages, (page - 1) * pageSize + 1, Math.min(page * pageSize, filteredAlerts.size()), filteredAlerts.size());
                            isError = false;
                        }
                    }
                    case "P" -> {
                        if (page > 1) {
                            page--;
                            selectedIndex = 0;
                            statusMessage = String.format("Page %d/%d. Showing incidents %d-%d of %d.",
                                    page, totalPages, (page - 1) * pageSize + 1, Math.min(page * pageSize, filteredAlerts.size()), filteredAlerts.size());
                            isError = false;
                        }
                    }
                    case "U" -> {
                        // Unfreeze Keybinding [U]
                        int globalIdx = (page - 1) * pageSize + selectedIndex;
                        if (globalIdx < filteredAlerts.size()) {
                            FraudAlert target = filteredAlerts.get(globalIdx);
                            Account targetAcc = null;
                            if (target.getAccountId() != null) {
                                try {
                                    targetAcc = adminController.getAccountById(admin, target.getAccountId());
                                } catch (Exception ignored) {}
                            }
                            boolean isTargetFrozen = (targetAcc != null && targetAcc.getStatus() == AccountStatus.FROZEN)
                                    || (target.getStatus() == FraudStatus.RESOLVED && target.getResolutionNotes() != null && target.getResolutionNotes().toLowerCase().contains("hold"));

                            if (isTargetFrozen) {
                                try {
                                    if (target.getAccountId() != null) {
                                        adminController.unfreezeAccount(admin, target.getAccountId());
                                        adminController.resolveAlert(admin, target.getAlertId(), "Administrative hold lifted by officer.", false);
                                        statusMessage = String.format("Status: Hold lifted. Account #ACC-%02d restored to ACTIVE.", target.getAccountId());
                                    } else if (target.getUserId() != null) {
                                        adminController.toggleUserFreeze(admin, target.getUserId());
                                        adminController.resolveAlert(admin, target.getAlertId(), "Administrative hold lifted by officer.", false);
                                        statusMessage = String.format("Status: Hold lifted. User #USR-%02d restored to ACTIVE.", target.getUserId());
                                    } else {
                                        statusMessage = "Target identifier not available for unfreeze.";
                                    }
                                    isError = false;
                                } catch (Exception ex) {
                                    statusMessage = "Error lifting account hold: " + ex.getMessage();
                                    isError = true;
                                }
                            } else {
                                statusMessage = "Account is not currently frozen.";
                                isError = true;
                            }
                        }
                    }
                    case "H" -> {
                        // Hold / Freeze Keybinding [H] (only if active)
                        int globalIdx = (page - 1) * pageSize + selectedIndex;
                        if (globalIdx < filteredAlerts.size()) {
                            FraudAlert target = filteredAlerts.get(globalIdx);
                            Account targetAcc = null;
                            if (target.getAccountId() != null) {
                                try {
                                    targetAcc = adminController.getAccountById(admin, target.getAccountId());
                                } catch (Exception ignored) {}
                            }
                            boolean isTargetFrozen = (targetAcc != null && targetAcc.getStatus() == AccountStatus.FROZEN);

                            if (!isTargetFrozen && (target.getStatus() == FraudStatus.OPEN || target.getStatus() == FraudStatus.INVESTIGATING)) {
                                try {
                                    if (target.getAccountId() != null) {
                                        adminController.freezeAccount(admin, target.getAccountId(), "AML/Fraud Surveillance Hold: Incident #" + target.getAlertId());
                                        adminController.resolveAlert(admin, target.getAlertId(), "Enforced by #ADM-" + String.format("%02d", admin.getAdminId()) + " (" + admin.getUsername() + "). Case closed.", true);
                                        statusMessage = String.format("Status: Account #ACC-%02d has been placed on ADMINISTRATIVE HOLD (FROZEN).", target.getAccountId());
                                    } else if (target.getUserId() != null) {
                                        adminController.toggleUserFreeze(admin, target.getUserId());
                                        adminController.resolveAlert(admin, target.getAlertId(), "Enforced by #ADM-" + String.format("%02d", admin.getAdminId()) + " (" + admin.getUsername() + "). Case closed.", true);
                                        statusMessage = String.format("Status: User #USR-%02d has been placed on ADMINISTRATIVE HOLD (FROZEN).", target.getUserId());
                                    } else {
                                        statusMessage = "Target account identifier not available for hold action.";
                                    }
                                    isError = false;
                                } catch (Exception ex) {
                                    statusMessage = "Error applying account hold: " + ex.getMessage();
                                    isError = true;
                                }
                            } else if (isTargetFrozen) {
                                statusMessage = "Account is already on hold. Press [U] to release hold.";
                                isError = true;
                            }
                        }
                    }
                    case "D" -> {
                        // Dismiss Alert Keybinding [D] (only if unresolved)
                        int globalIdx = (page - 1) * pageSize + selectedIndex;
                        if (globalIdx < filteredAlerts.size()) {
                            FraudAlert target = filteredAlerts.get(globalIdx);
                            if (target.getStatus() == FraudStatus.OPEN || target.getStatus() == FraudStatus.INVESTIGATING) {
                                try {
                                    adminController.resolveAlert(admin, target.getAlertId(), "Dismissed as false positive following surveillance review", false);
                                    statusMessage = String.format("Status: Incident #ALT-%02d DISMISSED and archived as FALSE_POSITIVE.", target.getAlertId());
                                    isError = false;
                                } catch (Exception ex) {
                                    statusMessage = "Error dismissing incident: " + ex.getMessage();
                                    isError = true;
                                }
                            } else {
                                statusMessage = "Incident has already been resolved.";
                                isError = true;
                            }
                        }
                    }
                    case "ENTER" -> {
                        int globalIdx = (page - 1) * pageSize + selectedIndex;
                        if (globalIdx < filteredAlerts.size()) {
                            FraudAlert target = filteredAlerts.get(globalIdx);
                            Account targetAcc = null;
                            if (target.getAccountId() != null) {
                                try {
                                    targetAcc = adminController.getAccountById(admin, target.getAccountId());
                                } catch (Exception ignored) {}
                            }
                            boolean isTargetFrozen = (targetAcc != null && targetAcc.getStatus() == AccountStatus.FROZEN)
                                    || (target.getStatus() == FraudStatus.RESOLVED && target.getResolutionNotes() != null && target.getResolutionNotes().toLowerCase().contains("hold"));

                            if (isTargetFrozen) {
                                statusMessage = String.format("Status: Account #ACC-%02d is currently FROZEN. Press [U] to release hold.",
                                        target.getAccountId() != null ? target.getAccountId() : 0);
                            } else if (target.getStatus() == FraudStatus.OPEN || target.getStatus() == FraudStatus.INVESTIGATING) {
                                statusMessage = String.format("Status: Incident #ALT-%02d selected. Press [H] to Freeze Account, [D] to Dismiss.", target.getAlertId());
                            } else {
                                statusMessage = String.format("Status: Incident #ALT-%02d selected (Resolved).", target.getAlertId());
                            }
                            isError = false;
                        }
                    }
                    default -> {
                        // Discard unmapped keys without exiting or throwing
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in FraudTriageScreen loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderContent(List<FraudAlert> allAlerts, List<FraudAlert> filteredAlerts,
                                       int selectedIndex, int filterIndex, int page, int totalPages,
                                       String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > COMPLIANCE OFFICER > FRAUD THREAT TRIAGE & AML AUDIT"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Header & Tab Selection
        long totalAll = allAlerts != null ? allAlerts.size() : 0;
        long totalUnresolved = allAlerts != null ? allAlerts.stream().filter(a -> a.getStatus() == FraudStatus.OPEN || a.getStatus() == FraudStatus.INVESTIGATING).count() : 0;
        long totalHighCrit = allAlerts != null ? allAlerts.stream().filter(a -> a.getRiskLevel() == RiskLevel.HIGH).count() : 0;

        String secTitle = String.format("INCIDENT SURVEILLANCE FEED (Page %d/%d)", page, totalPages);
        sb.append(TUIBox.line(" " + ConsoleTheme.bold(secTitle), width)).append("\n");

        String tUnres = String.format("[1] UNRESOLVED (%d)", totalUnresolved);
        String tHighCrit = String.format("[2] HIGH/CRIT (%d)", totalHighCrit);
        String tAll = String.format("[3] ALL (%d)", totalAll);

        String tabLine = String.format("  [Tab] Cycle Filter:   %s   %s   %s",
                filterIndex == 0 ? ("\033[7m " + tUnres + " \033[0m") : Ansi.yellow(tUnres),
                filterIndex == 1 ? ("\033[7m " + tHighCrit + " \033[0m") : Ansi.yellow(tHighCrit),
                filterIndex == 2 ? ("\033[7m " + tAll + " \033[0m") : Ansi.yellow(tAll)
        );
        sb.append(TUIBox.line(tabLine, width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Table Header: strictly 74 chars inside border
        String th = String.format("  %-9s %-9s %-9s %-10s %-16s %-17s",
                "ALERT ID", "TARGET", "RISK", "STATUS", "LOGGED AT", "DESCRIPTION");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

        final int pageSize = 6;
        int startIdx = (page - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, filteredAlerts.size());

        FraudAlert selectedAlert = null;

        if (!filteredAlerts.isEmpty()) {
            for (int i = 0; i < pageSize; i++) {
                int alertIdx = startIdx + i;
                if (alertIdx < endIdx) {
                    FraudAlert a = filteredAlerts.get(alertIdx);
                    boolean isSel = (selectedIndex == i);
                    if (isSel) selectedAlert = a;

                    String prefix = isSel ? "▸" : " ";
                    String aid = "#ALT-" + (a.getAlertId() != null ? String.format("%02d", a.getAlertId()) : "01");
                    String target = a.getAccountId() != null ? String.format("#ACC-%02d", a.getAccountId())
                            : (a.getUserId() != null ? String.format("#USR-%02d", a.getUserId()) : "N/A");
                    String risk = a.getRiskLevel() != null ? a.getRiskLevel().name() : "LOW";
                    String st = a.getStatus() != null ? a.getStatus().name() : "OPEN";
                    String logged = a.getCreatedAt() != null ? a.getCreatedAt().format(DATE_FMT) : "2026-08-30 21:15";
                    String desc = a.getDescription() != null ? a.getDescription() : "Unspecified incident";
                    if (desc.length() > 17) desc = desc.substring(0, 14) + "...";

                    String plainRow = String.format("%s%-8s %-9s %-9s %-10s %-16s %-17s",
                            prefix, aid, target, risk, st, logged, desc);
                    if (plainRow.length() > 76) plainRow = plainRow.substring(0, 76);
                    plainRow = String.format("%-76s", plainRow);

                    if (isSel) {
                        sb.append(TUIBox.fullWidthInverted("  " + plainRow, width)).append("\n");
                    } else {
                        String riskColored = switch (risk) {
                            case "CRITICAL", "HIGH" -> Ansi.red(risk);
                            case "MEDIUM" -> Ansi.yellow(risk);
                            default -> ConsoleTheme.muted(risk);
                        };
                        String stColored = switch (st) {
                            case "OPEN" -> Ansi.red(st);
                            case "INVESTIGATING" -> Ansi.yellow(st);
                            case "CONFIRMED_FRAUD" -> Ansi.red(st);
                            case "FALSE_POSITIVE", "RESOLVED" -> Ansi.green(st);
                            default -> ConsoleTheme.muted(st);
                        };
                        String renderedRow = String.format("%s%-8s %-9s %s%s %s%s %-16s %-17s",
                                prefix, aid, target,
                                riskColored, " ".repeat(Math.max(0, 9 - risk.length())),
                                stColored, " ".repeat(Math.max(0, 10 - st.length())),
                                logged, desc);
                        sb.append(TUIBox.line("  " + renderedRow, width)).append("\n");
                    }
                } else {
                    sb.append(TUIBox.emptyLine(width)).append("\n");
                }
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No incidents detected matching current surveillance filter."), width)).append("\n");
            for (int i = 0; i < 5; i++) {
                sb.append(TUIBox.emptyLine(width)).append("\n");
            }
        }

        sb.append(TUIBox.divider(width)).append("\n");

        // Incident Dossier Drawer
        boolean selectedIsFrozen = false;
        boolean selectedIsUnresolved = false;

        if (selectedAlert != null) {
            String aid = "#ALT-" + (selectedAlert.getAlertId() != null ? String.format("%02d", selectedAlert.getAlertId()) : "01");
            String drawerTitle = String.format("INCIDENT DOSSIER [%s]", aid);
            sb.append(TUIBox.line(" " + ConsoleTheme.bold(drawerTitle), width)).append("\n");

            // Live account, user, and admin resolution
            Account targetAcc = null;
            User targetUser = null;
            Admin invAdmin = null;

            try {
                if (selectedAlert.getAccountId() != null) {
                    targetAcc = ControllerFactory.getAccountRepository().findById(selectedAlert.getAccountId()).orElse(null);
                }
                Long uid = (targetAcc != null && targetAcc.getUserId() != null) ? targetAcc.getUserId() : selectedAlert.getUserId();
                if (uid != null) {
                    targetUser = ControllerFactory.getUserRepository().findById(uid).orElse(null);
                }
                if (selectedAlert.getInvestigatedBy() != null) {
                    invAdmin = ControllerFactory.getAdminRepository().findById(selectedAlert.getInvestigatedBy()).orElse(null);
                }
            } catch (Exception ignored) {}

            selectedIsFrozen = (targetAcc != null && targetAcc.getStatus() == AccountStatus.FROZEN)
                    || (statusMessage != null && statusMessage.contains("FROZEN") && !statusMessage.contains("restored to ACTIVE"))
                    || (selectedAlert.getStatus() == FraudStatus.RESOLVED && selectedAlert.getResolutionNotes() != null
                    && selectedAlert.getResolutionNotes().toLowerCase().contains("hold") && (statusMessage == null || !statusMessage.contains("restored to ACTIVE")));

            selectedIsUnresolved = (selectedAlert.getStatus() == FraudStatus.OPEN || selectedAlert.getStatus() == FraudStatus.INVESTIGATING);

            // 1. Target Account
            String targetAccStr;
            if (selectedAlert.getAccountId() != null) {
                String accPart = String.format("#ACC-%02d", selectedAlert.getAccountId());
                if (targetUser != null) {
                    targetAccStr = String.format("%s (User: %s #USR-%02d)", accPart, targetUser.getFullName(), targetUser.getUserId());
                } else if (selectedAlert.getUserId() != null) {
                    targetAccStr = String.format("%s (User: #USR-%02d)", accPart, selectedAlert.getUserId());
                } else {
                    targetAccStr = accPart;
                }
            } else if (selectedAlert.getUserId() != null) {
                targetAccStr = targetUser != null
                        ? String.format("User: %s #USR-%02d", targetUser.getFullName(), targetUser.getUserId())
                        : String.format("#USR-%02d", selectedAlert.getUserId());
            } else {
                targetAccStr = "N/A";
            }
            sb.append(TUIBox.line("  Target Account : " + targetAccStr, width)).append("\n");

            // 2. Account Status
            String statusBadge;
            if (selectedIsFrozen) {
                statusBadge = Ansi.red("[ADMINISTRATIVE HOLD / FROZEN]");
            } else if (targetAcc != null && targetAcc.getStatus() == AccountStatus.ACTIVE) {
                statusBadge = Ansi.green("[ACTIVE]");
            } else if (targetAcc != null) {
                statusBadge = Ansi.yellow("[" + targetAcc.getStatus().name() + "]");
            } else if (targetUser != null && targetUser.getStatus() == UserStatus.SUSPENDED) {
                statusBadge = Ansi.red("[ADMINISTRATIVE HOLD / FROZEN]");
            } else {
                statusBadge = Ansi.green("[ACTIVE]");
            }
            sb.append(TUIBox.line("  Account Status : " + statusBadge, width)).append("\n");

            // 3. Trigger Reason: full non-truncated trigger reason without dots (...)
            String fullReason = selectedAlert.getDescription() != null ? selectedAlert.getDescription() : "Unspecified surveillance alert trigger";
            List<String> wrappedReason = wrapText(fullReason, 59);
            for (int r = 0; r < wrappedReason.size(); r++) {
                if (r == 0) {
                    sb.append(TUIBox.line("  Trigger Reason : " + wrappedReason.get(r), width)).append("\n");
                } else {
                    sb.append(TUIBox.line("                   " + wrappedReason.get(r), width)).append("\n");
                }
            }

            // 4. Resolution
            String resNote;
            if (selectedAlert.getResolutionNotes() != null && !selectedAlert.getResolutionNotes().isBlank()) {
                resNote = selectedAlert.getResolutionNotes();
            } else if (!selectedIsUnresolved) {
                if (invAdmin != null) {
                    resNote = String.format("Enforced by #ADM-%02d (%s). Case closed.", invAdmin.getAdminId(), invAdmin.getUsername());
                } else if (selectedAlert.getInvestigatedBy() != null) {
                    resNote = String.format("Enforced by #ADM-%02d. Case closed.", selectedAlert.getInvestigatedBy());
                } else {
                    resNote = "Case closed.";
                }
            } else {
                resNote = "Pending compliance officer review & triage.";
            }
            List<String> wrappedRes = wrapText(resNote, 59);
            for (int r = 0; r < wrappedRes.size(); r++) {
                if (r == 0) {
                    sb.append(TUIBox.line("  Resolution     : " + wrappedRes.get(r), width)).append("\n");
                } else {
                    sb.append(TUIBox.line("                   " + wrappedRes.get(r), width)).append("\n");
                }
            }

            sb.append(TUIBox.emptyLine(width)).append("\n");

            // 5. Contextual Action Buttons
            String actionLine;
            if (selectedIsFrozen) {
                actionLine = Ansi.yellow("[U] Unfreeze / Lift Hold") + "     " + ConsoleTheme.muted("[Esc] Back to Dashboard");
            } else if (selectedIsUnresolved) {
                actionLine = Ansi.yellow("[H] Hold / Freeze Account") + "  •  " + Ansi.green("[D] Dismiss / False Positive");
            } else {
                actionLine = ConsoleTheme.muted("[Esc] Back to Dashboard");
            }
            sb.append(TUIBox.line("  Next Actions   : " + actionLine, width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
        }

        // Status Line
        String cleanStatus = statusMessage != null ? statusMessage : "Ready.";
        if (cleanStatus.startsWith("Status: ")) {
            cleanStatus = cleanStatus.substring(8);
        }
        if (cleanStatus.length() > 70) {
            cleanStatus = cleanStatus.substring(0, 67) + "...";
        }
        String statusDisplay = isError ? ConsoleTheme.error(cleanStatus) : ConsoleTheme.muted(cleanStatus);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint: dynamically updated based on UNRESOLVED vs RESOLVED/FROZEN
        String footerHints;
        if (selectedIsFrozen) {
            footerHints = (totalPages > 1)
                    ? "[↑/↓] Select  •  [Tab] Filter  •  [N/P] Page  •  [U] Unfreeze  •  [Esc] Back"
                    : "[↑/↓] Select  •  [Tab] Filter  •  [U] Unfreeze  •  [Esc] Back";
        } else if (selectedIsUnresolved) {
            footerHints = (totalPages > 1)
                    ? "[↑/↓] Select  •  [Tab] Filter  •  [N/P] Page  •  [H] Hold Account  •  [D] Dismiss  •  [Esc] Back"
                    : "[↑/↓] Select  •  [Tab] Filter  •  [H] Hold Account  •  [D] Dismiss  •  [Esc] Back";
        } else {
            footerHints = (totalPages > 1)
                    ? "[↑/↓] Select  •  [Tab] Filter  •  [N/P] Page  •  [Esc] Back"
                    : "[↑/↓] Select  •  [Tab] Filter  •  [Esc] Back";
        }

        sb.append(ConsoleTheme.keyGuide(footerHints)).append("\n");

        return sb.toString();
    }

    private static List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("N/A");
            return lines;
        }
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() + word.length() + (currentLine.isEmpty() ? 0 : 1) > maxWidth) {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                }
                while (word.length() > maxWidth) {
                    lines.add(word.substring(0, maxWidth));
                    word = word.substring(maxWidth);
                }
            }
            if (!currentLine.isEmpty()) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }
}
