package com.bank.console;

import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ScreenRendererTest {

    @Test
    @DisplayName("computeLeftPadding calculates correct centering offset on standard/wide terminals")
    void testComputeLeftPaddingStandardAndWide() {
        // Standard 140-col widescreen terminal with 82-col content: (140 - 82) / 2 = 29
        assertEquals(29, ScreenRenderer.computeLeftPadding(140, 82));

        // 120-col terminal: (120 - 82) / 2 = 19
        assertEquals(19, ScreenRenderer.computeLeftPadding(120, 82));

        // 100-col terminal: (100 - 82) / 2 = 9
        assertEquals(9, ScreenRenderer.computeLeftPadding(100, 82));

        // Exact match: 82-col terminal with 82-col content -> 0
        assertEquals(0, ScreenRenderer.computeLeftPadding(82, 82));
    }

    @ParameterizedTest
    @CsvSource({
            "81, 82, 0",
            "80, 82, 0",
            "50, 82, 0",
            "0, 82, 0",
            "-10, 82, 0"
    })
    @DisplayName("computeLeftPadding boundary fallback: returns 0 when terminal width < box width")
    void testComputeLeftPaddingNarrowFallback(int termWidth, int boxWidth, int expected) {
        assertEquals(expected, ScreenRenderer.computeLeftPadding(termWidth, boxWidth),
                "Narrow or boundary terminal width must not produce negative or wrapping left padding");
    }

    @Test
    @DisplayName("computeTopPadding calculates correct vertical centering offset on tall terminals")
    void testComputeTopPaddingTall() {
        // 40-row terminal with 20-row box: (40 - 20) / 2 = 10
        assertEquals(10, ScreenRenderer.computeTopPadding(40, 20));

        // 50-row terminal with 24-row box: (50 - 24) / 2 = 13
        assertEquals(13, ScreenRenderer.computeTopPadding(50, 24));
    }

    @ParameterizedTest
    @CsvSource({
            "20, 20, 0",
            "19, 20, 0",
            "10, 20, 0",
            "0, 20, 0",
            "-5, 20, 0"
    })
    @DisplayName("computeTopPadding boundary fallback: returns 0 when terminal height <= box height")
    void testComputeTopPaddingShortFallback(int termHeight, int boxHeight, int expected) {
        assertEquals(expected, ScreenRenderer.computeTopPadding(termHeight, boxHeight),
                "Short or boundary terminal height must not produce negative top padding or scrolling");
    }

    @Test
    @DisplayName("formatCentered correctly applies vertical blank lines and horizontal space indentation")
    void testFormatCenteredWideWindow() {
        String box = String.join("\n",
                TUIBox.top(82),
                TUIBox.line("TITLE", 82),
                TUIBox.bottom(82),
                " [↑/↓] Navigate  •  [Enter] Select"
        );

        int termWidth = 120;
        int termHeight = 30;
        int boxWidth = 82;

        String formatted = ScreenRenderer.formatCentered(box, termWidth, termHeight, boxWidth, true);

        // Expected offsets:
        // leftPadding = (120 - 82) / 2 = 19
        // boxHeight = 4 lines
        // topPadding = (30 - 4) / 2 = 13
        int expectedLeft = 19;
        int expectedTop = 13;

        assertTrue(formatted.startsWith("\u001B[H\u001B[2J"), "First render should begin with complete screen clearing sequence");

        String afterClear = formatted.substring("\u001B[H\u001B[2J".length());
        // Verify topPadding blank lines
        String expectedTopBlankLines = "\n".repeat(expectedTop);
        assertTrue(afterClear.startsWith(expectedTopBlankLines), "Top padding must inject blank lines for vertical centering");

        String contentPortion = afterClear.substring(expectedTopBlankLines.length());
        String[] renderedLines = contentPortion.split("\n");

        assertEquals(4, renderedLines.length, "Should preserve the 4 rendered content rows");

        String expectedIndent = " ".repeat(expectedLeft);
        for (String line : renderedLines) {
            assertTrue(line.startsWith(expectedIndent),
                    "Every rendered line (including top border and footer shortcut hints) must be indented by leftPadding: " + line);
        }
    }

    @Test
    @DisplayName("formatCentered uses redraw ANSI sequence and preserves centering on redraw (firstRender=false)")
    void testFormatCenteredRedrawMode() {
        String box = TUIBox.top(82) + "\n" + TUIBox.bottom(82);
        String formatted = ScreenRenderer.formatCentered(box, 100, 20, 82, false);

        assertTrue(formatted.startsWith("\u001B[H\u001B[J"),
                "Redraw render should home cursor and clear downwards without full buffer wipe to avoid flicker");

        int expectedLeft = (100 - 82) / 2; // 9
        int expectedTop = (20 - 2) / 2;    // 9
        String expectedIndent = " ".repeat(expectedLeft);

        String afterAnsi = formatted.substring("\u001B[H\u001B[J".length());
        assertTrue(afterAnsi.startsWith("\n".repeat(expectedTop)), "Should have vertical top padding");

        String contentOnly = afterAnsi.substring(expectedTop);
        String[] lines = contentOnly.split("\n");
        assertEquals(2, lines.length);
        for (String line : lines) {
            assertTrue(line.startsWith(expectedIndent));
        }
    }

    @Test
    @DisplayName("formatCentered gracefully falls back to 0 padding when terminal is narrower and shorter than content")
    void testFormatCenteredNarrowAndShortFallback() {
        String box = TUIBox.top(82) + "\n" + TUIBox.bottom(82);
        int termWidth = 80;  // Narrower than 82
        int termHeight = 2;  // Exact match with 2 lines

        String formatted = ScreenRenderer.formatCentered(box, termWidth, termHeight, 82, true);

        String afterClear = formatted.substring("\u001B[H\u001B[2J".length());
        assertFalse(afterClear.startsWith("\n"), "Should have 0 top padding when termHeight <= boxHeight");

        String[] lines = afterClear.split("\n");
        assertEquals(2, lines.length);
        assertFalse(lines[0].startsWith(" "), "Should not have leading space indent when termWidth < boxWidth");
    }

    @Test
    @DisplayName("formatCentered strips existing leading ANSI clear/home sequences to avoid breaking vertical alignment")
    void testFormatCenteredStripsLeadingAnsi() {
        String rawWithLeadingClear = "\u001B[2J\u001B[H" + TUIBox.top(82) + "\n" + TUIBox.bottom(82);

        String formatted = ScreenRenderer.formatCentered(rawWithLeadingClear, 120, 20, 82, true);

        // Verify it starts with our standard clear sequence once
        assertTrue(formatted.startsWith("\u001B[H\u001B[2J"));
        String afterClear = formatted.substring("\u001B[H\u001B[2J".length());
        assertFalse(afterClear.contains("\u001B[2J"), "Inner content should not retain redundant clearing sequences");

        // Top padding should be (20 - 2) / 2 = 9 newlines
        int expectedTop = 9;
        assertTrue(afterClear.startsWith("\n".repeat(expectedTop)));
    }

    @Test
    @DisplayName("formatCentered handles null and empty content without throwing exceptions")
    void testFormatCenteredNullOrEmpty() {
        assertEquals("", ScreenRenderer.formatCentered(null, 100, 30, 82, true));
        assertEquals("", ScreenRenderer.formatCentered("", 100, 30, 82, true));
        assertEquals("", ScreenRenderer.formatCentered("   \n\n  ", 100, 30, 82, true));
    }
}
