package com.bank.kt;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Base64;

final class Support {

    private static final String USER = "sa";
    private static final String PASSWORD = "password";
    private static final String ENCRYPTION_KEY = "bank-kt-demo-key";

    private Support() {
    }

    static Connection openFileConnection(Path dbFile) throws SQLException {
        String url = "jdbc:h2:file:" + normalize(dbFile) + ";AUTO_SERVER=FALSE;DB_CLOSE_DELAY=-1";
        return DriverManager.getConnection(url, USER, PASSWORD);
    }

    static Connection openMemConnection(String dbName) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        return DriverManager.getConnection(url, USER, PASSWORD);
    }

    static void executeSql(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static int countRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    static void runLiquibase(Connection connection, String changelogPath) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase(changelogPath, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts());
    }

    static Path createTempDbPath(String prefix) throws Exception {
        Path dir = Files.createTempDirectory(prefix);
        return dir.resolve("bankdb");
    }

    static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(buildAesKey(), "AES"));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    static String decrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(buildAesKey(), "AES"));
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(value));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static byte[] buildAesKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8));
        return Arrays.copyOf(hash, 16);
    }

    private static String normalize(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
