package com.ghana.gwire.db;

import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Facade over the H2 component catalogue: schema init, seed, and queries for UI.
 */
public final class ComponentLibraryService {

    private static final Logger log = LoggerFactory.getLogger(ComponentLibraryService.class);

    private final DatabaseManager db;
    private final ComponentRepository repository;
    private boolean initialized;

    public ComponentLibraryService() {
        this(new DatabaseManager(DatabaseConfig.defaultLibrary()));
    }

    public ComponentLibraryService(DatabaseManager db) {
        this.db = Objects.requireNonNull(db, "db");
        this.repository = new ComponentRepository(db);
    }

    public ComponentLibraryService(DatabaseConfig config) {
        this(new DatabaseManager(config));
    }

    /**
     * Opens DB, creates schema, and seeds the Ghana starter catalogue if empty.
     */
    public synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        try {
            db.initSchema();
            int count = repository.count();
            if (count == 0) {
                List<ElectricalComponent> seed = ComponentSeed.starterCatalogue();
                repository.insertAll(seed);
                log.info("Seeded component library with {} entries", seed.size());
            } else {
                log.info("Component library already has {} rows — skip seed", count);
            }
            initialized = true;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialise component library: " + e.getMessage(), e);
        }
    }

    public List<ElectricalComponent> listAll() {
        ensureInitialized();
        try {
            return repository.findAll();
        } catch (SQLException e) {
            throw new IllegalStateException("listAll failed: " + e.getMessage(), e);
        }
    }

    public List<ElectricalComponent> listByCategory(ComponentCategory category) {
        ensureInitialized();
        try {
            return repository.findByCategory(category);
        } catch (SQLException e) {
            throw new IllegalStateException("listByCategory failed: " + e.getMessage(), e);
        }
    }

    public Optional<ElectricalComponent> getById(String id) {
        ensureInitialized();
        try {
            return repository.findById(id);
        } catch (SQLException e) {
            throw new IllegalStateException("getById failed: " + e.getMessage(), e);
        }
    }

    public List<ElectricalComponent> search(String text) {
        ensureInitialized();
        try {
            return repository.search(text);
        } catch (SQLException e) {
            throw new IllegalStateException("search failed: " + e.getMessage(), e);
        }
    }

    public void updateCost(String id, double costGhs) {
        ensureInitialized();
        try {
            repository.updateCost(id, costGhs);
        } catch (SQLException e) {
            throw new IllegalStateException("updateCost failed: " + e.getMessage(), e);
        }
    }

    public int count() {
        ensureInitialized();
        try {
            return repository.count();
        } catch (SQLException e) {
            throw new IllegalStateException("count failed: " + e.getMessage(), e);
        }
    }

    public ComponentRepository repository() {
        ensureInitialized();
        return repository;
    }

    public DatabaseManager databaseManager() {
        return db;
    }

    public void close() {
        db.close();
        initialized = false;
    }
}
