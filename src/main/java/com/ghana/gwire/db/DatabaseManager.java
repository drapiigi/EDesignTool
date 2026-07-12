package com.ghana.gwire.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Owns the H2 connection lifecycle and component schema.
 */
public final class DatabaseManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private static final String SCHEMA_SQL = """
            CREATE TABLE IF NOT EXISTS components (
              id VARCHAR(64) PRIMARY KEY,
              name VARCHAR(255) NOT NULL,
              category VARCHAR(64) NOT NULL,
              description VARCHAR(1024),
              ghana_reference VARCHAR(255),
              standard_size VARCHAR(64),
              unit VARCHAR(32) NOT NULL,
              unit_cost_ghs DOUBLE NOT NULL,
              symbol_key VARCHAR(64) NOT NULL,
              current_rating_a DOUBLE,
              voltage_rating_v DOUBLE,
              poles INT,
              cross_section_mm2 DOUBLE,
              resistance_ohm_per_km DOUBLE,
              voltage_drop_mv_per_am DOUBLE,
              power_w DOUBLE,
              notes VARCHAR(1024),
              active BOOLEAN DEFAULT TRUE
            )
            """;

    private final DatabaseConfig config;
    private Connection connection;

    public DatabaseManager(DatabaseConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password());
            log.debug("Opened H2 connection: {}", config.jdbcUrl());
        }
        return connection;
    }

    public void initSchema() throws SQLException {
        try (Statement st = getConnection().createStatement()) {
            st.execute(SCHEMA_SQL);
        }
        log.info("Component schema ready");
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn("Error closing DB connection: {}", e.getMessage());
            }
            connection = null;
        }
    }
}
