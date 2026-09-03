package com.bank.service;

import com.bank.exception.CategoryNotFoundException;
import com.bank.exception.DuplicateResourceException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.Category;
import com.bank.model.User;
import com.bank.repository.CategoryRepository;

import java.time.LocalDateTime;
import java.util.List;

public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Returns everything visible to this user: shared system categories
     * plus their own private custom categories.
     */
    public List<Category> getVisibleCategories(User user) {
        return categoryRepository.findVisibleForUser(user.getUserId());
    }

    public List<Category> getSystemCategories() {
        return categoryRepository.findSystemCategories();
    }

    public List<Category> getMyCustomCategories(User user) {
        return categoryRepository.findCustomCategoriesByUserId(user.getUserId());
    }

    public Category getCategoryById(Long categoryId, User requestingUser) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));

        assertVisible(category, requestingUser);
        return category;
    }

    /**
     * Creates a private custom category owned by the given user.
     * Duplicate names are only rejected within the SAME owner (system
     * categories vs. this user's own categories) - two different users
     * can freely use the same category name.
     */
    public Category createCustomCategory(String name, String description, User owner) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }

        boolean duplicateForThisUser = categoryRepository.findCustomCategoriesByUserId(owner.getUserId())
                .stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(name.trim()));
        if (duplicateForThisUser) {
            throw new DuplicateResourceException("You already have a category named: " + name);
        }

        Category category = Category.builder()
                .userId(owner.getUserId())
                .name(name.trim())
                .description(description)
                .system(false)
                .createdAt(LocalDateTime.now())
                .build();

        return categoryRepository.save(category);
    }

    /**
     * Deletes a custom category. System categories can never be deleted,
     * and a user can only delete their own custom categories.
     */
    public void deleteCustomCategory(Long categoryId, User requestingUser) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));

        if (category.isSystem()) {
            throw new UnauthorizedException("System categories cannot be deleted");
        }
        if (!category.getUserId().equals(requestingUser.getUserId())) {
            throw new UnauthorizedException("You do not have access to this category");
        }

        categoryRepository.deleteById(categoryId);
    }

    private void assertVisible(Category category, User requestingUser) {
        boolean isOwnedByRequester = category.getUserId() != null
                && category.getUserId().equals(requestingUser.getUserId());

        if (!category.isSystem() && !isOwnedByRequester) {
            throw new UnauthorizedException("You do not have access to this category");
        }
    }
}