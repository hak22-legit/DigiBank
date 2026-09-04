package com.bank.service;

import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.enums.UserStatus;
import com.bank.exception.AuthenticationException;
import com.bank.exception.DuplicateResourceException;
import com.bank.model.entity.User;
import com.bank.model.repository.UserRepository;
import com.bank.security.PasswordHasher;
import com.bank.security.SessionManager;

public class AuthService {

    private final UserRepository userRepository;
    private final AccountService accountService;

    public AuthService(UserRepository userRepository, AccountService accountService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
    }

    public User register(String username, String email, String password, String fullName, String phone) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new DuplicateResourceException("Username already taken: " + username);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateResourceException("Email already registered: " + email);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(PasswordHasher.hash(password))
                .fullName(fullName)
                .phone(phone)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        // Default account created automatically on registration.
        // Users can create additional accounts (e.g. SAVINGS, KHR) themselves after logging in.
        accountService.createAccount(savedUser, AccountType.CHECKING, Currency.USD);

        return savedUser;
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        if (!PasswordHasher.verify(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException("Account is not active. Status: " + user.getStatus());
        }

        SessionManager.loginUser(user);
        return user;
    }

    public void logout() {
        SessionManager.logout();
    }
}