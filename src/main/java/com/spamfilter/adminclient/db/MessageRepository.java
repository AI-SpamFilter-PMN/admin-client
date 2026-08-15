package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.model.MessageRow;
import com.spamfilter.adminclient.model.Page;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only JDBC access to Neon's messages table, across every subscriber
 * (unlike sms-client's UserRepository.historyForUser, which scopes to one
 * user's own numbers). This app never writes to this table.
 */
public class MessageRepository {

    private final DataSource dataSource;

    public MessageRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    public Page<MessageRow> search(String label, String status, String query, int page, int pageSize) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (label != null && !label.isBlank() && !"all".equalsIgnoreCase(label)) {
            clauses.add("classification_label = ?");
            params.add(label);
        }
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            clauses.add("status = ?");
            params.add(status);
        }
        if (query != null && !query.isBlank()) {
            clauses.add("(source ILIKE ? OR destination ILIKE ?)");
            String like = "%" + query + "%";
            params.add(like);
            params.add(like);
        }
        String where = clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);

        String listSql = "SELECT id, source, destination, classification_label, classification_score, "
                + "status, smpp_message_id, sms_body, received_at, "
                + "EXISTS (SELECT 1 FROM blocklist b WHERE b.msisdn = messages.source) AS is_blacklisted "
                + "FROM messages " + where + " ORDER BY received_at DESC LIMIT ? OFFSET ?";
        String countSql = "SELECT count(*) FROM messages " + where;

        List<MessageRow> items = new ArrayList<>();
        long total;
        try (Connection con = connect()) {
            try (PreparedStatement ps = con.prepareStatement(listSql)) {
                int i = bindAll(ps, params);
                ps.setInt(i++, pageSize);
                ps.setInt(i, Math.max(0, page - 1) * pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new MessageRow(
                                rs.getString("id"),
                                rs.getString("source"),
                                rs.getString("destination"),
                                rs.getString("classification_label"),
                                rs.getDouble("classification_score"),
                                rs.getString("status"),
                                rs.getString("smpp_message_id"),
                                rs.getString("sms_body"),
                                rs.getBoolean("is_blacklisted"),
                                rs.getTimestamp("received_at").toInstant()));
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(countSql)) {
                bindAll(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load messages: " + e.getMessage(), e);
        }
        return new Page<>(items, page, pageSize, total);
    }

    private static int bindAll(PreparedStatement ps, List<Object> params) throws SQLException {
        int i = 1;
        for (Object param : params) {
            ps.setObject(i++, param);
        }
        return i;
    }
}
