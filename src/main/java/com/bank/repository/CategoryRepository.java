package com.bank.repository;

import com.bank.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Optional<Category> findById(Long categoryId);
    Optional<Category> findByName(String name);
    List<Category> findAll();
    Category save(Category category);
    boolean deleteById(Long categoryId);
}