package com.bank.service;

import com.bank.enums.UserStatus;
import com.bank.exception.AuthenticationException;
import com.bank.exception.DuplicateResourceException;
import com.bank.model.User;
import com.bank.repository.UserRepository;
import com.bank.security.PasswordHasher;
import com.bank.security.SessionManager;

import java.util.Optional;

public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

        return userRepository.save(user);
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