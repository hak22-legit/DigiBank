package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AuthController;
import com.bank.exception.AuthenticationException;
import com.bank.exception.InactiveAccountException;
import com.bank.exception.LockedAccountException;
import com.bank.model.dto.AuthenticatedUser;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * SCREEN 2: UNIVERSAL LOGIN GATEWAY (USERS & ADMINS) (82 Columns)
 * Interactive in-box form with focus management (Tab / Arrow keys) and zero dual-prompts.
 */
public class LoginScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(LoginScreen.class);

    private final AuthController authController;
    private String statusMessage;
    private boolean isErrorStatus;

    public LoginScreen() {
        this(ControllerFactory.getAuthController());
    }

    public LoginScreen(AuthController authController) {
        this.authController = authController;
        this.statusMessage = "Awaiting credentials...";
        this.isErrorStatus = false;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        StringBuilder username = new StringBuilder();
        StringBuilder password = new StringBuilder();
        int focusIndex = 0; // 0: Username, 1: Password, 2: [SIGN IN], 3: [BACK TO WELCOME]
        boolean firstRender = true;

        try {
            while (true) {
                renderForm(session, username.toString(), password.toString(), focusIndex, firstRender);
                firstRender = false;

                int ch = reader.read();

                if (ch == 27) { // Escape sequence or bare ESC
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        // Bare ESC -> Return to Welcome
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A') { // Up Arrow
                            focusIndex = (focusIndex - 1 + 4) % 4;
                        } else if (code == 'B') { // Down Arrow
                            focusIndex = (focusIndex + 1) % 4;
                        } else if (code == 'Z') { // Shift+Tab
                            focusIndex = (focusIndex - 1 + 4) % 4;
                        } else if (code == 'P') { // F1
                            terminal.setAttributes(origAttributes);
                            navigator.push(new ForgotPasswordScreen(authController));
                            return;
                        }
                    }
                } else if (ch == '\t') { // Tab key -> advance focus
                    focusIndex = (focusIndex + 1) % 4;
                } else if (ch == '\r' || ch == '\n') { // Enter key
                    if (focusIndex == 0) {
                        focusIndex = 1; // Move to password
                    } else if (focusIndex == 1) {
                        focusIndex = 2; // Move to [SIGN IN]
                    } else if (focusIndex == 2) {
                        // Execute Sign In
                        boolean success = attemptLogin(username.toString(), password.toString(), navigator, session, terminal, origAttributes);
                        if (success) {
                            return;
                        }
                        firstRender = true;
                    } else if (focusIndex == 3) {
                        // Back to Welcome
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                } else if (ch == 8 || ch == 127) { // Backspace
                    if (focusIndex == 0 && username.length() > 0) {
                        username.deleteCharAt(username.length() - 1);
                    } else if (focusIndex == 1 && password.length() > 0) {
                        password.deleteCharAt(password.length() - 1);
                    }
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                } else if (ch >= 32 && ch <= 126) { // Printable characters
                    if (focusIndex == 0) {
                        if (username.length() < 46) {
                            username.append((char) ch);
                        }
                    } else if (focusIndex == 1) {
                        if (password.length() < 46) {
                            password.append((char) ch);
                        }
                    } else if (focusIndex == 2 || focusIndex == 3) {
                        if (ch == 's' || ch == 'S') {
                            focusIndex = 2;
                        } else if (ch == 'b' || ch == 'B') {
                            focusIndex = 3;
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading raw keyboard input", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void renderForm(TUISession session, String username, String password, int focusIndex, boolean firstRender) {
        StringBuilder sb = new StringBuilder();
        int width = TUILayout.APP_WIDTH;

        // Render Screen 2 Box
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SYSTEM ACCESS GATEWAY"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line("  Please provide your credentials to authenticate:", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Format Username Field
        String userDisplay = (focusIndex == 0) ? (username + "_") : username;
        int userPad = Math.max(0, 48 - userDisplay.length());
        String userVal = userDisplay + " ".repeat(userPad);
        String userLabel = (focusIndex == 0) ? ConsoleTheme.bold("Username / Email  : [ ") : "Username / Email  : [ ";
        String userField = "  " + userLabel + (focusIndex == 0 ? ConsoleTheme.bold(userVal) : userVal) + " ]";
        sb.append(TUIBox.line(userField, width)).append("\n");

        // Format Password Field
        String passMask = "*".repeat(password.length());
        String passDisplay = (focusIndex == 1) ? (passMask + "_") : passMask;
        int passPad = Math.max(0, 48 - passDisplay.length());
        String passVal = passDisplay + " ".repeat(passPad);
        String passLabel = (focusIndex == 1) ? ConsoleTheme.bold("Password          : [ ") : "Password          : [ ";
        String passField = "  " + passLabel + (focusIndex == 1 ? ConsoleTheme.bold(passVal) : passVal) + " ]";
        sb.append(TUIBox.line(passField, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Format Buttons
        String btnSignIn = (focusIndex == 2) ? ("► " + ConsoleTheme.highlight("[SIGN IN]")) : ("  " + ConsoleTheme.bold("[SIGN IN]"));
        String btnBack = (focusIndex == 3) ? ("► " + ConsoleTheme.highlight("[BACK TO WELCOME]")) : ("  " + ConsoleTheme.muted("[BACK TO WELCOME]"));
        String btnLine = "  " + btnSignIn + "                              " + btnBack;
        sb.append(TUIBox.line(btnLine, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.muted("Use [Tab] or [↑/↓] to switch fields. [Enter] submit. [F1] Password Recovery."), width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.muted("The system automatically detects Customer vs Admin (Staff) roles."), width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : statusMessage;
        sb.append(" Status: ").append(statusDisplay).append(" ".repeat(Math.max(0, width - 10 - TUIBox.stripAnsi(statusDisplay).length()))).append("\n");
        sb.append(TUIBox.rule(width)).append("\n");

        ScreenRenderer.render(sb.toString(), firstRender);
    }

    private boolean attemptLogin(String username, String password, ScreenNavigator navigator,
                                 TUISession session, Terminal terminal, Attributes origAttributes) {
        String identifier = username.trim();
        if (identifier.isEmpty()) {
            this.statusMessage = "Username cannot be empty.";
            this.isErrorStatus = true;
            return false;
        }

        if (password.isEmpty()) {
            this.statusMessage = "Password cannot be empty.";
            this.isErrorStatus = true;
            return false;
        }

        try {
            AuthenticatedUser authUser = authController.login(identifier, password);

            terminal.setAttributes(origAttributes);

            if (authUser.isCustomer()) {
                session.setCurrentUser(authUser.getUserDTO());
            } else {
                session.setCurrentAdmin(authUser.getAdminDTO());
            }

            int width = TUILayout.APP_WIDTH;
            StringBuilder authSb = new StringBuilder();
            authSb.append(TUIBox.top(width)).append("\n");
            authSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > AUTHENTICATION APPROVED"), width)).append("\n");
            authSb.append(TUIBox.divider(width)).append("\n");
            authSb.append(TUIBox.emptyLine(width)).append("\n");
            authSb.append(TUIBox.center("Welcome, " + ConsoleTheme.highlight(authUser.getFullName()), width)).append("\n");
            authSb.append(TUIBox.emptyLine(width)).append("\n");
            authSb.append(TUIBox.center(ConsoleTheme.info("Role: " + authUser.getRole() +
                    (authUser.getSpecificRole() != null ? " (" + authUser.getSpecificRole() + ")" : "")), width)).append("\n");
            authSb.append(TUIBox.emptyLine(width)).append("\n");
            authSb.append(TUIBox.center(ConsoleTheme.muted("Loading your dashboard..."), width)).append("\n");
            authSb.append(TUIBox.emptyLine(width)).append("\n");
            authSb.append(TUIBox.bottom(width)).append("\n");
            ScreenRenderer.render(authSb.toString(), true);

            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {}

            // Route based on role
            if (authUser.isCustomer()) {
                navigator.clearAndPush(new CustomerDashboardScreen());
            } else {
                navigator.clearAndPush(new AdminDashboardScreen());
            }
            return true;

        } catch (InactiveAccountException e) {
            this.statusMessage = "Sign in denied: Account is inactive.";
            this.isErrorStatus = true;
        } catch (LockedAccountException e) {
            this.statusMessage = "Sign in denied: Account is locked. Contact support.";
            this.isErrorStatus = true;
        } catch (AuthenticationException e) {
            this.statusMessage = "Invalid username or password. Please verify credentials.";
            this.isErrorStatus = true;
        } catch (Exception e) {
            this.statusMessage = "Authentication error: " + e.getMessage();
            this.isErrorStatus = true;
        }
        return false;
    }
}