package com.iras;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationTest {
    static HashMap<String, String> configuration() throws Exception {
        var values = new HashMap<String, String>();
        Path path = Path.of(".env.real");
        if (Files.exists(path)) {
            for (String line : Files.readAllLines(path)) {
                if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
                int separator = line.indexOf('=');
                if (separator < 1) throw new IllegalArgumentException("Invalid local configuration");
                String value = line.substring(separator + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(line.substring(0, separator), value);
            }
        }
        System.getenv().forEach((key, value) -> { if (!value.isBlank()) values.put(key, value); });
        for (String key : new String[]{"DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD"}) {
            if (values.getOrDefault(key, "").isBlank()) throw new IllegalStateException("Missing " + key);
        }
        return values;
    }

    @Test
    void migrationsAreRepeatableAndAccountsAreUnique() throws Exception {
        var config = configuration();
        String schema = "iras_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(config.get("DATABASE_URL"),
                config.get("DATABASE_USER"), config.get("DATABASE_PASSWORD"))) {
            try {
                var flyway = Flyway.configure().dataSource(config.get("DATABASE_URL"),
                        config.get("DATABASE_USER"), config.get("DATABASE_PASSWORD"))
                        .schemas(schema).defaultSchema(schema).load();
                assertThat(flyway.migrate().migrationsExecuted).isPositive();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                try (var statement = connection.createStatement()) {
                    statement.execute("SET search_path TO " + schema + ", public");
                    statement.execute("INSERT INTO accounts(account,password_hash,role) VALUES ('synthetic','hash','USER')");
                    try (var rows = statement.executeQuery("SELECT count(*) FROM accounts")) {
                        rows.next();
                        assertThat(rows.getInt(1)).isEqualTo(1);
                    }
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.execute(
                            "INSERT INTO accounts(account,password_hash,role) VALUES ('synthetic','hash','USER')"))
                            .isInstanceOf(java.sql.SQLException.class);
                }
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        }
    }
}