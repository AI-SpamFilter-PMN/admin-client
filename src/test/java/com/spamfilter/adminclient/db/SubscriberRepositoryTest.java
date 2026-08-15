package com.spamfilter.adminclient.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SubscriberRepositoryTest {

    @Test
    void addAndDeleteSubscriberWorks() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:subscribers;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        try (HikariDataSource ds = new HikariDataSource(config)) {
            try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE subscribers (id UUID PRIMARY KEY, msisdn VARCHAR(255), imsi VARCHAR(255), display_name VARCHAR(255), status VARCHAR(32), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")) {
                ps.executeUpdate();
            }

            SubscriberRepository repository = new SubscriberRepository(ds);

            assertDoesNotThrow(() -> repository.add("+1234567890", "001010123456789", "Test User", "ACTIVE"));
            assertDoesNotThrow(() -> repository.deleteByMsisdn("+1234567890"));
        }
    }
}
