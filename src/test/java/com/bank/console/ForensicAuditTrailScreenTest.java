package com.bank.console;

import com.bank.console.screens.ForensicAuditTrailScreen;
import com.bank.console.screens.Screen;
import com.bank.model.entity.Admin;
import com.bank.model.entity.AuditLog;
import com.bank.model.enums.AdminRole;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForensicAuditTrailScreenTest {

    private ScreenNavigator navigator;
    private TUISession session;
    private Terminal terminal;
    private Attributes attributes;
    private Admin superAdmin;

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
        ControllerFactory.init();
        navigator = new ScreenNavigator();
        session = mock(TUISession.class);
        terminal = mock(Terminal.class);
        attributes = mock(Attributes.class);

        when(session.getTerminal()).thenReturn(terminal);
        when(terminal.enterRawMode()).thenReturn(attributes);

        superAdmin = Admin.builder()
                .adminId(1L)
                .username("superadmin")
                .role(AdminRole.SUPER_ADMIN)
                .build();
        SessionManager.loginAdmin(superAdmin);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("1. Drain Buffer On Entry: Drains lingering \\r and \\n characters from input stream")
    void testDrainBufferOnEntry() throws IOException {
        NonBlockingReader reader = createReader("\r\n\r\n\033");
        ForensicAuditTrailScreen.drainBufferOnEntry(reader);

        // Next character should now be ESC (\033), not \r or \n
        int nextChar = reader.read();
        assertEquals(27, nextChar, "Leading newlines must be flushed, yielding ESC next");
    }

    @Test
    @DisplayName("2. Persistent Loop: Ignores unknown keys without premature exit, navigates, and exits only on [Esc]")
    void testPersistentLoopIgnoresUnknownKeysAndExitsOnEsc() {
        BaseScreen base = new BaseScreen();
        navigator.push(base);

        ForensicAuditTrailScreen screen = new ForensicAuditTrailScreen();
        navigator.push(screen);

        // Feed:
        // 'Z' (unknown key) -> ignored by default case
        // '?' (unknown key) -> ignored by default case
        // 'k' (Up navigation)
        // 'j' (Down navigation)
        // '1', '2', '3', '4' (period filters)
        // '\033' (ESC) -> exits loop cleanly
        String keystrokes = "Z?kj1234\033";
        NonBlockingReader reader = createReader(keystrokes);
        when(terminal.reader()).thenReturn(reader);

        assertDoesNotThrow(() -> screen.render(navigator, session));

        // Screen popped cleanly back to base screen only after ESC
        assertEquals(base, navigator.getCurrentScreen(), "Screen must return to base screen after ESC");
        verify(terminal).setAttributes(attributes);
    }

    @Test
    @DisplayName("3. Persistent Loop: Exits cleanly when user presses 'B' or 'b'")
    void testPersistentLoopExitsOnB() {
        BaseScreen base = new BaseScreen();
        navigator.push(base);

        ForensicAuditTrailScreen screen = new ForensicAuditTrailScreen();
        navigator.push(screen);

        NonBlockingReader reader = createReader("b");
        when(terminal.reader()).thenReturn(reader);

        screen.render(navigator, session);

        assertEquals(base, navigator.getCurrentScreen(), "Screen must return to base screen after pressing 'b'");
    }

    @Test
    @DisplayName("4. Raw JSON Signature Modal: Renders and dismisses on Enter without closing the screen")
    void testRawJsonSignatureViewerModal() throws IOException {
        AuditLog sampleLog = AuditLog.builder()
                .logId(99L)
                .adminId(1L)
                .action("SECURITY_ALERT")
                .targetTable("accounts")
                .targetId(101L)
                .details("Suspicious login attempt flagged")
                .createdAt(LocalDateTime.now())
                .build();

        // Dismiss modal with Enter (\n)
        NonBlockingReader modalReader = createReader("\n");
        assertDoesNotThrow(() ->
                ForensicAuditTrailScreen.renderRawJsonSignatureViewerModal(terminal, modalReader, sampleLog, 82)
        );
    }

    @Test
    @DisplayName("5. Unauthenticated Admin: Popped immediately on entry")
    void testUnauthenticatedAdminPopped() {
        SessionManager.logout();

        BaseScreen base = new BaseScreen();
        navigator.push(base);

        ForensicAuditTrailScreen screen = new ForensicAuditTrailScreen();
        navigator.push(screen);

        screen.render(navigator, session);

        assertEquals(base, navigator.getCurrentScreen(), "Unauthenticated admin must be popped immediately");
    }
}
