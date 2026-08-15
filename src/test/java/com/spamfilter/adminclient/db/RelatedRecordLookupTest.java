package com.spamfilter.adminclient.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RelatedRecordLookupTest {

    @Test
    void findsMostRecentMessageAndCallForBlockedMsisdn() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:related-records;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");

        try (HikariDataSource ds = new HikariDataSource(config)) {
            try (Connection c = ds.getConnection()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "CREATE TABLE messages (id VARCHAR(255) PRIMARY KEY, source VARCHAR(255), destination VARCHAR(255), classification_label VARCHAR(64), classification_score DOUBLE, status VARCHAR(64), smpp_message_id VARCHAR(255), sms_body TEXT, received_at TIMESTAMP)")) {
                    ps.executeUpdate();
                }
                try (PreparedStatement ps2 = c.prepareStatement(
                        "CREATE TABLE calls (id VARCHAR(255) PRIMARY KEY, source VARCHAR(255), destination VARCHAR(255), started_at TIMESTAMP, ended_at TIMESTAMP, classification_label VARCHAR(64), classification_score DOUBLE, status VARCHAR(64), transcript TEXT)")) {
                    ps2.executeUpdate();
                }
            }

            try (Connection c = ds.getConnection(); PreparedStatement insertMessage = c.prepareStatement(
                    "INSERT INTO messages (id, source, destination, classification_label, classification_score, status, smpp_message_id, sms_body, received_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)");
                 PreparedStatement insertCall = c.prepareStatement(
                    "INSERT INTO calls (id, source, destination, started_at, ended_at, classification_label, classification_score, status, transcript) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, ?)");
            ) {
                insertMessage.setString(1, "msg-1");
                insertMessage.setString(2, "+123");
                insertMessage.setString(3, "+999");
                insertMessage.setString(4, "spam");
                insertMessage.setDouble(5, 0.99);
                insertMessage.setString(6, "BLOCKED");
                insertMessage.setString(7, "sms-1");
                insertMessage.setString(8, "This is a blocked spam message");
                insertMessage.executeUpdate();

                insertCall.setString(1, "call-1");
                insertCall.setString(2, "+123");
                insertCall.setString(3, "+999");
                insertCall.setString(4, "spam");
                insertCall.setDouble(5, 0.82);
                insertCall.setString(6, "BLOCKED");
                insertCall.setString(7, "Call transcript");
                insertCall.executeUpdate();
            }

            MessageRepository messageRepository = new MessageRepository(ds);
            CallRepository callRepository = new CallRepository(ds);

            Map<String, Object> messageDetail = messageRepository.findLatestByMsisdn("+123");
            assertNotNull(messageDetail);
            assertEquals("msg-1", messageDetail.get("id"));

            Map<String, Object> callDetail = callRepository.findLatestByMsisdn("+123");
            assertNotNull(callDetail);
            assertEquals("call-1", callDetail.get("id"));
        }
    }
}
