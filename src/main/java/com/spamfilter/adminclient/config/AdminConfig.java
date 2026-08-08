package com.spamfilter.adminclient.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads application.properties from the classpath; any key can be overridden
 * with a matching -Dkey=value system property, or an environment variable
 * (db.url -> DB_URL). Secrets like the Neon connection string should only
 * ever be supplied this way, never written into application.properties.
 */
public class AdminConfig {

    private final Properties props = new Properties();

    public AdminConfig() {
        this("application.properties");
    }

    public AdminConfig(String resourceName) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + resourceName + " on the classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resourceName, e);
        }
    }

    private String get(String key) {
        String systemProperty = System.getProperty(key);
        if (systemProperty != null) {
            return systemProperty;
        }
        String envVar = System.getenv(key.toUpperCase().replace('.', '_'));
        if (envVar != null) {
            return envVar;
        }
        return props.getProperty(key);
    }

    public int serverPort() {
        return Integer.parseInt(get("server.port"));
    }

    /**
     * Neon connection string, e.g.
     * postgresql://user:password@host/dbname?sslmode=require
     * Always supplied via -Ddb.url=... or the DB_URL env var - never
     * committed to application.properties.
     */
    public String dbUrl() {
        return get("db.url");
    }
}
