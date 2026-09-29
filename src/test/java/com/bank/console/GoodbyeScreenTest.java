package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.screens.GoodbyeScreen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GoodbyeScreenTest {

    @Test
    void testAllBannerLines() {
        String[] innerLines = {
            "         ██████╗  ██████╗  ██████╗ ██████╗ ██████╗ ██╗   ██╗███████╗            ",
            "        ██╔════╝ ██╔═══██╗██╔═══██╗██╔══██╗██╔══██╗╚██╗ ██╔╝██╔════╝            ",
            "        ██║  ███╗██║   ██║██║   ██║██║  ██║██████╔╝ ╚████╔╝ █████╗              ",
            "        ██║   ██║██║   ██║██║   ██║██║  ██║██╔══██╗  ╚██╔╝  ██╔══╝              ",
            "        ╚██████╔╝╚██████╔╝╚██████╔╝██████╔╝██████╔╝   ██║   ███████╗            ",
            "         ╚═════╝  ╚═════╝  ╚═════╝ ╚═════╝ ╚═════╝    ╚═╝   ╚══════╝            "
        };

        for (int i = 0; i < innerLines.length; i++) {
            assertEquals(80, innerLines[i].length(), "Banner line " + i + " must be exactly 80 chars");
        }
    }

    @Test
    @DisplayName("Verify GoodbyeScreen 82-column layout and box geometry")
    void testGoodbyeScreenLayoutConformsStrictlyTo82Columns() {
        int width = 82;
        String timestamp = "2026-09-25 01:05:12 UTC";
        String rendered = GoodbyeScreen.renderContent(2, timestamp, width);

        String[] lines = rendered.split("\n");
        // Lines 0 to 12 are the box borders and contents; line 13 is the footer hint
        for (int i = 0; i < 13; i++) {
            assertEquals(width, TUIBox.visibleLength(lines[i]),
                    "GoodbyeScreen box line " + i + " must be exactly " + width + " cols: " + lines[i]);
        }
    }

    @Test
    @DisplayName("Verify GoodbyeScreen contains all required header, message, timestamp, countdown, status, and footer elements")
    void testGoodbyeScreenContentElements() {
        int width = 82;
        String timestamp = "2026-09-25 01:05:12 UTC";
        String rendered = GoodbyeScreen.renderContent(2, timestamp, width);

        // 1. Header
        assertTrue(rendered.contains("DIGIBANK CORE > SYSTEM LOGOUT & TERMINATION"),
                "Must include header 'DIGIBANK CORE > SYSTEM LOGOUT & TERMINATION'");

        // 2. Cyan / Bright White centered message
        assertTrue(rendered.contains("\033[1;36mThank you for using DigiBank Core!\033[0m"),
                "Must include message in cyan/bright white (\033[1;36m)");

        // 3. Centered Timestamp
        assertTrue(rendered.contains("Session terminated at: 2026-09-25 01:05:12 UTC"),
                "Must include session termination timestamp");

        // 4. Yellow countdown line
        assertTrue(rendered.contains("\033[33mClosing terminal gateway in 2s...\033[0m"),
                "Must include countdown in yellow (\033[33m)");

        // 5. Status bar
        assertTrue(rendered.contains("Status: Goodbye! Have a great day."),
                "Must include status bar text");

        // 6. Footer hint
        assertTrue(rendered.contains("\033[2;90mConnection closed safely.\033[0m"),
                "Must include dim footer hint");
    }

    @Test
    @DisplayName("Verify GoodbyeScreen countdown dynamic refresh for 1s")
    void testGoodbyeScreenCountdownTransition() {
        int width = 82;
        String timestamp = "2026-09-25 01:05:12 UTC";
        String rendered1s = GoodbyeScreen.renderContent(1, timestamp, width);

        assertTrue(rendered1s.contains("\033[33mClosing terminal gateway in 1s...\033[0m"),
                "Must update countdown text to 1s");
    }

    @Test
    @DisplayName("Verify GoodbyeScreen.show executes smoothly without crashing or unhandled exceptions")
    void testGoodbyeScreenShowExecutionWithoutExit() {
        TUISession mockSession = mock(TUISession.class);
        assertDoesNotThrow(() -> GoodbyeScreen.show(mockSession, 0, false));
    }
}
