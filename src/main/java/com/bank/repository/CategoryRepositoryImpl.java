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
    public Optional<Category> findByName(String name) {
        String sql = "SELECT * FROM categories WHERE name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding category by name: " + name, e);
        }
        return Optional.empty();
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
            INSERT INTO categories (name, description, is_system, created_at)
            VALUES (?, ?, ?, ?)
            RETURNING category_id
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setString(1, category.getName());
            stmt.setString(2, category.getDescription());
            stmt.setBoolean(3, category.isSystem());
            stmt.setTimestamp(4, Timestamp.valueOf(now));

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
        String sql = "UPDATE categories SET name = ?, description = ?, is_system = ? WHERE category_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, category.getName());
            stmt.setString(2, category.getDescription());
            stmt.setBoolean(3, category.isSystem());
            stmt.setLong(4, category.getCategoryId());
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
        return Category.builder()
                .categoryId(rs.getLong("category_id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .system(rs.getBoolean("is_system"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}