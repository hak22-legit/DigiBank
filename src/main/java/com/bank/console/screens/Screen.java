package com.bank.console.screens;

import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;

public interface Screen {
    /**
     * Renders the screen, handles keyboard interaction, and delegates
     * navigation events to the ScreenNavigator.
     *
     * @param navigator Stack-based screen navigator
     * @param session   Terminal and session manager
     */
    void render(ScreenNavigator navigator, TUISession session);
}