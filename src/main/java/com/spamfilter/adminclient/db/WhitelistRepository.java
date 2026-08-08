package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.model.Page;
import com.spamfilter.adminclient.model.WhitelistedSender;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access to the shared whitelisted_senders table - trusted senders,
 * added by an admin (added_by) or the system. No expiry, unlike blocklist.
 */
public class WhitelistRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;

    public WhitelistRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    public Page<WhitelistedSender> search(String query, int page, int pageSize) {
        String where = (query == null || query.isBlank())
                ? "" : "WHERE sender_id ILIKE ? OR alias_name ILIKE ? OR description ILIKE ?";
        String listSql = "SELECT id, sender_id, alias_name, description, added_by, created_at "
                + "FROM whitelisted_senders " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        String countSql = "SELECT count(*) FROM whitelisted_senders " + where;

        List<WhitelistedSender> items = new ArrayList<>();
        long total;
        try (Connection con = connect()) {
            try (PreparedStatement ps = con.prepareStatement(listSql)) {
                int i = 1;
                if (!where.isEmpty()) {
                    String like = "%" + query + "%";
                    ps.setString(i++, like);
                    ps.setString(i++, like);
                    ps.setString(i++, like);
                }
                ps.setInt(i++, pageSize);
                ps.setInt(i, Math.max(0, page - 1) * pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(map(rs));
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(countSql)) {
                if (!where.isEmpty()) {
                    String like = "%" + query + "%";
                    ps.setString(1, like);
                    ps.setString(2, like);
                    ps.setString(3, like);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load whitelist: " + e.getMessage(), e);
        }
        return new Page<>(items, page, pageSize, total);
    }

    public void add(String senderId, String aliasName, String description, String addedBy) {
        String sql = "INSERT INTO whitelisted_senders (sender_id, alias_name, description, added_by) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, senderId);
            ps.setString(2, aliasName);
            ps.setString(3, description);
            ps.setString(4, addedBy);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new IllegalArgumentException("That sender is already whitelisted");
            }
            throw new IllegalStateException("Could not add to whitelist: " + e.getMessage(), e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM whitelisted_senders WHERE id = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not remove from whitelist: " + e.getMessage(), e);
        }
    }

    public long count() {
        String sql = "SELECT count(*) FROM whitelisted_senders";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not count whitelist: " + e.getMessage(), e);
        }
    }

    private static WhitelistedSender map(ResultSet rs) throws SQLException {
        return new WhitelistedSender(
                rs.getLong("id"),
                rs.getString("sender_id"),
                rs.getString("alias_name"),
                rs.getString("description"),
                rs.getString("added_by"),
                rs.getTimestamp("created_at").toInstant());
    }
}
