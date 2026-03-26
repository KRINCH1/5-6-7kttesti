package com.bank.kt;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserCrudTest {

    @Test
    void createUserWithValidData() throws Exception {
        try (Connection connection = Support.openMemConnection("crud_create_valid")) {
            createUsersTable(connection);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO users(id, full_name, email, status)
                    VALUES (?, ?, ?, ?)
                    """)) {
                statement.setLong(1, 1L);
                statement.setString(2, "Ivan Petrov");
                statement.setString(3, "ivan.petrov@bank.local");
                statement.setString(4, "ACTIVE");
                int inserted = statement.executeUpdate();

                assertEquals(1, inserted);
                assertEquals(1, Support.countRows(connection, "users"));
                assertEquals("Ivan Petrov",
                        Support.scalar(connection, "SELECT full_name FROM users WHERE id = 1"));
            }
        }
    }

    @Test
    void createUserWithDuplicateEmailFails() throws Exception {
        try (Connection connection = Support.openMemConnection("crud_create_negative")) {
            createUsersTable(connection);
            Support.executeSql(connection, """
                    INSERT INTO users(id, full_name, email, status)
                    VALUES (1, 'Ivan Petrov', 'ivan.petrov@bank.local', 'ACTIVE')
                    """);

            SQLException exception = assertThrows(SQLException.class, () -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO users(id, full_name, email, status)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, 2L);
                    statement.setString(2, "Anna Sidorova");
                    statement.setString(3, "ivan.petrov@bank.local");
                    statement.setString(4, "ACTIVE");
                    statement.executeUpdate();
                }
            });

            assertTrue(exception.getMessage().toLowerCase().contains("unique"));
            assertEquals(1, Support.countRows(connection, "users"));
        }
    }

    @Test
    void readUsersReturnsInsertedRows() throws Exception {
        try (Connection connection = Support.openMemConnection("crud_read_valid")) {
            createUsersTable(connection);
            Support.executeSql(connection, """
                    INSERT INTO users(id, full_name, email, status) VALUES
                    (1, 'Ivan Petrov', 'ivan.petrov@bank.local', 'ACTIVE'),
                    (2, 'Anna Sidorova', 'anna.sidorova@bank.local', 'BLOCKED')
                    """);

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT full_name, status
                    FROM users
                    ORDER BY id
                    """);
                 ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                assertEquals("Ivan Petrov", resultSet.getString("full_name"));
                assertEquals("ACTIVE", resultSet.getString("status"));

                resultSet.next();
                assertEquals("Anna Sidorova", resultSet.getString("full_name"));
                assertEquals("BLOCKED", resultSet.getString("status"));
            }
        }
    }

    @Test
    void readUserByMissingIdReturnsEmptyResult() throws Exception {
        try (Connection connection = Support.openMemConnection("crud_read_negative")) {
            createUsersTable(connection);

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id
                    FROM users
                    WHERE id = ?
                    """)) {
                statement.setLong(1, 999L);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(!resultSet.next());
                }
            }
        }
    }

    @Test
    void updateUserStatusWithValidValue() throws Exception {
        try (Connection connection = Support.openMemConnection("crud_update_valid")) {
            createUsersTable(connection);
            Support.executeSql(connection, """
                    INSERT INTO users(id, full_name, email, status)
                    VALUES (1, 'Ivan Petrov', 'ivan.petrov@bank.local', 'ACTIVE')
                    """);

            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE users
                    SET status = ?
                    WHERE id = ?
                    """)) {
                statement.setString(1, "BLOCKED");
                statement.setLong(2, 1L);
                int updated = statement.executeUpdate();

                assertEquals(1, updated);
                assertEquals("BLOCKED",
                        Support.scalar(connection, "SELECT status FROM users WHERE id = 1"));
            }
        }
    }

    @Test
    void updateUserWithInvalidStatusFails() throws Exception {
        try (Connection connection = Support.openMemConnection("crud_update_negative")) {
            createUsersTable(connection);
            Support.executeSql(connection, """
                    INSERT INTO users(id, full_name, email, status)
                    VALUES (1, 'Ivan Petrov', 'ivan.petrov@bank.local', 'ACTIVE')
                    """);

            SQLException exception = assertThrows(SQLException.class, () -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE users
                        SET status = ?
                        WHERE id = ?
                        """)) {
                    statement.setString(1, "UNKNOWN");
                    statement.setLong(2, 1L);
                    statement.executeUpdate();
                }
            });

            assertTrue(exception.getMessage().toLowerCase().contains("check constraint"));
            assertEquals("ACTIVE",
                    Support.scalar(connection, "SELECT status FROM users WHERE id = 1"));
        }
    }

    @Test
    void deleteUserById() throws Exception {
        try (Connection connection = Support.openMemConnection("crud_delete_valid")) {
            createUsersTable(connection);
            Support.executeSql(connection, """
                    INSERT INTO users(id, full_name, email, status)
                    VALUES (1, 'Ivan Petrov', 'ivan.petrov@bank.local', 'ACTIVE')
                    """);

            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM users
                    WHERE id = ?
                    """)) {
                statement.setLong(1, 1L);
                int deleted = statement.executeUpdate();

                assertEquals(1, deleted);
                assertEquals(0, Support.countRows(connection, "users"));
            }
        }
    }

    @Test
    void deleteMissingUserDoesNotAffectData() throws Exception {
        try (Connection connection = Support.openMemConnection("crud_delete_negative")) {
            createUsersTable(connection);
            Support.executeSql(connection, """
                    INSERT INTO users(id, full_name, email, status)
                    VALUES (1, 'Ivan Petrov', 'ivan.petrov@bank.local', 'ACTIVE')
                    """);

            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM users
                    WHERE id = ?
                    """)) {
                statement.setLong(1, 999L);
                int deleted = statement.executeUpdate();

                assertEquals(0, deleted);
                assertEquals(1, Support.countRows(connection, "users"));
            }
        }
    }

    private void createUsersTable(Connection connection) throws SQLException {
        Support.executeSql(connection, """
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY,
                    full_name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    status VARCHAR(20) NOT NULL,
                    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
                )
                """);
    }
}
