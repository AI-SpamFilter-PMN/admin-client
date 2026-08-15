package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.model.Page;
import com.spamfilter.adminclient.model.Subscriber;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC access to the subscribers table - the users/numbers on the private
 * network. This app reads it broadly and writes only to `status`
 * (suspend/activate/block a subscriber); everything else is owned by
 * whichever component registers subscribers.
 */
public class SubscriberRepository {

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "SUSPENDED", "BLOCKED");

    private final DataSource dataSource;

    public SubscriberRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    public Page<Subscriber> search(String status, String query, int page, int pageSize) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            clauses.add("status = ?");
            params.add(status);
        }
        if (query != null && !query.isBlank()) {
            clauses.add("(msisdn ILIKE ? OR display_name ILIKE ? OR imsi ILIKE ?)");
            String like = "%" + query + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        String where = clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);

        String listSql = "SELECT id, msisdn, imsi, display_name, status, created_at FROM subscribers " + where
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        String countSql = "SELECT count(*) FROM subscribers " + where;

        List<Subscriber> items = new ArrayList<>();
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
                        items.add(new Subscriber(
                                rs.getString("id"),
                                rs.getString("msisdn"),
                                rs.getString("imsi"),
                                rs.getString("display_name"),
                                rs.getString("status"),
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
            throw new IllegalStateException("Could not load subscribers: " + e.getMessage(), e);
        }
        return new Page<>(items, page, pageSize, total);
    }

    public void add(String msisdn, String imsi, String displayName, String status) {
        if (msisdn == null || msisdn.isBlank()) {
            throw new IllegalArgumentException("msisdn is required");
        }
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }

        String sql = "INSERT INTO subscribers (id, msisdn, imsi, display_name, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, msisdn.trim());
            ps.setString(3, imsi == null || imsi.isBlank() ? null : imsi.trim());
            ps.setString(4, displayName == null || displayName.isBlank() ? null : displayName.trim());
            ps.setString(5, status);
            int inserted = ps.executeUpdate();
            if (inserted == 0) {
                throw new IllegalStateException("Subscriber insert did not affect any row");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not add subscriber: " + e.getMessage(), e);
        }
    }

    public void deleteById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }

        String sql = "DELETE FROM subscribers WHERE id = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(id));
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                throw new IllegalArgumentException("No such subscriber");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid subscriber id: " + id, e);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not remove subscriber: " + e.getMessage(), e);
        }
    }

    public void deleteByMsisdn(String msisdn) {
        if (msisdn == null || msisdn.isBlank()) {
            throw new IllegalArgumentException("msisdn is required");
        }

        String sql = "DELETE FROM subscribers WHERE msisdn = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, msisdn.trim());
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                throw new IllegalArgumentException("No such subscriber");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not remove subscriber: " + e.getMessage(), e);
        }
    }

    public void updateStatus(String id, String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
        String sql = "UPDATE subscribers SET status = ? WHERE id = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setObject(2, id, java.sql.Types.OTHER);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("No such subscriber");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update subscriber status: " + e.getMessage(), e);
        }
    }

    public long countByStatus(String status) {
        String sql = "SELECT count(*) FROM subscribers WHERE status = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not count subscribers: " + e.getMessage(), e);
        }
    }
}
