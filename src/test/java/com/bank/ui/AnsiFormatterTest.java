package com.bank.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AnsiFormatterTest {

    @Test
    @DisplayName("colorizeDelta wraps positive amounts in GREEN and negative in RED")
    void testColorizeDelta() {
        String credit = AnsiFormatter.colorizeDelta("$ 4,000.00", true);
        assertEquals(AnsiFormatter.GREEN + "$ 4,000.00" + AnsiFormatter.RESET, credit);

        String debit = AnsiFormatter.colorizeDelta("$ 300.00", false);
        assertEquals(AnsiFormatter.RED + "$ 300.00" + AnsiFormatter.RESET, debit);
    }

    @Test
    @DisplayName("stripAnsi and visibleLength compute true printable length ignoring escape codes")
    void testStripAnsiAndVisibleLength() {
        String colored = AnsiFormatter.colorizeDelta("$ 1,234.56", true);
        assertEquals("$ 1,234.56", AnsiFormatter.stripAnsi(colored));
        assertEquals(10, AnsiFormatter.visibleLength(colored));

        String styled = AnsiFormatter.INVERSE + "MEN SENGHAK" + AnsiFormatter.RESET;
        assertEquals("MEN SENGHAK", AnsiFormatter.stripAnsi(styled));
        assertEquals(11, AnsiFormatter.visibleLength(styled));
    }

    @Test
    @DisplayName("formatRow produces clamped 74-character printable lines within border pipes")
    void testFormatRowPaddingAndClamping() {
        // Short line should be padded to 74 characters inside pipes
        String shortLine = "ACCOUNT IDENTIFICATION & SUMMARY";
        String formatted = AnsiFormatter.formatRow(shortLine);
        assertEquals(80, formatted.length());
        assertTrue(formatted.startsWith("│  "));
        assertTrue(formatted.endsWith("  │"));

        // Long line exceeding 74 characters should be clamped with '...'
        String longLine = "This is an extremely long transaction audit memo that definitely exceeds the seventy-four character printable width limit";
        String clamped = AnsiFormatter.formatRow(longLine);
        assertEquals(80, clamped.length());
        assertTrue(clamped.contains("..."));
    }

    @Test
    @DisplayName("printRow outputs clamped line to standard output")
    void testPrintRow() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
            AnsiFormatter.printRow("Test Line Content");
            String output = outContent.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("│  Test Line Content"));
            assertTrue(output.contains("  │"));
        } finally {
            System.setOut(originalOut);
        }
    }
}
