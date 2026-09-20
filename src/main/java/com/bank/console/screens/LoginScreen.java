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
import com.bank.controller.AuthController;
import com.bank.exception.AuthenticationException;
import com.bank.exception.InactiveAccountException;
import com.bank.exception.LockedAccountException;
import com.bank.model.dto.AuthenticatedUser;
import com.bank.model.enums.AdminRole;
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
        int focusIndex = 0; // 0: Username, 1: Password, 2: Action Bar
        int actionIndex = 0; // 0: [1] Sign In, 1: [2] Forgot Password?, 2: [3] Back to Welcome
        boolean firstRender = true;

        boolean running = true;
        try {
            while (running) {
                try {
                    renderForm(session, username.toString(), password.toString(), focusIndex, actionIndex, firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        running = false;
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                        focusIndex = (focusIndex + 1) % 3;
                    } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                        focusIndex = (focusIndex - 1 + 3) % 3;
                    } else if (event.action() == KeyAction.LEFT) {
                        if (focusIndex == 2) {
                            actionIndex = (actionIndex - 1 + 3) % 3;
                        }
                    } else if (event.action() == KeyAction.RIGHT) {
                        if (focusIndex == 2) {
                            actionIndex = (actionIndex + 1) % 3;
                        }
                    } else if (event.action() == KeyAction.BACKSPACE) {
                        if (focusIndex == 0 && username.length() > 0) {
                            username.deleteCharAt(username.length() - 1);
                        } else if (focusIndex == 1 && password.length() > 0) {
                            password.deleteCharAt(password.length() - 1);
                        }
                    } else if (event.action() == KeyAction.ENTER) {
                        if (focusIndex == 0) {
                            focusIndex = 1; // Move to password
                        } else if (focusIndex == 1) {
                            focusIndex = 2; // Move to Action Bar
                            actionIndex = 0;
                        } else if (focusIndex == 2) {
                            if (actionIndex == 0) {
                                // [1] Sign In
                                boolean success = attemptLogin(username.toString(), password.toString(), navigator, session, terminal, origAttributes);
                                if (success) {
                                    return;
                                }
                                firstRender = true;
                            } else if (actionIndex == 1) {
                                // [2] Forgot Password?
                                navigator.push(new ForgotPasswordWizard(authController, username.toString().trim()));
                                return;
                            } else if (actionIndex == 2) {
                                // [3] Back to Welcome
                                navigator.pop();
                                return;
                            }
                        }
                    } else if (event.code() == 6) { // Ctrl+F hotkey from anywhere
                        navigator.push(new ForgotPasswordWizard(authController, username.toString().trim()));
                        return;
                    } else if (event.action() == KeyAction.CHAR || event.action() == KeyAction.DIGIT) {
                        char ch = event.ch();
                        if (focusIndex == 0) {
                            if (username.length() < 46) {
                                username.append(ch);
                            }
                        } else if (focusIndex == 1) {
                            if (password.length() < 46) {
                                password.append(ch);
                            }
                        } else if (focusIndex == 2) {
                            if (ch == '1') {
                                boolean success = attemptLogin(username.toString(), password.toString(), navigator, session, terminal, origAttributes);
                                if (success) return;
                                firstRender = true;
                            } else if (ch == '2' || ch == 'f' || ch == 'F') {
                                navigator.push(new ForgotPasswordWizard(authController, username.toString().trim()));
                                return;
                            } else if (ch == '3' || ch == 'b' || ch == 'B') {
                                navigator.pop();
                                return;
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("LoginScreen error recovery", ex);
                    this.statusMessage = "Status: Action completed or temporarily deferred. Press [Esc] to return.";
                    this.isErrorStatus = true;
                }
            }
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void renderForm(TUISession session, String username, String password, int focusIndex, int actionIndex, boolean firstRender) {
        StringBuilder sb = new StringBuilder();
        int width = TUILayout.APP_WIDTH;

        // Render Screen 2 Box
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SYSTEM ACCESS GATEWAY"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("AUTHENTICATION CREDENTIALS", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Format Username Field
        String userDisplay = (focusIndex == 0) ? (username + "_") : username;
        int userPad = Math.max(0, 48 - userDisplay.length());
        String userVal = userDisplay + " ".repeat(userPad);
        String userLabel = (focusIndex == 0) ? ConsoleTheme.bold("Username / Email  : [ ") : "Username / Email  : [ ";
        String userField = "  " + userLabel + (focusIndex == 0 ? ConsoleTheme.bold(userVal) : userVal) + " ]";
        sb.append(TUIBox.line(userField, width)).append("\n");

        // Format Password Field (masked with •)
        String passMask = "•".repeat(password.length());
        String passDisplay = (focusIndex == 1) ? (passMask + "_") : passMask;
        int passPad = Math.max(0, 48 - passDisplay.length());
        String passVal = passDisplay + " ".repeat(passPad);
        String passLabel = (focusIndex == 1) ? ConsoleTheme.bold("Password          : [ ") : "Password          : [ ";
        String passField = "  " + passLabel + (focusIndex == 1 ? ConsoleTheme.bold(passVal) : passVal) + " ]";
        sb.append(TUIBox.line(passField, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");

        // ACTION Compartment
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("ACTION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String btn1 = "[1] Sign In";
        String btn2 = "[2] Forgot Password?";
        String btn3 = "[3] Back to Welcome";

        String actionLine;
        if (focusIndex == 2 && actionIndex == 1) {
            actionLine = "    " + btn1 + "          ▸ " + ConsoleTheme.highlight(btn2) + "          " + btn3;
        } else if (focusIndex == 2 && actionIndex == 2) {
            actionLine = "    " + btn1 + "          " + btn2 + "        ▸ " + ConsoleTheme.highlight(btn3);
        } else if (focusIndex == 2) {
            actionLine = "  ▸ " + ConsoleTheme.highlight(btn1) + "          " + btn2 + "          " + btn3;
        } else {
            actionLine = "  ▸ " + btn1 + "          " + btn2 + "          " + btn3;
        }
        sb.append(TUIBox.line(actionLine, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Status Bar inside box
        sb.append(TUIBox.divider(width)).append("\n");
        String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : statusMessage;
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer hint
        sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [←/→] Select Action  •  [Enter] Confirm  •  [F] Forgot Pwd  •  [Esc] Back")).append("\n");

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
            } else if (authUser.isAdmin() || (authUser.getAdminDTO() != null && authUser.getAdminDTO().getRole() == AdminRole.SUPER_ADMIN)) {
                navigator.clearAndPush(new SuperAdminDashboardScreen());
            } else {
                navigator.clearAndPush(new StaffDashboardScreen());
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