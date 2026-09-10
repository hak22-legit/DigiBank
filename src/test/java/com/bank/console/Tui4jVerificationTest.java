package com.bank.console;

import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;
import com.williamcallahan.tui4j.term.TerminalInfo;
import com.williamcallahan.tui4j.compat.bubbletea.Program;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Tui4jVerificationTest {

    @Test
    @DisplayName("Verify TUI4J Model implementation")
    void testTui4jModel() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));

        Model testModel = new Model() {
            @Override
            public Command init() {
                return null;
            }

            @Override
            public UpdateResult update(Message message) {
                return UpdateResult.from(this);
            }

            @Override
            public String view() {
                Style header = Style.newStyle()
                        .bold(true)
                        .foreground(Color.color("32"));
                return header.render("DIGIBANK ENTERPRISE TUI");
            }
        };

        assertNotNull(testModel.view());
        assertTrue(testModel.view().contains("DIGIBANK"));

        Style testStyle = Style.newStyle()
                .bold(true)
                .reverse(true)
                .faint(true)
                .foreground(Color.color("32"));
        String rendered = testStyle.render("DIGIBANK");
        assertNotNull(rendered);
        assertEquals("DIGIBANK".length(), com.bank.console.components.TUIBox.visibleLength(rendered));
    }
}
