package com.bank.console.screens;

import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;

public class SplashScreen implements Screen {

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");

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
            sb.append(TUIBox.center(ConsoleTheme.logo(line), TUILayout.APP_WIDTH)).append("\n");
        }

        sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.BOLD + ConsoleTheme.FG_DEFAULT + "ENTERPRISE BANKING & FINANCIAL SYSTEM" + ConsoleTheme.RESET, TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.muted("Secure  •  Fast  •  Intelligent  •  ACID-Compliant"), TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.divider(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.success("✔ System Initialized (v1.0-SNAPSHOT)"), TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.muted("PostgreSQL Connection Pool Ready"), TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.bottom(TUILayout.APP_WIDTH)).append("\n");

        ScreenRenderer.render(sb.toString(), true);

        // Prompt to begin
        ConsolePrompt.pause();

        // Navigate to Welcome Screen
        navigator.clearAndPush(new WelcomeScreen());
    }
}