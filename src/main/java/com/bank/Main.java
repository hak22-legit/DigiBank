package com.bank;

import com.bank.database.DatabaseConnection;
import com.bank.model.Category;
import com.bank.model.User;
import com.bank.repository.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Enterprise Banking System");
        System.out.println("  Phase 5 - Repository / DAO Layer");
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
        }

        // Phase 5 repository tests
        CategoryRepository catRepo = new CategoryRepositoryImpl();
        List<Category> categories = catRepo.findAll();
        System.out.println("Categories found: " + categories.size()); // should be 8

        UserRepository userRepo = new UserRepositoryImpl();
        List<User> users = userRepo.findAll();
        System.out.println("Users found: " + users.size()); // 0 if no test data yet

        LoanRepository loanRepo = new LoanRepositoryImpl();
        System.out.println("Loans found: " + loanRepo.findAll().size());

        BudgetRepository budgetRepo = new BudgetRepositoryImpl();
        System.out.println("Budgets found: " + budgetRepo.findAll().size());

        // Pool shutdown must ALWAYS be the last thing that happens
        DatabaseConnection.closePool();
    }
}