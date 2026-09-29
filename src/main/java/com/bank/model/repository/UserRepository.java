package com.bank.model.repository;

import com.bank.model.dto.UserDirectoryItem;
import com.bank.model.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long userId);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    List<User> findAll();
    User save(User user);
    boolean deleteById(Long userId);

    List<UserDirectoryItem> findUserDirectorySummary(int offset, int limit);
    long countUsers();

    default Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email) {
        if (username != null && !username.isBlank()) {
            Optional<User> byUser = findByUsername(username.trim());
            if (byUser.isPresent()) return byUser;
            for (User u : findAll()) {
                if (u.getUsername() != null && u.getUsername().equalsIgnoreCase(username.trim())) {
                    return Optional.of(u);
                }
            }
        }
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = findByEmail(email.trim().toLowerCase());
            if (byEmail.isPresent()) return byEmail;
            for (User u : findAll()) {
                if (u.getEmail() != null && u.getEmail().equalsIgnoreCase(email.trim())) {
                    return Optional.of(u);
                }
            }
        }
        return Optional.empty();
    }

    default boolean updatePassword(Long userId, String newPasswordHash) {
        return false;
    }
}