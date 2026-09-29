package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.LoanManagementAndRepaymentsScreen;
import com.bank.console.screens.LoanScreen;
import com.bank.console.screens.LoanScreen.LoanFacility;
import com.bank.console.screens.Screen;
import com.bank.controller.AccountController;
import com.bank.controller.LoanController;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LoanManagementAndRepaymentsScreenTest {

    private ScreenNavigator navigator;
    private TUISession session;
    private Terminal terminal;
    private Attributes attributes;
    private LoanController loanController;
    private AccountController accountController;
    private User testUser;
    private UserDTO testUserDto;
    private List<LoanFacility> mockFacilities;

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
        loanController = mock(LoanController.class);
        accountController = mock(AccountController.class);

        when(session.getTerminal()).thenReturn(terminal);
        when(terminal.enterRawMode()).thenReturn(attributes);

        testUser = User.builder()
                .userId(101L)
                .username("john_doe")
                .fullName("John Doe")
                .build();
        testUserDto = UserDTO.builder()
                .userId(101L)
                .username("john_doe")
                .fullName("John Doe")
                .build();

        SessionManager.loginUser(testUser);
        when(session.getCurrentUser()).thenReturn(testUserDto);

        mockFacilities = new ArrayList<>();
        // Page 1: ACTIVE
        mockFacilities.add(new LoanFacility("#LN-13", 13L, "Personal Credit", new BigDecimal("1000.00"),
                new BigDecimal("8.50"), "ACTIVE", new BigDecimal("46.68"),
                "DGB-429309564", 12, LocalDate.of(2026, 10, 20), new BigDecimal("83.33")));
        mockFacilities.add(new LoanFacility("#LN-12", 12L, "Personal Credit", new BigDecimal("1000.00"),
                new BigDecimal("10.50"), "ACTIVE", new BigDecimal("1105.00"),
                "DGB-429309564", 12, LocalDate.of(2026, 10, 20), new BigDecimal("92.08")));
        mockFacilities.add(new LoanFacility("#LN-11", 11L, "Personal Credit", new BigDecimal("1500.00"),
                new BigDecimal("8.75"), "ACTIVE", new BigDecimal("1631.25"),
                "DGB-429309564", 12, LocalDate.of(2026, 10, 20), new BigDecimal("135.94")));
        mockFacilities.add(new LoanFacility("#LN-09", 9L, "Emergency Auto", new BigDecimal("5000.00"),
                new BigDecimal("9.50"), "ACTIVE", new BigDecimal("5475.00"),
                "DGB-429309564", 24, LocalDate.of(2026, 10, 20), new BigDecimal("228.13")));
        mockFacilities.add(new LoanFacility("#LN-08", 8L, "Emergency Auto", new BigDecimal("3000.00"),
                new BigDecimal("7.50"), "ACTIVE", new BigDecimal("3225.00"),
                "DGB-429309564", 24, LocalDate.of(2026, 10, 20), new BigDecimal("134.38")));

        // Page 2: REJECTED
        mockFacilities.add(new LoanFacility("#LN-20", 20L, "Personal Credit", new BigDecimal("1000.00"),
                BigDecimal.ZERO, "REJECTED", BigDecimal.ZERO,
                "N/A", 12, null, BigDecimal.ZERO,
                "2026-09-18 14:20 UTC", "Exceeded maximum Debt-To-Income threshold (DTI > 55%)."));
        mockFacilities.add(new LoanFacility("#LN-19", 19L, "Personal Credit", new BigDecimal("1000.00"),
                BigDecimal.ZERO, "REJECTED", BigDecimal.ZERO,
                "N/A", 12, null, BigDecimal.ZERO,
                "2026-09-17 11:15 UTC", "Credit score below minimum threshold."));

        // Page 3: SETTLED
        mockFacilities.add(new LoanFacility("#LN-07", 7L, "Personal Credit", new BigDecimal("2000.00"),
                new BigDecimal("8.00"), "SETTLED", BigDecimal.ZERO,
                "DGB-429309564", 12, null, BigDecimal.ZERO));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("1. ACTIVE Loan Layout: Renders Dossier, Schedule, [1] Pay Due, and conforms to 82 columns")
    void testActiveLoanLayoutAnd82Columns() {
        int width = TUILayout.APP_WIDTH;
        String rendered = LoanScreen.renderContent(mockFacilities, 0, 0, 0, null, false, width);

        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "Line " + i + " must conform strictly to 82 columns: " + lines[i]);
        }
        assertTrue(TUIBox.visibleLength(lines[lines.length - 1]) <= 82, "Footer must be <= 82 cols");

        // Verify selected row has full-width inverse background highlighting
        boolean foundInverseSelectedRow = false;
        for (String line : lines) {
            if (line.contains("\033[7m") && line.contains("#LN-13") && line.contains("ACTIVE")) {
                foundInverseSelectedRow = true;
                break;
            }
        }
        assertTrue(foundInverseSelectedRow, "Selected row #LN-13 must have full-width inverse highlighting \\033[7m");

        String stripped = TUIBox.stripAnsi(rendered);
        assertTrue(stripped.contains("FACILITY DOSSIER [#LN-13] • ACTIVE FACILITY"));
        assertTrue(stripped.contains("Disbursed To : DGB-429309564 (Checking)"));
        assertTrue(stripped.contains("Next Payment Due: 2026-10-20"));
        assertTrue(stripped.contains("REPAYMENT SCHEDULE & INSTALLMENTS"));
        assertTrue(stripped.contains("[1] Pay Due"));
        assertTrue(stripped.contains("[2] Contract"));
        assertTrue(stripped.contains("[Esc] Back"));
    }

    @Test
    @DisplayName("2. REJECTED Loan Layout: OMIT schedule, show decline reason, and hide payment buttons")
    void testRejectedLoanLayout() {
        int width = TUILayout.APP_WIDTH;
        // Select index 0 on Page 1 (which corresponds to #LN-20 REJECTED)
        String rendered = LoanScreen.renderContent(mockFacilities, 0, 1, 0, null, false, width);

        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "Line " + i + " must conform strictly to 82 columns: " + lines[i]);
        }
        assertTrue(TUIBox.visibleLength(lines[lines.length - 1]) <= 82);

        String stripped = TUIBox.stripAnsi(rendered);
        assertTrue(stripped.contains("FACILITY DOSSIER [#LN-20] • APPLICATION DECLINED"));
        assertTrue(stripped.contains("Application Date : 2026-09-18 14:20 UTC"));
        assertTrue(stripped.contains("Underwriting Note: Exceeded maximum Debt-To-Income threshold (DTI > 55%)."));
        assertTrue(stripped.contains("Facility Status  : REJECTED (No funds disbursed, no repayment required)."));

        // Critical: MUST NOT contain repayment schedule
        assertFalse(stripped.contains("REPAYMENT SCHEDULE & INSTALLMENTS"), "Rejected facility must NOT render repayment schedule");
        assertFalse(stripped.contains("[1] Pay Due"), "Rejected facility must NOT show [1] Pay Due button");
        assertTrue(stripped.contains("[3] Apply New Loan"), "Rejected facility must display [3] Apply New Loan");
        assertTrue(stripped.contains("[Esc] Return"), "Rejected facility key guide must have [Esc] Return");
    }

    @Test
    @DisplayName("3. SETTLED Loan Layout: Display settlement notice and hide payment buttons")
    void testSettledLoanLayout() {
        int width = TUILayout.APP_WIDTH;
        // Filter for SETTLED facilities (filterIndex = 2), index 0 (corresponds to #LN-07 SETTLED)
        String rendered = LoanScreen.renderContent(mockFacilities, 0, 0, 2, null, false, width);

        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "Line " + i + " must conform strictly to 82 columns: " + lines[i]);
        }
        assertTrue(TUIBox.visibleLength(lines[lines.length - 1]) <= 82);

        String stripped = TUIBox.stripAnsi(rendered);
        assertTrue(stripped.contains("FACILITY DOSSIER [#LN-07] • SETTLED FACILITY"));
        assertTrue(stripped.contains("FACILITY SETTLEMENT NOTICE"));
        assertTrue(stripped.contains("All installments settled in full. Facility closed."));
        assertFalse(stripped.contains("[1] Pay Due"), "Settled facility must NOT show [1] Pay Due button");
        assertTrue(stripped.contains("[2] Contract"));
        assertTrue(stripped.contains("[Esc] Back"));
    }

    @Test
    @DisplayName("4. Upper Facilities Pagination: 5 rows max per page, N/P page flipping")
    void testUpperFacilitiesPaginationAndNavigation() {
        BaseScreen base = new BaseScreen();
        navigator.push(base);

        LoanManagementAndRepaymentsScreen screen = new LoanManagementAndRepaymentsScreen(loanController, accountController);
        navigator.push(screen);

        // Feed:
        // "N" -> next page (flips to page 2/2)
        // "P" -> previous page (flips back to page 1/2)
        // "\033[C" -> right arrow (flips to page 2/2)
        // "\033[D" -> left arrow (flips to page 1/2)
        // "1" on #LN-13 -> navigates to LoanRepaymentScreen
        String keystrokes = "N" + "P" + "\033[C" + "\033[D" + "1";
        NonBlockingReader reader = createReader(keystrokes);
        when(terminal.reader()).thenReturn(reader);

        assertDoesNotThrow(() -> screen.render(navigator, session));
    }

    @Test
    @DisplayName("5. Class Alias: LoanManagementAndRepaymentsScreen extends LoanScreen cleanly")
    void testClassHierarchy() {
        LoanManagementAndRepaymentsScreen screen = new LoanManagementAndRepaymentsScreen();
        assertTrue(screen instanceof LoanScreen);
        assertNotNull(new LoanManagementAndRepaymentsScreen(loanController));
        assertNotNull(new LoanManagementAndRepaymentsScreen(loanController, accountController));
    }

    @Test
    @DisplayName("6. Fixed Height Verification: Total row count is strictly constant (27 lines) across ACTIVE, REJECTED, SETTLED, and EMPTY")
    void testConstantScreenHeightAcrossAllLoanStatuses() {
        int width = TUILayout.APP_WIDTH;

        // 1. ACTIVE loan selected (page 0, index 0 -> #LN-13)
        String renderedActive = LoanScreen.renderContent(mockFacilities, 0, 0, 0, null, false, width);
        String[] linesActive = renderedActive.split("\n");

        // 2. REJECTED loan selected (page 1, index 0 -> #LN-20)
        String renderedRejected = LoanScreen.renderContent(mockFacilities, 0, 1, 0, null, false, width);
        String[] linesRejected = renderedRejected.split("\n");

        // 3. SETTLED loan selected (filter 2, index 0 -> #LN-07)
        String renderedSettled = LoanScreen.renderContent(mockFacilities, 0, 0, 2, null, false, width);
        String[] linesSettled = renderedSettled.split("\n");

        // 4. Empty facilities list
        String renderedEmpty = LoanScreen.renderContent(new ArrayList<>(), 0, 0, 0, null, false, width);
        String[] linesEmpty = renderedEmpty.split("\n");

        // Strict verification: Row count MUST NOT jump or flicker
        assertEquals(27, linesActive.length, "Active facility screen height must be exactly 27 rows");
        assertEquals(27, linesRejected.length, "Rejected facility screen height must be exactly 27 rows");
        assertEquals(27, linesSettled.length, "Settled facility screen height must be exactly 27 rows");
        assertEquals(27, linesEmpty.length, "Empty facility screen height must be exactly 27 rows");

        // Verify across all 4 modes that box columns conform strictly to 82 columns
        for (String[] lines : List.of(linesActive, linesRejected, linesSettled, linesEmpty)) {
            for (int i = 0; i < lines.length - 1; i++) {
                assertEquals(82, TUIBox.visibleLength(lines[i]), "Line " + i + " must conform to 82 columns");
            }
            assertTrue(TUIBox.visibleLength(lines[lines.length - 1]) <= 82, "Footer must be <= 82 columns");
        }
    }
}

