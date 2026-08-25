package com.bank;

import com.bank.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class Main {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Enterprise Banking System");
        System.out.println("  Phase 3 - HikariCP + JDBC");
        System.out.println("========================================");

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM categories")) {

            if (rs.next()) {
                System.out.println("Database connection: SUCCESS");
                System.out.println("Categories in database: " + rs.getInt(1));
            }

        } catch (Exception e) {
            System.err.println("Database connection: FAILED");
            e.printStackTrace();
        } finally {
            DatabaseConnection.closePool();
        }
    }
}