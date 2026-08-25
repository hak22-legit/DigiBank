package com.bank.repository;

import com.bank.model.Admin;

import java.util.List;
import java.util.Optional;

public interface AdminRepository {
    Optional<Admin> findById(Long adminId);
    Optional<Admin> findByEmail(String email);
    Optional<Admin> findByUsername(String username);
    List<Admin> findAll();
    Admin save(Admin admin);
    boolean deleteById(Long adminId);
}