package com.bank.console;

import com.bank.console.theme.ConsoleTheme;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.AdminDTO;
import com.bank.model.dto.UserDTO;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.bank.model.dto.AuthenticatedUser;

import java.io.IOException;

/**
 * Manages the lifecycle of JLine terminal hardware resources and
 * holds current user/admin authenticated session state.
 */
public class TUISession {
    private static TUISession instance;
    private final Terminal terminal;
    private final LineReader lineReader;

    // Authenticated Session State
    private UserDTO currentUser;
    private AdminDTO currentAdmin;
    private AuthenticatedUser currentAuthenticatedUser;
    private AccountDTO currentAccount;

    private TUISession() {
        try {
            this.terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            this.lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize TUI session", e);
        }
    }

    public static synchronized TUISession getInstance() {
        if (instance == null) {
            instance = new TUISession();
        }
        return instance;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public LineReader getLineReader() {
        return lineReader;
    }

    // Session State Management
    public UserDTO getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(UserDTO currentUser) {
        this.currentUser = currentUser;
        this.currentAdmin = null; // Mutually exclusive
        this.currentAuthenticatedUser = currentUser != null ? AuthenticatedUser.fromCustomer(currentUser) : null;
    }

    public AdminDTO getCurrentAdmin() {
        return currentAdmin;
    }

    public void setCurrentAdmin(AdminDTO currentAdmin) {
        this.currentAdmin = currentAdmin;
        this.currentUser = null; // Mutually exclusive
        this.currentAuthenticatedUser = currentAdmin != null ? AuthenticatedUser.fromAdmin(currentAdmin) : null;
    }

    public AuthenticatedUser getCurrentAuthenticatedUser() {
        return currentAuthenticatedUser;
    }

    public void setCurrentAuthenticatedUser(AuthenticatedUser authUser) {
        this.currentAuthenticatedUser = authUser;
        if (authUser == null) {
            this.currentUser = null;
            this.currentAdmin = null;
        } else if (authUser.isCustomer()) {
            this.currentUser = authUser.getUserDTO();
            this.currentAdmin = null;
        } else {
            this.currentAdmin = authUser.getAdminDTO();
            this.currentUser = null;
        }
    }

    public AccountDTO getCurrentAccount() {
        return currentAccount;
    }

    public void setCurrentAccount(AccountDTO currentAccount) {
        this.currentAccount = currentAccount;
    }

    public boolean isUserLoggedIn() {
        return currentUser != null;
    }

    public boolean isAdminLoggedIn() {
        return currentAdmin != null;
    }

    public String getAuthenticatedName() {
        if (currentUser != null) return currentUser.getFullName();
        if (currentAdmin != null) return currentAdmin.getFullName() + " (" + currentAdmin.getRole() + ")";
        return "Guest";
    }

    public int getTerminalWidth() {
        if (terminal != null) {
            int width = terminal.getWidth();
            if (width >= 80) {
                return Math.min(width, 120);
            }
        }
        return 84;
    }

    public void logout() {
        this.currentUser = null;
        this.currentAdmin = null;
        this.currentAuthenticatedUser = null;
        this.currentAccount = null;
    }

    public void clearScreen() {
        System.out.print(ConsoleTheme.CLEAR_SCREEN);
        System.out.flush();
    }

    public void repositionCursorHome() {
        System.out.print("\u001B[H");
        System.out.flush();
    }

    public void close() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                System.err.println("Failed to close terminal session: " + e.getMessage());
            }
        }
    }
}