package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.model.DashboardStats;
import com.spamfilter.adminclient.model.DayCount;
import com.spamfilter.adminclient.model.LogEntry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate queries backing the dashboard's KPI tiles, timeseries chart and
 * recent-activity feed. Read-only across subscribers/messages/calls/logs;
 * only reads blocklist/whitelist counts too (no writes from this class).
 */
public class DashboardRepository {

    private static final int TIMESERIES_DAYS = 14;

    private final DataSource dataSource;

    public DashboardRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    public DashboardStats load() {
        try (Connection con = connect()) {
            long active = scalar(con, "SELECT count(*) FROM subscribers WHERE status = 'ACTIVE'");
            long suspended = scalar(con, "SELECT count(*) FROM subscribers WHERE status = 'SUSPENDED'");
            long blocked = scalar(con, "SELECT count(*) FROM subscribers WHERE status = 'BLOCKED'");

            long messagesTotal = scalar(con, "SELECT count(*) FROM messages");
            long messagesToday = scalar(con, "SELECT count(*) FROM messages WHERE received_at >= current_date");
            long spamToday = scalar(con,
                    "SELECT count(*) FROM messages WHERE received_at >= current_date AND classification_label = 'spam'");
            long hamToday = scalar(con,
                    "SELECT count(*) FROM messages WHERE received_at >= current_date AND classification_label = 'ham'");
            long blockedMessagesToday = scalar(con,
                    "SELECT count(*) FROM messages WHERE received_at >= current_date AND status = 'BLOCKED'");

            long callsTotal = scalar(con, "SELECT count(*) FROM calls");
            long callsToday = scalar(con, "SELECT count(*) FROM calls WHERE started_at >= current_date");
            long blockedCallsToday = scalar(con,
                    "SELECT count(*) FROM calls WHERE started_at >= current_date AND status = 'BLOCKED'");

            long blocklistSize = scalar(con, "SELECT count(*) FROM blocklist");
            long whitelistSize = scalar(con, "SELECT count(*) FROM whitelisted_senders");

            long errors24h = scalar(con,
                    "SELECT count(*) FROM logs WHERE created_at >= now() - interval '24 hours' AND severity = 'ERROR'");
            long warnings24h = scalar(con,
                    "SELECT count(*) FROM logs WHERE created_at >= now() - interval '24 hours' AND severity = 'WARN'");

            List<DayCount> timeseries = loadTimeseries(con);
            List<LogEntry> recentLogs = loadRecentLogs(con);

            return new DashboardStats(active, suspended, blocked, messagesTotal, messagesToday, spamToday, hamToday,
                    blockedMessagesToday, callsTotal, callsToday, blockedCallsToday, blocklistSize, whitelistSize,
                    errors24h, warnings24h, timeseries, recentLogs);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load dashboard stats: " + e.getMessage(), e);
        }
    }

    private List<DayCount> loadTimeseries(Connection con) throws SQLException {
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = TIMESERIES_DAYS - 1; i >= 0; i--) {
            byDay.put(today.minusDays(i), new long[2]);
        }

        String sql = "SELECT date_trunc('day', received_at) AS day, classification_label, count(*) AS n "
                + "FROM messages WHERE received_at >= current_date - interval '" + (TIMESERIES_DAYS - 1) + " days' "
                + "GROUP BY day, classification_label";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LocalDate day = rs.getTimestamp("day").toLocalDateTime().toLocalDate();
                long[] counts = byDay.get(day);
                if (counts == null) {
                    continue;
                }
                if ("spam".equals(rs.getString("classification_label"))) {
                    counts[0] = rs.getLong("n");
                } else {
                    counts[1] = rs.getLong("n");
                }
            }
        }

        return byDay.entrySet().stream()
                .map(e -> new DayCount(e.getKey().toString(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private List<LogEntry> loadRecentLogs(Connection con) throws SQLException {
        String sql = "SELECT id, event_type, severity, message, related_message_id, related_call_id, created_at "
                + "FROM logs ORDER BY created_at DESC LIMIT 10";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<LogEntry> logs = new java.util.ArrayList<>();
            while (rs.next()) {
                logs.add(new LogEntry(
                        rs.getLong("id"),
                        rs.getString("event_type"),
                        rs.getString("severity"),
                        rs.getString("message"),
                        rs.getString("related_message_id"),
                        rs.getString("related_call_id"),
                        rs.getTimestamp("created_at").toInstant()));
            }
            return logs;
        }
    }

    private static long scalar(Connection con, String sql) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
