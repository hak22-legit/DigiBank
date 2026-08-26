package com.bank.security;

import com.bank.model.Admin;
import com.bank.model.User;

public class SessionManager {

    private static User currentUser;
    private static Admin currentAdmin;

    private SessionManager() {}

    public static void loginUser(User user) {
        clear();
        currentUser = user;
    }

    public static void loginAdmin(Admin admin) {
        clear();
        currentAdmin = admin;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static Admin getCurrentAdmin() {
        return currentAdmin;
    }

    public static boolean isUserLoggedIn() {
        return currentUser != null;
    }

    public static boolean isAdminLoggedIn() {
        return currentAdmin != null;
    }

    public static void logout() {
        clear();
    }

    private static void clear() {
        currentUser = null;
        currentAdmin = null;
    }
}