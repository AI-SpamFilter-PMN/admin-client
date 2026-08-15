package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.model.LogEntry;
import com.spamfilter.adminclient.model.Page;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only JDBC access to Neon's logs table - the system/event log every
 * other component writes to. This app never writes to this table.
 */
public class LogRepository {

    private final DataSource dataSource;

    public LogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    public Page<LogEntry> search(String severity, String query, int page, int pageSize) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (severity != null && !severity.isBlank() && !"all".equalsIgnoreCase(severity)) {
            clauses.add("l.severity = ?");
            params.add(severity);
        }
        if (query != null && !query.isBlank()) {
            clauses.add("(l.event_type ILIKE ? OR l.message ILIKE ? OR COALESCE(m.source, '') ILIKE ? OR COALESCE(m.destination, '') ILIKE ? OR COALESCE(c.source, '') ILIKE ? OR COALESCE(c.destination, '') ILIKE ?)");
            String like = "%" + query + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        String where = clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);

        String listSql = "SELECT l.id, l.event_type, l.severity, l.message, l.related_message_id, l.related_call_id, "
                + "COALESCE(m.source, c.source) AS source_number, COALESCE(m.destination, c.destination) AS destination_number, "
                + "l.created_at FROM logs l LEFT JOIN messages m ON m.id = l.related_message_id "
                + "LEFT JOIN calls c ON c.id = l.related_call_id " + where
                + " ORDER BY l.created_at DESC LIMIT ? OFFSET ?";
        String countSql = "SELECT count(*) FROM logs l LEFT JOIN messages m ON m.id = l.related_message_id "
                + "LEFT JOIN calls c ON c.id = l.related_call_id " + where;

        List<LogEntry> items = new ArrayList<>();
        long total;
        try (Connection con = connect()) {
            try (PreparedStatement ps = con.prepareStatement(listSql)) {
                int i = 1;
                for (Object param : params) {
                    ps.setObject(i++, param);
                }
                ps.setInt(i++, pageSize);
                ps.setInt(i, Math.max(0, page - 1) * pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new LogEntry(
                                rs.getLong("id"),
                                rs.getString("event_type"),
                                rs.getString("severity"),
                                rs.getString("message"),
                                rs.getString("related_message_id"),
                                rs.getString("related_call_id"),
                                rs.getString("source_number"),
                                rs.getString("destination_number"),
                                rs.getTimestamp("created_at").toInstant()));
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(countSql)) {
                int i = 1;
                for (Object param : params) {
                    ps.setObject(i++, param);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load logs: " + e.getMessage(), e);
        }
        return new Page<>(items, page, pageSize, total);
    }
}
