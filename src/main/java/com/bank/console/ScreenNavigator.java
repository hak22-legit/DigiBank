package com.bank.console;

import com.bank.console.screens.Screen;
import java.util.Stack;

/**
 * Manages the navigation stack for the application screens.
 * Allows for pushing new screens and popping back to the previous one.
 */
public class ScreenNavigator {
    private final Stack<Screen> screenStack = new Stack<>();

    public void push(Screen screen) {
        if (screen != null) {
            screenStack.push(screen);
        }
    }

    public void pushScreen(Screen screen) {
        push(screen);
    }

    public void pop() {
        if (screenStack.size() > 1) {
            screenStack.pop();
        }
    }

    public void popScreen() {
        pop();
    }

    public int size() {
        return screenStack.size();
    }

    public Screen getCurrentScreen() {
        return screenStack.isEmpty() ? null : screenStack.peek();
    }

    public boolean isEmpty() {
        return screenStack.isEmpty();
    }

    public void replace(Screen screen) {
        if (!screenStack.isEmpty()) {
            screenStack.pop();
        }
        push(screen);
    }

    public void clearAndPush(Screen screen) {
        screenStack.clear();
        push(screen);
    }
}
