package com.bank.console.screens;

import com.bank.controller.AdminController;

/**
 * SUPER ADMIN > USER PROFILE DOSSIER SCREEN
 * Alias for UserProfileModal providing clean, seamless identity dossier and security dispatch.
 */
public class UserProfileDossierScreen extends UserProfileModal {

    public UserProfileDossierScreen(Long userId) {
        super(userId);
    }

    public UserProfileDossierScreen(String rawUserId) {
        super(rawUserId);
    }

    public UserProfileDossierScreen(AdminController adminController, Long userId) {
        super(adminController, userId);
    }
}
