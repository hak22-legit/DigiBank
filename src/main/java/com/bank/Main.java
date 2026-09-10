package com.bank;

import com.bank.console.ConsoleApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Enforce UTF-8 encoding across Java runtime and standard I/O streams
        enforceUtf8Encoding();

        logger.info("Initializing DigiBank...");
        try {
            ConsoleApplication app = new ConsoleApplication();
            app.start();
        } catch (Exception e) {
            logger.error("Fatal startup error: {}", e.getMessage(), e);
            System.err.println("Fatal error starting DigiBank: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void enforceUtf8Encoding() {
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");

        try {
            // Re-bind System.out and System.err to explicit UTF-8 PrintStreams
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8.name()));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            logger.warn("Could not rebind console output streams to UTF-8: {}", e.getMessage());
        }
    }
}
