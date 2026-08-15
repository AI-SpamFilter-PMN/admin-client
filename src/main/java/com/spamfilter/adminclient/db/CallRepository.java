package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.model.CallRow;
import com.spamfilter.adminclient.model.Page;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only JDBC access to Neon's calls table, across every subscriber. This
 * app never writes to this table - owned by the SIP client.
 */
public class CallRepository {

    private final DataSource dataSource;

    public CallRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    public Page<CallRow> search(String status, String query, int page, int pageSize) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

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

        String listSql = "SELECT id, source, destination, started_at, ended_at, classification_label, "
                + "classification_score, status, transcript FROM calls " + where
                + " ORDER BY started_at DESC LIMIT ? OFFSET ?";
        String countSql = "SELECT count(*) FROM calls " + where;

        List<CallRow> items = new ArrayList<>();
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
                        Timestamp ended = rs.getTimestamp("ended_at");
                        double score = rs.getDouble("classification_score");
                        items.add(new CallRow(
                                rs.getString("id"),
                                rs.getString("source"),
                                rs.getString("destination"),
                                rs.getTimestamp("started_at").toInstant(),
                                ended == null ? null : ended.toInstant(),
                                rs.getString("classification_label"),
                                rs.wasNull() ? null : score,
                                rs.getString("status"),
                                rs.getString("transcript")));
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
            throw new IllegalStateException("Could not load calls: " + e.getMessage(), e);
        }
        return new Page<>(items, page, pageSize, total);
    }
}
