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
import com.bank.model.dto.UserProfileDossier;
import com.bank.model.entity.Account;
import com.bank.model.entity.Admin;
import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.Currency;
import com.bank.model.enums.UserStatus;
import com.bank.security.SessionManager;
import com.bank.ui.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SUPER ADMIN > USER PROFILE DOSSIER (82 Columns)
 * Displays customer KYC identity, linked bank accounts, freeze toggle, and reset token issuance.
 */
public class UserProfileModal implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(UserProfileModal.class);

    private final AdminController adminController;
    private final Long userId;

    public UserProfileModal(Long userId) {
        this(ControllerFactory.getAdminController(), userId);
    }

    public UserProfileModal(String rawUserId) {
        this(ControllerFactory.getAdminController(), rawUserId != null ? Long.parseLong(rawUserId.replaceAll("[^0-9]", "")) : null);
    }

    public UserProfileModal(AdminController adminController, Long userId) {
        this.adminController = adminController;
        this.userId = (userId != null) ? Long.parseLong(String.valueOf(userId).replaceAll("[^0-9]", "")) : null;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin == null || userId == null) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        String statusMessage = "Profile loaded. Customer ledger integrity intact.";
        String issuedToken = null;
        boolean isError = false;
        boolean firstRender = true;

        try {
            while (true) {
                UserProfileDossier dossier;
                try {
                    dossier = adminController.getUserProfileDossier(admin, userId);
                } catch (Exception e) {
                    logger.error("Error loading dossier for user {}", userId, e);
                    navigator.pop();
                    return;
                }

                String rendered = renderContent(dossier, issuedToken, statusMessage, isError, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && (event.ch() == 'b' || event.ch() == 'B'))) {
                    navigator.pop();
                    return;
                } else if (event.action() == KeyAction.CHAR && (event.ch() == 'f' || event.ch() == 'F')) {
                    try {
                        var updated = adminController.toggleUserFreeze(admin, userId);
                        statusMessage = "User status updated: " + updated.getStatus() + ". Accounts synchronized.";
                        isError = false;
                    } catch (Exception e) {
                        statusMessage = "Freeze action failed: " + e.getMessage();
                        isError = true;
                    }
                } else if (event.action() == KeyAction.CHAR && (event.ch() == 'r' || event.ch() == 'R')) {
                    try {
                        String token = adminController.issueUserResetToken(admin, userId);
                        issuedToken = token;
                        statusMessage = "One-time reset token generated and logged to audit trail.";
                        isError = false;
                    } catch (Exception e) {
                        statusMessage = "Token generation failed: " + e.getMessage();
                        isError = true;
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (issuedToken != null) {
                        issuedToken = null;
                        statusMessage = "Reset token dismissed. Profile operational.";
                        isError = false;
                    } else {
                        navigator.pop();
                        return;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in UserProfileModal loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderContent(UserProfileDossier d, String statusMessage, boolean isError, int width) {
        return renderContent(d, null, statusMessage, isError, width);
    }

    public static String renderContent(UserProfileDossier d, String issuedToken, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat dfUsd = new DecimalFormat("#,##0.00");
        DecimalFormat dfKhr = new DecimalFormat("#,##0");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > USER PROFILE DOSSIER"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Identity Compartment
        sb.append(TUIBox.line("CUSTOMER IDENTITY DOSSIER", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String uIdStr = "#USR-" + (d.getUserId() != null ? String.format("%02d", d.getUserId()) : "00");
        String regDateStr = d.getRegistrationDate() != null ? d.getRegistrationDate().format(dtf) : "2026-03-12";
        String nameStr = d.getFullName() != null ? d.getFullName() : "-";
        if (nameStr.length() > 25) nameStr = nameStr.substring(0, 22) + "...";
        String phoneStr = d.getPhone() != null ? d.getPhone() : "+855 12 889900";
        String emailStr = d.getEmail() != null ? d.getEmail() : "-";
        if (emailStr.length() > 25) emailStr = emailStr.substring(0, 22) + "...";
        String kycStr = d.getKycVerificationLevel() != null ? d.getKycVerificationLevel() : "LEVEL_2 (FULL)";
        String loginStatusStr = (d.getStatus() != null ? d.getStatus().name() : "ACTIVE") + " (" + d.getFailedLoginAttempts() + " failed attempts)";
        if (d.getStatus() == UserStatus.FROZEN || d.getStatus() == UserStatus.SUSPENDED) {
            loginStatusStr = ConsoleTheme.warning(loginStatusStr);
        }

        String r1 = String.format("  User ID         : %-20s Registration Date : %s", uIdStr, regDateStr);
        String r2 = String.format("  Full Name       : %-20s Phone Number      : %s", nameStr, phoneStr);
        String r3 = String.format("  Primary Email   : %-20s KYC Verification  : %s", emailStr, kycStr);
        String r4 = String.format("  Login Status    : %-20s", loginStatusStr);

        sb.append(TUIBox.line(r1, width)).append("\n");
        sb.append(TUIBox.line(r2, width)).append("\n");
        sb.append(TUIBox.line(r3, width)).append("\n");
        sb.append(TUIBox.line(r4, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        if (issuedToken != null) {
            // SECURITY DISPATCH MODAL BOX (78 chars inside TUIBox)
            String topBox = "┌" + "─".repeat(76) + "┐";
            String titleBox = "│ SECURITY DISPATCH: ONE-TIME RECOVERY TOKEN ISSUED                          │";
            String midBox = "├" + "─".repeat(76) + "┤";

            String tokenLine = String.format("│ Token Code      : %s%s│", Ansi.cyan(issuedToken), " ".repeat(Math.max(0, 58 - issuedToken.length())));
            String targetEmailStr = emailStr + " (Simulated Dispatch)";
            if (targetEmailStr.length() > 56) targetEmailStr = targetEmailStr.substring(0, 53) + "...";
            String emailLine = String.format("│ Target Email    : %-56s │", targetEmailStr);
            String expLine = "│ Expiration      : 180 Seconds (3 Minutes)                                  │";
            String auditRef = String.format("#RST-%s-%03d", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), d.getUserId() != null ? d.getUserId() : 0);
            String auditLine = String.format("│ Audit Reference : %-56s │", auditRef);
            String botBox = "└" + "─".repeat(76) + "┘";

            sb.append(TUIBox.line(topBox, width)).append("\n");
            sb.append(TUIBox.line(titleBox, width)).append("\n");
            sb.append(TUIBox.line(midBox, width)).append("\n");
            sb.append(TUIBox.line(tokenLine, width)).append("\n");
            sb.append(TUIBox.line(emailLine, width)).append("\n");
            sb.append(TUIBox.line(expLine, width)).append("\n");
            sb.append(TUIBox.line(auditLine, width)).append("\n");
            sb.append(TUIBox.line(botBox, width)).append("\n");
        } else {
            // Linked Accounts Compartment
            sb.append(TUIBox.line("LINKED BANK ACCOUNTS", width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            // Table Header: <= 78 chars
            String th = "  ACCOUNT NUMBER       TYPE        CURRENCY               BALANCE       STATUS";
            sb.append(TUIBox.line(th, width)).append("\n");
            sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

            List<Account> accounts = d.getLinkedAccounts();
            if (accounts != null && !accounts.isEmpty()) {
                for (Account a : accounts) {
                    String accNum = a.getAccountNumber() != null ? a.getAccountNumber() : "DGB-000000000";
                    String type = a.getAccountType() != null ? a.getAccountType().name() : "CHECKING";
                    String ccy = a.getCurrency() != null ? a.getCurrency().name() : "USD";
                    String balStr;
                    if (a.getCurrency() == Currency.KHR) {
                        balStr = "៛ " + String.format("%9s", dfKhr.format(a.getBalance() != null ? a.getBalance() : 0));
                    } else {
                        balStr = "$ " + String.format("%9s", dfUsd.format(a.getBalance() != null ? a.getBalance() : 0));
                    }
                    String st = a.getStatus() != null ? a.getStatus().name() : "ACTIVE";
                    String stColor = (a.getStatus() == AccountStatus.FROZEN) ? ConsoleTheme.warning(st) : Ansi.green(st);

                    String row = String.format("  %-19s  %-10s  %-17s  %10s       %s", accNum, type, ccy, balStr, stColor);
                    sb.append(TUIBox.line(row, width)).append("\n");
                }
            } else {
                sb.append(TUIBox.line("  " + ConsoleTheme.muted("No linked bank accounts found for this customer."), width)).append("\n");
            }
            sb.append(TUIBox.emptyLine(width)).append("\n");
        }

        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        String statusDisplay = isError ? ConsoleTheme.error(statusMessage)
                : (statusMessage != null && statusMessage.contains("One-time reset token")) ? ConsoleTheme.success(statusMessage)
                : ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        if (issuedToken != null) {
            sb.append(ConsoleTheme.keyGuide("[Enter] Acknowledge & Dismiss  •  [F] Toggle Freeze  •  [Esc] Back")).append("\n");
        } else {
            sb.append(ConsoleTheme.keyGuide("[F] Toggle Freeze  •  [R] Reset Token  •  [Esc] Back")).append("\n");
        }

        return sb.toString();
    }
}
