package com.bank.database;

import com.bank.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DatabaseConfig.getUrl());
        config.setUsername(DatabaseConfig.getUsername());
        config.setPassword(DatabaseConfig.getPassword());
        config.setDriverClassName(DatabaseConfig.getDriver());

        // Pool settings
        config.setMaximumPoolSize(DatabaseConfig.getPoolSize());
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000);
        config.setPoolName("BankingPool");

        // Recommended for PostgreSQL
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
    }

    private DatabaseConnection() {
        // Prevent instantiation
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static HikariDataSource getDataSource() {
        return dataSource;
    }

    public static int getActiveConnections() {
        return (dataSource != null && dataSource.getHikariPoolMXBean() != null)
                ? dataSource.getHikariPoolMXBean().getActiveConnections() : 0;
    }

    public static int getIdleConnections() {
        return (dataSource != null && dataSource.getHikariPoolMXBean() != null)
                ? dataSource.getHikariPoolMXBean().getIdleConnections() : 0;
    }

    public static int getTotalConnections() {
        return (dataSource != null && dataSource.getHikariPoolMXBean() != null)
                ? dataSource.getHikariPoolMXBean().getTotalConnections() : 0;
    }

    public static int getThreadsAwaitingConnection() {
        return (dataSource != null && dataSource.getHikariPoolMXBean() != null)
                ? dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection() : 0;
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}