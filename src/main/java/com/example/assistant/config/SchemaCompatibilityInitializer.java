package com.example.assistant.config;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class SchemaCompatibilityInitializer {

    private final DataSource dataSource;

    public SchemaCompatibilityInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrateH2EnumColumns() {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("h2")) {
                return;
            }
            statement.execute("alter table if exists user_activity alter column type varchar(32)");
            statement.execute("alter table if exists user_activity alter column text clob");
            statement.execute("alter table if exists user_activity alter column raw_evidence clob");
        } catch (Exception ignored) {
            // Best-effort compatibility migration for old local H2 databases.
        }
    }
}
