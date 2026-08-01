package com.beefit.api.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgreSqlTestSupport {

    private static final String IMAGE = "postgres:16-alpine";
    private static final String USERNAME = "beefit";
    private static final String PASSWORD = "beefit";

    private PostgreSqlTestSupport() {
    }

    public static PostgreSQLContainer createContainer(String databaseName) {
        return new PostgreSQLContainer(IMAGE)
                .withDatabaseName(databaseName)
                .withUsername(USERNAME)
                .withPassword(PASSWORD);
    }

    public static void registerDataSourceProperties(
            DynamicPropertyRegistry registry,
            PostgreSQLContainer postgres) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }
}
