package com.spamfilter.adminclient.db;

import com.spamfilter.adminclient.config.AdminConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Builds a pooled DataSource from a Neon-style connection string
 * (postgresql://user:password@host/db?params...). Never blocks or fails app
 * startup if Neon isn't reachable yet - connections are attempted lazily.
 */
public final class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private Database() {
    }

    public static DataSource connect(AdminConfig config) {
        String rawUrl = config.dbUrl();
        if (rawUrl == null || rawUrl.isBlank()) {
            log.warn("db.url is not set; the admin site will be unable to reach the database");
            return null;
        }

        try {
            URI uri = new URI(rawUrl);
            String[] userInfo = uri.getUserInfo().split(":", 2);
            String user = userInfo[0];
            String password = userInfo.length > 1 ? userInfo[1] : "";
            String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + uri.getPath() + query;

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(user);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(5);
            hikariConfig.setConnectionTimeout(5000);
            hikariConfig.setInitializationFailTimeout(-1);
            // Don't rely on the database's default search_path - Neon's SQL
            // editor can reset it, which breaks every unqualified table name.
            hikariConfig.setConnectionInitSql("SET search_path TO public");

            log.info("Connecting to Neon at {}{}", uri.getHost(), uri.getPath());
            return new HikariDataSource(hikariConfig);
        } catch (URISyntaxException | IllegalArgumentException e) {
            log.warn("Invalid db.url; the admin site will be unable to reach the database: {}", e.getMessage());
            return null;
        }
    }
}
