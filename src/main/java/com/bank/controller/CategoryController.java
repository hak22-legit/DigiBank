package com.bank.controller;

import com.bank.model.entity.Category;
import com.bank.model.entity.User;
import com.bank.service.CategoryService;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    public List<Category> getVisibleCategories(User user) {
        return categoryService.getVisibleCategories(user);
    }

    public Category createCustomCategory(String name, String desc, User user) {
        return categoryService.createCustomCategory(name, desc, user);
    }
}
