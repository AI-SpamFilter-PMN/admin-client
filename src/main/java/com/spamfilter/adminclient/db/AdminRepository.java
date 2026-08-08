package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.model.Admin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/**
 * JDBC access to the shared users table for admin login. There's no
 * separate admins table: access is gated by users.role, which sms-client's
 * own account system already models (ROLE_SUPPORT/ROLE_ESCALATION/ROLE_ADMIN).
 * Passwords are plaintext here for the same reason sms-client's are - this
 * is literally the same column, not a copy.
 */
public class AdminRepository {

    private static final Set<String> ADMIN_ROLES = Set.of("ROLE_ADMIN", "ROLE_ESCALATION");

    private final DataSource dataSource;

    public AdminRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    /** Returns null for a wrong password, an unknown email, or a role without admin access. */
    public Admin authenticate(String email, String password) {
        String sql = "SELECT id, email, display_name, password, role FROM users WHERE email = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                if (!password.equals(rs.getString("password"))) {
                    return null;
                }
                String role = rs.getString("role");
                if (!ADMIN_ROLES.contains(role)) {
                    return null;
                }
                return new Admin(rs.getString("id"), rs.getString("email"), rs.getString("display_name"), role);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not verify admin account: " + e.getMessage(), e);
        }
    }
}
