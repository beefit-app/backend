package com.beefit.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.beefit.api.support.PostgreSqlTestSupport;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport.createContainer("beefit_test");

    @Test
    void appliesInitialSchemaToAnEmptyPostgreSqlDatabase() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertEquals(5, flyway.migrate().migrationsExecuted);

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("select count(*) from information_schema.tables where table_name = 'users'")) {
            result.next();
            assertEquals(1, result.getInt(1));
        }
    }
}
