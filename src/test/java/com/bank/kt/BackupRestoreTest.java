package com.bank.kt;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRestoreTest {

    @Test
    void backupAndRestoreKeepsAllBankData() throws Exception {
        Path sourceDb = Support.createTempDbPath("bank-backup-source-");
        Path restoredDb = Support.createTempDbPath("bank-backup-restored-");
        Path backupScript = Files.createTempFile("bank-backup-", ".sql");

        try (Connection sourceConnection = Support.openFileConnection(sourceDb)) {
            Support.executeSql(sourceConnection, """
                    CREATE TABLE customers (
                        id BIGINT PRIMARY KEY,
                        full_name VARCHAR(255) NOT NULL,
                        email VARCHAR(255) NOT NULL UNIQUE
                    );

                    CREATE TABLE accounts (
                        id BIGINT PRIMARY KEY,
                        customer_id BIGINT NOT NULL,
                        iban VARCHAR(34) NOT NULL UNIQUE,
                        balance DECIMAL(15, 2) NOT NULL,
                        CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
                    );

                    CREATE TABLE transactions (
                        id BIGINT PRIMARY KEY,
                        account_id BIGINT NOT NULL,
                        amount DECIMAL(15, 2) NOT NULL,
                        description VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts(id)
                    );

                    INSERT INTO customers(id, full_name, email) VALUES
                        (1, 'Ivan Petrov', 'ivan.petrov@bank.local'),
                        (2, 'Anna Sidorova', 'anna.sidorova@bank.local');

                    INSERT INTO accounts(id, customer_id, iban, balance) VALUES
                        (10, 1, 'RU0000000000000000000000000001', 125000.50),
                        (11, 2, 'RU0000000000000000000000000002', 87000.00);

                    INSERT INTO transactions(id, account_id, amount, description, created_at) VALUES
                        (100, 10, -1500.00, 'Card payment', TIMESTAMP '2026-03-20 10:15:00'),
                        (101, 10, 50000.00, 'Salary transfer', TIMESTAMP '2026-03-21 09:00:00'),
                        (102, 11, -3200.00, 'Utility payment', TIMESTAMP '2026-03-21 12:30:00');
                    """);

            Support.executeSql(sourceConnection,
                    "SCRIPT DROP TO '" + backupScript.toAbsolutePath().toString().replace("\\", "/") + "'");
        }

        assertTrue(Files.exists(backupScript));
        assertTrue(Files.size(backupScript) > 0);

        try (Connection restoredConnection = Support.openFileConnection(restoredDb)) {
            Support.executeSql(restoredConnection,
                    "RUNSCRIPT FROM '" + backupScript.toAbsolutePath().toString().replace("\\", "/") + "'");

            assertEquals(2, Support.countRows(restoredConnection, "customers"));
            assertEquals(2, Support.countRows(restoredConnection, "accounts"));
            assertEquals(3, Support.countRows(restoredConnection, "transactions"));
            assertEquals("Ivan Petrov",
                    Support.scalar(restoredConnection, "SELECT full_name FROM customers WHERE id = 1"));
            assertEquals("125000.50",
                    Support.scalar(restoredConnection, "SELECT CAST(balance AS VARCHAR) FROM accounts WHERE id = 10"));
        }
    }
}
