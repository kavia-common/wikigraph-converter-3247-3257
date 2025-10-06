package com.example.backend.config;

import jakarta.annotation.PreDestroy;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j configuration that exposes an org.neo4j.driver.Driver bean configured
 * from application properties:
 *   - neo4j.uri
 *   - neo4j.username
 *   - neo4j.password
 *
 * The driver is created once and closed gracefully on application shutdown.
 */
@Configuration
public class Neo4jConfig {

    @Value("${neo4j.uri}")
    private String uri;

    @Value("${neo4j.username}")
    private String username;

    @Value("${neo4j.password}")
    private String password;

    private Driver driver;

    // PUBLIC_INTERFACE
    @Bean
    public Driver neo4jDriver() {
        // Create the driver using the configured URI and basic authentication
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
        return this.driver;
    }

    /**
     * Ensure the driver is closed gracefully during application shutdown.
     */
    @PreDestroy
    public void closeDriver() {
        if (this.driver != null) {
            try {
                this.driver.close();
            } catch (Exception ignored) {
                // Swallow exceptions during shutdown to avoid noisy logs on exit
            }
        }
    }
}
