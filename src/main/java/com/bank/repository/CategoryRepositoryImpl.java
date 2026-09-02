package com.bank.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.Category;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CategoryRepositoryImpl implements CategoryRepository {

    @Override
    public Optional<Category> findById(Long categoryId) {
        String sql = "SELECT * FROM categories WHERE category_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, categoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding category by id: " + categoryId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Category> findSystemCategories() {
        String sql = "SELECT * FROM categories WHERE is_system = true ORDER BY category_id";
        List<Category> categories = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) categories.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding system categories", e);
        }
        return categories;
    }

    @Override
    public List<Category> findCustomCategoriesByUserId(Long userId) {
        String sql = "SELECT * FROM categories WHERE user_id = ? ORDER BY category_id";
        List<Category> categories = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) categories.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding custom categories for user: " + userId, e);
        }
        return categories;
    }

    @Override
    public List<Category> findVisibleForUser(Long userId) {
        String sql = "SELECT * FROM categories WHERE is_system = true OR user_id = ? ORDER BY category_id";
        List<Category> categories = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) categories.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding visible categories for user: " + userId, e);
        }
        return categories;
    }

    @Override
    public List<Category> findAll() {
        String sql = "SELECT * FROM categories ORDER BY category_id";
        List<Category> categories = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) categories.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all categories", e);
        }
        return categories;
    }

    @Override
    public Category save(Category category) {
        return category.getCategoryId() == null ? insert(category) : update(category);
    }

    private Category insert(Category category) {
        String sql = """
            INSERT INTO categories (user_id, name, description, is_system, created_at)
            VALUES (?, ?, ?, ?, ?)
            RETURNING category_id
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            if (category.getUserId() != null) {
                stmt.setLong(1, category.getUserId());
            } else {
                stmt.setNull(1, Types.BIGINT);
            }
            stmt.setString(2, category.getName());
            stmt.setString(3, category.getDescription());
            stmt.setBoolean(4, category.isSystem());
            stmt.setTimestamp(5, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    category.setCategoryId(rs.getLong("category_id"));
                    category.setCreatedAt(now);
                }
            }
            return category;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting category", e);
        }
    }

    private Category update(Category category) {
        String sql = "UPDATE categories SET name = ?, description = ? WHERE category_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, category.getName());
            stmt.setString(2, category.getDescription());
            stmt.setLong(3, category.getCategoryId());
            stmt.executeUpdate();
            return category;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating category", e);
        }
    }

    @Override
    public boolean deleteById(Long categoryId) {
        String sql = "DELETE FROM categories WHERE category_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, categoryId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting category: " + categoryId, e);
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        Long userId = rs.getObject("user_id") != null ? rs.getLong("user_id") : null;

        return Category.builder()
                .categoryId(rs.getLong("category_id"))
                .userId(userId)
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .system(rs.getBoolean("is_system"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}