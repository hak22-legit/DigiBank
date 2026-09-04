package com.bank.model.repository;

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
}