package com.bank.kt;

import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationVersioningTest {

    @Test
    void liquibaseMigrationsUpdateBankSchemaWithoutDataLoss() throws Exception {
        try (Connection connection = Support.openMemConnection("bank_migrations")) {
            Support.runLiquibase(connection, "db/changelog/bank.changelog-master.xml");

            assertTrue(columnExists(connection, "CUSTOMERS", "LOYALTY_LEVEL"));
            assertFalse(columnExists(connection, "ACCOUNTS", "LEGACY_CODE"));
            assertEquals(1, Support.countRows(connection, "branches"));
            assertEquals(2, Support.countRows(connection, "customers"));
            assertEquals(2, Support.countRows(connection, "accounts"));
            assertEquals("STANDARD",
                    Support.scalar(connection, "SELECT loyalty_level FROM customers WHERE id = 1"));
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        return "1".equals(Support.scalar(connection, """
                SELECT CASE WHEN COUNT(*) > 0 THEN '1' ELSE '0' END
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = '%s' AND COLUMN_NAME = '%s'
                """.formatted(tableName, columnName)));
    }
}
