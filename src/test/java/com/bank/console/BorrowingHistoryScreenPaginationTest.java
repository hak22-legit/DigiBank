package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.BorrowingHistoryScreen;
import com.bank.console.screens.Screen;
import com.bank.controller.AdminController;
import com.bank.controller.LoanController;
import com.bank.model.entity.Admin;
import com.bank.model.entity.Loan;
import com.bank.model.enums.AdminRole;
import com.bank.model.enums.LoanStatus;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BorrowingHistoryScreenPaginationTest {

    private ScreenNavigator navigator;
    private TUISession session;
    private Terminal terminal;
    private Attributes attributes;
    private AdminController adminController;
    private LoanController loanController;
    private Admin loanOfficer;
    private List<Loan> mockLoans;

    private static class BaseScreen implements Screen {
        @Override
        public void render(ScreenNavigator nav, TUISession sess) {}
    }

    private static NonBlockingReader createReader(String input) {
        return new NonBlockingReader() {
            private int pos = 0;

            @Override
            public int read(long timeout, boolean isPeek) {
                if (pos >= input.length()) return -1;
                if (timeout > 0) {
                    char next = input.charAt(pos);
                    if (next != '[' && next != 'O') {
                        return -2;
                    }
                }
                int ch = input.charAt(pos);
                if (!isPeek) pos++;
                return ch;
            }

            @Override
            public boolean ready() {
                return pos < input.length();
            }

            @Override
            public int readBuffered(char[] b, int off, int len, long timeout) {
                return -1;
            }

            @Override
            public void close() {}
        };
    }

    @BeforeEach
    void setUp() {
        navigator = new ScreenNavigator();
        session = mock(TUISession.class);
        terminal = mock(Terminal.class);
        attributes = mock(Attributes.class);
        adminController = mock(AdminController.class);
        loanController = mock(LoanController.class);

        when(session.getTerminal()).thenReturn(terminal);
        when(terminal.enterRawMode()).thenReturn(attributes);

        loanOfficer = Admin.builder()
                .adminId(2L)
                .username("officer1")
                .role(AdminRole.LOAN_OFFICER)
                .build();
        SessionManager.loginAdmin(loanOfficer);

        // Generate 30 mock loans (5 pages @ 6 loans/page)
        mockLoans = new ArrayList<>();
        for (long i = 1; i <= 30; i++) {
            mockLoans.add(Loan.builder()
                    .loanId(1000L + i)
                    .userId(103L)
                    .requestedAmount(new BigDecimal("5000.00"))
                    .approvedAmount(new BigDecimal("5000.00"))
                    .outstandingBalance(new BigDecimal("2500.00"))
                    .termMonths(12)
                    .interestRate(new BigDecimal("5.50"))
                    .status(LoanStatus.ACTIVE)
                    .build());
        }
        when(loanController.getCustomerBorrowingHistory(loanOfficer, 103L)).thenReturn(mockLoans);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("Verify Footer Guide conforms to 82 columns and includes all expected shortcuts")
    void testFooterGuideStructureAndContainment() {
        String rendered = BorrowingHistoryScreen.renderContent(
                "103", 103L, null, mockLoans, 0, 0, 1, "Ready", false, TUILayout.APP_WIDTH
        );

        String[] lines = rendered.split("\n");
        String footerLine = lines[lines.length - 1];

        // Ensure <= 82 columns
        int visibleLen = TUIBox.visibleLength(footerLine);
        assertTrue(visibleLen <= 82, "Footer line visible length must be <= 82 cols but was: " + visibleLen);

        String stripped = TUIBox.stripAnsi(footerLine);
        assertTrue(stripped.contains("[↑/↓] Select"), "Footer must contain [↑/↓] Select");
        assertTrue(stripped.contains("[N/P] Page"), "Footer must contain [N/P] Page");
        assertTrue(stripped.contains("[Tab] Filter"), "Footer must contain [Tab] Filter");
        assertTrue(stripped.contains("[Digits] Search"), "Footer must contain [Digits] Search");
        assertTrue(stripped.contains("[Esc] Back"), "Footer must contain [Esc] Back");
    }

    @Test
    @DisplayName("Verify N/P and Arrow keys navigate pages without getting stuck on Page 1/5")
    void testPageNavigationKeybindings() {
        BaseScreen base = new BaseScreen();
        navigator.push(base);

        BorrowingHistoryScreen screen = new BorrowingHistoryScreen(adminController, loanController);
        navigator.push(screen);

        // Keystrokes:
        // "103\n" -> search user #103 (loads 30 loans = 5 pages)
        // "n"     -> flip to page 2
        // "\033[C"-> Right Arrow: flip to page 3
        // "p"     -> flip to page 2
        // "\033[D"-> Left Arrow: flip to page 1
        // "\033"  -> ESC to clear idBuffer
        // "\033"  -> ESC to exit screen
        String keystrokes = "103\n" + "n" + "\033[C" + "p" + "\033[D" + "\033\033";
        NonBlockingReader reader = createReader(keystrokes);
        when(terminal.reader()).thenReturn(reader);

        assertDoesNotThrow(() -> screen.render(navigator, session));

        assertEquals(base, navigator.getCurrentScreen(), "Screen must return to base after ESC");
        verify(terminal).setAttributes(attributes);
    }

    @Test
    @DisplayName("Verify Down/Up arrow keys auto-scroll across page boundaries")
    void testAutoPageScrollWithDownAndUpArrows() {
        BaseScreen base = new BaseScreen();
        navigator.push(base);

        BorrowingHistoryScreen screen = new BorrowingHistoryScreen(adminController, loanController);
        navigator.push(screen);

        // Keystrokes:
        // "103\n" -> loads 5 pages of loans
        // Press Down arrow 6 times:
        //   index 0 -> 1 -> 2 -> 3 -> 4 -> 5 (visiblePageRows - 1)
        //   6th Down arrow -> auto-scrolls to Page 2, selectedIndex = 0!
        // Press Up arrow 1 time:
        //   at index 0 of Page 2 -> auto-scrolls back to Page 1, selectedIndex = 5!
        // ESC twice to exit
        StringBuilder sb = new StringBuilder("103\n");
        for (int i = 0; i < 6; i++) {
            sb.append("\033[B"); // Down arrow
        }
        sb.append("\033[A"); // Up arrow
        sb.append("\033\033"); // Exit

        NonBlockingReader reader = createReader(sb.toString());
        when(terminal.reader()).thenReturn(reader);

        assertDoesNotThrow(() -> screen.render(navigator, session));

        assertEquals(base, navigator.getCurrentScreen(), "Screen must return to base after ESC");
    }
}
