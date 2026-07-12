package com.ghana.gwire.db;

import java.nio.file.Path;

/**
 * H2 connection settings. Default library path: {@code ~/.gwire/library}.
 */
public final class DatabaseConfig {

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public DatabaseConfig(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user == null ? "sa" : user;
        this.password = password == null ? "" : password;
    }

    /** File-backed library under the user home directory. */
    public static DatabaseConfig defaultLibrary() {
        Path dir = Path.of(System.getProperty("user.home"), ".gwire");
        // H2 file URL without extension; creates library.mv.db
        String url = "jdbc:h2:file:" + dir.resolve("library").toAbsolutePath() + ";AUTO_SERVER=TRUE";
        return new DatabaseConfig(url, "sa", "");
    }

    /** In-memory DB for unit tests. */
    public static DatabaseConfig inMemory(String name) {
        return new DatabaseConfig("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }
}
