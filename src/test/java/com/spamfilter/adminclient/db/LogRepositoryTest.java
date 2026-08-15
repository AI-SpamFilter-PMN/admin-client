package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.model.LogEntry;
import com.spamfilter.adminclient.model.Page;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogRepositoryTest {

    @Test
    void searchMatchesRelatedSourceAndDestinationNumbers() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:logs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");

        try (HikariDataSource ds = new HikariDataSource(config);
             Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE messages (id VARCHAR(255) PRIMARY KEY, source VARCHAR(255), destination VARCHAR(255), received_at TIMESTAMP)");
                 PreparedStatement ps2 = c.prepareStatement(
                    "CREATE TABLE calls (id VARCHAR(255) PRIMARY KEY, source VARCHAR(255), destination VARCHAR(255), started_at TIMESTAMP, ended_at TIMESTAMP)");
                 PreparedStatement ps3 = c.prepareStatement(
                    "CREATE TABLE logs (id BIGINT PRIMARY KEY, event_type VARCHAR(255), severity VARCHAR(32), message VARCHAR(1024), related_message_id VARCHAR(255), related_call_id VARCHAR(255), created_at TIMESTAMP)") ) {
                ps.executeUpdate();
                ps2.executeUpdate();
                ps3.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO messages (id, source, destination, received_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
                ps.setString(1, "msg-1");
                ps.setString(2, "2000");
                ps.setString(3, "3000");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO logs (id, event_type, severity, message, related_message_id, related_call_id, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)")) {
                ps.setLong(1, 1L);
                ps.setString(2, "sms.sent");
                ps.setString(3, "INFO");
                ps.setString(4, "Message delivered");
                ps.setString(5, "msg-1");
                ps.setString(6, null);
                ps.executeUpdate();
            }

            LogRepository repository = new LogRepository(ds);
            Page<LogEntry> page = repository.search("all", "3000", 1, 10);

            assertEquals(1, page.getTotalItems());
            assertEquals("2000", page.getItems().get(0).getSourceNumber());
            assertEquals("3000", page.getItems().get(0).getDestinationNumber());
        }
    }
}
