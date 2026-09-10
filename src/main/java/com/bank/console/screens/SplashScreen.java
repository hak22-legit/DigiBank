package com.bank.console.screens;

import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;

public class SplashScreen implements Screen {

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        session.clearScreen();

        System.out.println(TUIBox.top(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        // DigiBank ASCII Art Logo in Gold
        String[] logoLines = {
                "  ██████╗ ██╗ ██████╗ ██╗██████╗  █████╗ ███╗   ██╗██╗  ██╗",
                "  ██╔══██╗██║██╔════╝ ██║██╔══██╗██╔══██╗████╗  ██║██║ ██╔╝",
                "  ██║  ██║██║██║  ███╗██║██████╔╝███████║██╔██╗ ██║█████╔╝ ",
                "  ██║  ██║██║██║   ██║██║██╔══██╗██╔══██║██║╚██╗██║██╔═██╗ ",
                "  ██████╔╝██║╚██████╔╝██║██████╔╝██║  ██║██║ ╚████║██║  ██╗",
                "  ╚═════╝ ╚═╝ ╚═════╝ ╚═╝╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝"
        };

        for (String line : logoLines) {
            System.out.println(TUIBox.center(ConsoleTheme.logo(line), TUILayout.APP_WIDTH));
        }

        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.center(ConsoleTheme.BOLD + ConsoleTheme.FG_DEFAULT + "ENTERPRISE BANKING & FINANCIAL SYSTEM" + ConsoleTheme.RESET, TUILayout.APP_WIDTH));
        System.out.println(TUIBox.center(ConsoleTheme.muted("Secure  •  Fast  •  Intelligent  •  ACID-Compliant"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.divider(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.center(ConsoleTheme.success("✔ System Initialized (v1.0-SNAPSHOT)"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.center(ConsoleTheme.muted("PostgreSQL Connection Pool Ready"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.bottom(TUILayout.APP_WIDTH));

        // Prompt to begin
        ConsolePrompt.pause();

        // Navigate to Welcome Screen
        navigator.clearAndPush(new WelcomeScreen());
    }
}