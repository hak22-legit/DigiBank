package com.bank.repository;

import com.bank.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Optional<Category> findById(Long categoryId);
    List<Category> findSystemCategories();
    List<Category> findCustomCategoriesByUserId(Long userId);

    /**
     * Returns everything a user can see: system categories + their own custom ones.
     */
    List<Category> findVisibleForUser(Long userId);

    List<Category> findAll();
    Category save(Category category);
    boolean deleteById(Long categoryId);
}