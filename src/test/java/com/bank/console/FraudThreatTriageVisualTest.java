package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.FraudThreatTriageScreen;
import com.bank.console.screens.FraudTriageScreen;
import com.bank.model.entity.FraudAlert;
import com.bank.model.enums.FraudStatus;
import com.bank.model.enums.RiskLevel;
import org.jline.utils.NonBlockingReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FraudThreatTriageVisualTest {

    @Test
    @DisplayName("Verify FraudThreatTriageScreen conforms strictly to 82 columns when account is FROZEN")
    void testFrozenIncidentDossierLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        FraudAlert alert = FraudAlert.builder()
                .alertId(1L)
                .accountId(9L)
                .userId(3L)
                .riskLevel(RiskLevel.HIGH)
                .status(FraudStatus.RESOLVED)
                .resolutionNotes("Enforced by #ADM-06 (haks). Case closed. Hold applied.")
                .description("High-value transfer: 15,000 USD (Exceeded threshold 10,000)")
                .createdAt(LocalDateTime.of(2026, 8, 30, 21, 15))
                .build();

        String statusMsg = "Status: Account #ACC-09 is currently FROZEN. Press [U] to release hold.";
        String rendered = FraudTriageScreen.renderContent(List.of(alert), List.of(alert), 0, 1, 1, 1, statusMsg, false, width);

        String[] lines = rendered.split("\n");
        // All boxed lines must be exactly 82 visible columns
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "Line " + i + " must be 82 columns: " + lines[i]);
        }

        // Must display INCIDENT DOSSIER [#ALT-01]
        assertTrue(rendered.contains("INCIDENT DOSSIER [#ALT-01]"));

        // Must show account status as [ADMINISTRATIVE HOLD / FROZEN]
        assertTrue(rendered.contains("[ADMINISTRATIVE HOLD / FROZEN]"));

        // Must show non-truncated trigger reason in Incident Dossier panel
        assertTrue(rendered.contains("Trigger Reason : High-value transfer: 15,000 USD (Exceeded threshold 10,000)"));

        // Contextual action buttons for FROZEN: must show [U] Unfreeze and [Esc] Back, and NOT show [H] Hold or [D] Dismiss
        assertTrue(rendered.contains("[U] Unfreeze / Lift Hold"));
        assertTrue(rendered.contains("[Esc] Back to Dashboard"));
        assertFalse(rendered.contains("[H] Hold / Freeze Account"));
        assertFalse(rendered.contains("[D] Dismiss / False Positive"));

        // Dim gray footer
        String footer = lines[lines.length - 1];
        assertTrue(footer.startsWith("\033[2;90m") || footer.contains("\033[2;90m"));
        assertTrue(footer.contains("[U] Unfreeze"));
        assertTrue(footer.contains("[Esc] Back"));
    }

    @Test
    @DisplayName("Verify FraudThreatTriageScreen conforms to UNRESOLVED incident contextual buttons")
    void testUnresolvedIncidentDossierLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        FraudAlert alert = FraudAlert.builder()
                .alertId(2L)
                .accountId(12L)
                .userId(5L)
                .riskLevel(RiskLevel.HIGH)
                .status(FraudStatus.OPEN)
                .description("Suspicious multiple failed PIN attempts detected across ATM terminals.")
                .createdAt(LocalDateTime.of(2026, 9, 20, 10, 30))
                .build();

        String statusMsg = "Status: Incident #ALT-02 selected. Press [H] to Freeze Account, [D] to Dismiss.";
        String rendered = FraudThreatTriageScreen.renderContent(List.of(alert), List.of(alert), 0, 0, 1, 1, statusMsg, false, width);

        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "Line " + i + " must be 82 columns: " + lines[i]);
        }

        // Contextual action buttons for UNRESOLVED: must show [H] Hold and [D] Dismiss
        assertTrue(rendered.contains("[H] Hold / Freeze Account"));
        assertTrue(rendered.contains("[D] Dismiss / False Positive"));
        assertFalse(rendered.contains("[U] Unfreeze / Lift Hold"));

        // Dynamic footer for UNRESOLVED
        String footer = lines[lines.length - 1];
        assertTrue(footer.contains("\033[2;90m"));
        assertTrue(footer.contains("[H] Hold Account"));
        assertTrue(footer.contains("[D] Dismiss"));
        assertTrue(footer.contains("[Esc] Back"));
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
            public int readBuffered(char[] b, int off, int len, long timeout) {
                return -1;
            }
            @Override
            public void close() {}
        };
    }

    @Test
    @DisplayName("Verify TerminalInputHandler parses ANSI arrows, vim keys, special keys, and preserves typing mode")
    void testTerminalInputHandler() throws IOException {
        // ANSI Up arrow
        NonBlockingReader upReader = createReader("\033[A");
        TerminalInputHandler.KeyCode upCode = TerminalInputHandler.readNavigationKey(upReader);
        assertTrue(upCode.isUp());

        // Vim k navigation key
        NonBlockingReader vimKReader = createReader("k");
        TerminalInputHandler.KeyCode vimKCode = TerminalInputHandler.readNavigationKey(vimKReader);
        assertTrue(vimKCode.isUp());

        // Vim j navigation key
        NonBlockingReader vimJReader = createReader("j");
        TerminalInputHandler.KeyCode vimJCode = TerminalInputHandler.readNavigationKey(vimJReader);
        assertTrue(vimJCode.isDown());

        // Vim in typing mode: 'j' is NOT DOWN, but character 'j'
        NonBlockingReader typingJReader = createReader("j");
        TerminalInputHandler.KeyCode typingJCode = TerminalInputHandler.readTypingKey(typingJReader);
        assertFalse(typingJCode.isDown());
        assertEquals('j', typingJCode.ch());

        // Tab key
        NonBlockingReader tabReader = createReader("\t");
        TerminalInputHandler.KeyCode tabCode = TerminalInputHandler.readNavigationKey(tabReader);
        assertTrue(tabCode.isTab());

        // Digit in typing mode: '5' is digit character
        NonBlockingReader digitReader = createReader("5");
        TerminalInputHandler.KeyCode digitCode = TerminalInputHandler.readTypingKey(digitReader);
        assertEquals('5', digitCode.ch());

        // Clamped table bounds
        assertEquals(0, TerminalInputHandler.moveSelectionClamped(0, -1, 5));
        assertEquals(1, TerminalInputHandler.moveSelectionClamped(0, 1, 5));
        assertEquals(4, TerminalInputHandler.moveSelectionClamped(4, 1, 5));

        // Modulo menu bounds
        assertEquals(4, TerminalInputHandler.moveSelectionModulo(0, -1, 5));
        assertEquals(0, TerminalInputHandler.moveSelectionModulo(4, 1, 5));
    }
}
