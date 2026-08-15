package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.model.BlocklistEntry;
import com.spamfilter.adminclient.model.Page;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access to the blocklist table - numbers blocked outright, checked
 * before classification runs (owned jointly with the SMPP server).
 */
public class BlocklistRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;

    public BlocklistRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    public Page<BlocklistEntry> search(String query, int page, int pageSize) {
        String where = (query == null || query.isBlank()) ? "" : "WHERE msisdn ILIKE ? OR reason ILIKE ?";
        String listSql = "SELECT id, msisdn, reason, created_at, expires_at FROM blocklist " + where
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        String countSql = "SELECT count(*) FROM blocklist " + where;

        List<BlocklistEntry> items = new ArrayList<>();
        long total;
        try (Connection con = connect()) {
            try (PreparedStatement ps = con.prepareStatement(listSql)) {
                int i = 1;
                if (!where.isEmpty()) {
                    String like = "%" + query + "%";
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
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load blocklist: " + e.getMessage(), e);
        }
        return new Page<>(items, page, pageSize, total);
    }

    public void add(String msisdn, String reason, Instant expiresAt) {
        addAndRemoveOpposite(msisdn, reason, expiresAt);
    }

    public void addAndRemoveOpposite(String msisdn, String reason, Instant expiresAt) {
        String sql = "INSERT INTO blocklist (msisdn, reason, expires_at) VALUES (?, ?, ?)";
        try (Connection con = connect()) {
            try (PreparedStatement deleteWhite = con.prepareStatement("DELETE FROM whitelisted_senders WHERE sender_id = ?")) {
                deleteWhite.setString(1, msisdn);
                deleteWhite.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, msisdn);
                ps.setString(2, reason);
                ps.setTimestamp(3, expiresAt == null ? null : Timestamp.from(expiresAt));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new IllegalArgumentException("That number is already blocklisted");
            }
            throw new IllegalStateException("Could not add to blocklist: " + e.getMessage(), e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM blocklist WHERE id = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not remove from blocklist: " + e.getMessage(), e);
        }
    }

    public long count() {
        String sql = "SELECT count(*) FROM blocklist";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not count blocklist: " + e.getMessage(), e);
        }
    }

    private static BlocklistEntry map(ResultSet rs) throws SQLException {
        Timestamp expires = rs.getTimestamp("expires_at");
        return new BlocklistEntry(
                rs.getLong("id"),
                rs.getString("msisdn"),
                rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant(),
                expires == null ? null : expires.toInstant());
    }
}
