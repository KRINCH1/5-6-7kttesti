package com.bank.kt;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSecurityTest {

    @Test
    void guestUserCannotModifyProtectedData() throws Exception {
        String dbName = "bank_security_rights";
        try (Connection adminConnection = Support.openMemConnection(dbName)) {
            Support.executeSql(adminConnection, """
                    CREATE USER IF NOT EXISTS bank_admin PASSWORD 'admin123';
                    CREATE USER IF NOT EXISTS bank_user PASSWORD 'user123';
                    CREATE USER IF NOT EXISTS bank_guest PASSWORD 'guest123';

                    CREATE TABLE users (
                        id BIGINT PRIMARY KEY,
                        username VARCHAR(50) NOT NULL,
                        role_name VARCHAR(20) NOT NULL
                    );

                    INSERT INTO users(id, username, role_name) VALUES
                        (1, 'admin', 'ADMIN'),
                        (2, 'operator', 'USER'),
                        (3, 'guest', 'GUEST');

                    GRANT SELECT, INSERT, UPDATE, DELETE ON users TO bank_admin;
                    GRANT SELECT ON users TO bank_user;
                    GRANT SELECT ON users TO bank_guest;
                    """);
        }

        try (Connection guestConnection = DriverManagerHolder.openExisting(dbName, "bank_guest", "guest123")) {
            SQLException exception = assertThrows(SQLException.class, () ->
                    Support.executeSql(guestConnection, "DELETE FROM users WHERE id = 1"));
            assertTrue(exception.getMessage().toLowerCase().contains("not enough rights"));
            assertEquals(3, Support.countRows(guestConnection, "users"));
        }
    }

    @Test
    void preparedStatementBlocksSqlInjectionAttempt() throws Exception {
        try (Connection connection = Support.openMemConnection("bank_injection")) {
            Support.executeSql(connection, """
                    CREATE TABLE customers (
                        id BIGINT PRIMARY KEY,
                        login VARCHAR(50) NOT NULL,
                        pin_code VARCHAR(20) NOT NULL
                    );

                    INSERT INTO customers(id, login, pin_code) VALUES
                        (1, 'client1', '1234'),
                        (2, 'client2', '5678');
                    """);

            String maliciousLogin = "client1' OR '1'='1";
            int matches;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM customers WHERE login = ? AND pin_code = ?")) {
                statement.setString(1, maliciousLogin);
                statement.setString(2, "9999");
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    matches = resultSet.getInt(1);
                }
            }

            assertEquals(0, matches);
            assertEquals(2, Support.countRows(connection, "customers"));
        }
    }

    @Test
    void confidentialDataIsStoredEncrypted() throws Exception {
        try (Connection connection = Support.openMemConnection("bank_encryption")) {
            Support.executeSql(connection, """
                    CREATE TABLE clients (
                        id BIGINT PRIMARY KEY,
                        full_name VARCHAR(255) NOT NULL,
                        passport_encrypted VARCHAR(512) NOT NULL
                    )
                    """);

            String plainPassport = "4510 123456";
            String encryptedPassport = Support.encrypt(plainPassport);

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO clients(id, full_name, passport_encrypted) VALUES (?, ?, ?)")) {
                statement.setLong(1, 1L);
                statement.setString(2, "Petr Ivanov");
                statement.setString(3, encryptedPassport);
                statement.executeUpdate();
            }

            String storedValue = Support.scalar(connection,
                    "SELECT passport_encrypted FROM clients WHERE id = 1");

            assertNotEquals(plainPassport, storedValue);
            assertEquals(plainPassport, Support.decrypt(storedValue));
        }
    }

    private static final class DriverManagerHolder {
        private static Connection openExisting(String dbName, String user, String password) throws SQLException {
            return java.sql.DriverManager.getConnection(
                    "jdbc:h2:mem:" + dbName,
                    user,
                    password
            );
        }
    }
}
