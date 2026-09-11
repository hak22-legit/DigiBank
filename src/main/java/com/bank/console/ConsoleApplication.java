package com.bank.console;

import com.bank.console.components.*;
import com.bank.console.screens.Screen;
import com.bank.console.screens.SplashScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Root controller for the terminal user interface.
 * Drives the ScreenNavigator execution loop and ensures graceful terminal shutdown.
 */
public class ConsoleApplication {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleApplication.class);

    private final ScreenNavigator navigator;
    private final TUISession session;

    public ConsoleApplication() {
        this.navigator = new ScreenNavigator();
        this.session = TUISession.getInstance();

        // Register JVM Shutdown Hook to restore terminal mode
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down DigiBank Console Application...");
            session.close();
        }));
    }

    public void start() {
        logger.info("Booting DigiBank Console Application...");

        // Initialize TUI4J Terminal Info
        com.williamcallahan.tui4j.term.TerminalInfo.provide(() -> new com.williamcallahan.tui4j.term.TerminalInfo(true, null));

        // Initialize ControllerFactory Dependency Injection container
        ControllerFactory.init();

        // Push Initial Startup Screen
        navigator.push(new SplashScreen());

        // Master Navigation Loop
        while (!navigator.isEmpty()) {
            Screen currentScreen = navigator.getCurrentScreen();
            try {
                currentScreen.render(navigator, session);
            } catch (Exception e) {
                logger.error("Unhandled error on screen: {}", currentScreen.getClass().getSimpleName(), e);
                handleScreenException(e);
            }
        }

        // Clean exit
        session.clearScreen();
        System.out.println("Thank you for using DigiBank. Goodbye!");
        session.close();
    }

    private void handleScreenException(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUILayout.header("System Error"));
        sb.append(TUILayout.screenTitle("Unexpected Error"));
        sb.append(TUILayout.alert("An error occurred: " + e.getMessage(), true));
        sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUILayout.footer("Press Enter to return to previous screen"));
        com.bank.console.components.ScreenRenderer.render(sb.toString(), true);
        ConsolePrompt.pause();
        navigator.pop();
    }
}