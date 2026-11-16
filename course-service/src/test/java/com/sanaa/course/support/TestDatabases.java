package com.sanaa.course.support;

import org.testcontainers.mysql.MySQLContainer;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Testcontainers wiring shared by the end-to-end suites. */
public final class TestDatabases {

    private TestDatabases() {
    }

    public static MySQLContainer mysql(String database) {
        return new MySQLContainer("mysql:8.4")
                .withDatabaseName(database)
                .withUsername("test")
                .withPassword("test");
    }

    /**
     * Registers the container. SSL is disabled only because the container certificate can be ahead
     * of the host clock on Windows, which makes the driver reject the handshake.
     */
    public static void register(DynamicPropertyRegistry registry, MySQLContainer mysql) {
        registry.add("spring.datasource.url",
                () -> mysql.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("management.server.port", () -> "0");
    }

    /** Keeps the production decoder lazy: no network call during startup. */
    public static void registerJwtFallback(DynamicPropertyRegistry registry) {
        registry.add("sanaa.jwt.jwks-uri", () -> "http://127.0.0.1:1/.well-known/jwks.json");
    }
}
