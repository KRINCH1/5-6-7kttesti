package com.bank.kt;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryPerformanceTest {

    @Test
    void evaluateQueryExecutionTimeAtDifferentDataVolumes() throws Exception {
        int[] volumes = {1000, 10000, 100000};
        List<String> reportLines = new ArrayList<>();

        for (int volume : volumes) {
            try (Connection connection = Support.openMemConnection("perf_" + volume)) {
                prepareSchema(connection);
                seedData(connection, volume);

                long simpleQueryMs = measureMillis(() -> executeSimpleQuery(connection));
                long joinQueryMs = measureMillis(() -> executeJoinQuery(connection));
                long aggregateQueryMs = measureMillis(() -> executeAggregateQuery(connection));

                reportLines.add(String.format(
                        "volume=%d | simple=%d ms | join=%d ms | aggregate=%d ms",
                        volume, simpleQueryMs, joinQueryMs, aggregateQueryMs
                ));

                assertTrue(simpleQueryMs >= 0);
                assertTrue(joinQueryMs >= 0);
                assertTrue(aggregateQueryMs >= 0);
            }
        }

        reportLines.forEach(System.out::println);
        assertEquals(3, reportLines.size());
    }

    private void prepareSchema(Connection connection) throws Exception {
        Support.executeSql(connection, """
                CREATE TABLE customers (
                    id BIGINT PRIMARY KEY,
                    full_name VARCHAR(255) NOT NULL
                );

                CREATE TABLE accounts (
                    id BIGINT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    account_number VARCHAR(34) NOT NULL,
                    balance DECIMAL(15,2) NOT NULL,
                    CONSTRAINT fk_perf_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
                );

                CREATE TABLE orders_bank (
                    id BIGINT PRIMARY KEY,
                    account_id BIGINT NOT NULL,
                    amount DECIMAL(15,2) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    CONSTRAINT fk_perf_orders_account FOREIGN KEY (account_id) REFERENCES accounts(id)
                );

                CREATE INDEX idx_orders_created_at ON orders_bank(created_at);
                CREATE INDEX idx_orders_account_id ON orders_bank(account_id);
                """);
    }

    private void seedData(Connection connection, int orderCount) throws Exception {
        try (PreparedStatement customerStatement = connection.prepareStatement("""
                INSERT INTO customers(id, full_name)
                VALUES (?, ?)
                """);
             PreparedStatement accountStatement = connection.prepareStatement("""
                     INSERT INTO accounts(id, customer_id, account_number, balance)
                     VALUES (?, ?, ?, ?)
                     """);
             PreparedStatement orderStatement = connection.prepareStatement("""
                     INSERT INTO orders_bank(id, account_id, amount, status, created_at)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {

            for (long id = 1; id <= 500; id++) {
                customerStatement.setLong(1, id);
                customerStatement.setString(2, "Customer " + id);
                customerStatement.addBatch();

                accountStatement.setLong(1, id);
                accountStatement.setLong(2, id);
                accountStatement.setString(3, "ACC-" + id);
                accountStatement.setBigDecimal(4, BigDecimal.valueOf(100000 + id));
                accountStatement.addBatch();
            }

            customerStatement.executeBatch();
            accountStatement.executeBatch();

            LocalDateTime baseDate = LocalDateTime.of(2026, 3, 1, 10, 0);
            for (int i = 1; i <= orderCount; i++) {
                long accountId = (i % 500) + 1L;
                orderStatement.setLong(1, i);
                orderStatement.setLong(2, accountId);
                orderStatement.setBigDecimal(3, BigDecimal.valueOf((i % 10000) + 100));
                orderStatement.setString(4, i % 2 == 0 ? "DONE" : "NEW");
                orderStatement.setTimestamp(5, Timestamp.valueOf(baseDate.plusMinutes(i % 43200)));
                orderStatement.addBatch();

                if (i % 5000 == 0) {
                    orderStatement.executeBatch();
                }
            }
            orderStatement.executeBatch();
        }
    }

    private void executeSimpleQuery(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, account_id, amount, created_at
                FROM orders_bank
                WHERE created_at >= ?
                ORDER BY created_at DESC
                """)) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.of(2026, 3, 1, 0, 0)));
            try (ResultSet resultSet = statement.executeQuery()) {
                int count = 0;
                while (resultSet.next()) {
                    count++;
                }
                assertTrue(count > 0);
            }
        }
    }

    private void executeJoinQuery(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT o.id, c.full_name, a.account_number, o.amount
                FROM orders_bank o
                JOIN accounts a ON o.account_id = a.id
                JOIN customers c ON a.customer_id = c.id
                WHERE o.status = ?
                ORDER BY o.id DESC
                """)) {
            statement.setString(1, "DONE");
            try (ResultSet resultSet = statement.executeQuery()) {
                int count = 0;
                while (resultSet.next()) {
                    count++;
                }
                assertTrue(count > 0);
            }
        }
    }

    private void executeAggregateQuery(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT a.id, COUNT(o.id) AS order_count, SUM(o.amount) AS total_amount
                FROM accounts a
                JOIN orders_bank o ON o.account_id = a.id
                GROUP BY a.id
                HAVING SUM(o.amount) > ?
                """)) {
            statement.setBigDecimal(1, BigDecimal.valueOf(1000));
            try (ResultSet resultSet = statement.executeQuery()) {
                int count = 0;
                while (resultSet.next()) {
                    count++;
                }
                assertTrue(count > 0);
            }
        }
    }

    private long measureMillis(CheckedRunnable runnable) throws Exception {
        long start = System.nanoTime();
        runnable.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
