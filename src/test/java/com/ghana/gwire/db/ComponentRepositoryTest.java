package com.ghana.gwire.db;

import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentRepositoryTest {

    private DatabaseManager db;
    private ComponentRepository repository;
    private ComponentLibraryService library;

    @BeforeEach
    void setUp() throws SQLException {
        String name = "test_" + UUID.randomUUID().toString().replace("-", "");
        db = new DatabaseManager(DatabaseConfig.inMemory(name));
        db.initSchema();
        repository = new ComponentRepository(db);
        library = new ComponentLibraryService(db);
    }

    @AfterEach
    void tearDown() {
        if (library != null) {
            library.close();
        }
    }

    @Test
    void seedLoadsNonEmptyCatalog() {
        library.ensureInitialized();
        assertTrue(library.count() >= 40, "expected 40+ seed components, got " + library.count());
        List<ElectricalComponent> all = library.listAll();
        assertFalse(all.isEmpty());
        assertEquals(library.count(), all.size());
    }

    @Test
    void categoryFilterWorks() throws SQLException {
        library.ensureInitialized();
        List<ElectricalComponent> cables = repository.findByCategory(ComponentCategory.CABLE);
        assertFalse(cables.isEmpty());
        assertTrue(cables.stream().allMatch(c -> c.category() == ComponentCategory.CABLE));

        List<ElectricalComponent> mcbs = library.listByCategory(ComponentCategory.CIRCUIT_BREAKER);
        assertFalse(mcbs.isEmpty());
        assertTrue(mcbs.stream().allMatch(c -> c.category() == ComponentCategory.CIRCUIT_BREAKER));
    }

    @Test
    void findByIdAndSearch() {
        library.ensureInitialized();
        Optional<ElectricalComponent> cable = library.getById("CABLE-2.5-PVC");
        assertTrue(cable.isPresent());
        assertEquals("2.5mm²", cable.get().standardSize());

        List<ElectricalComponent> sockets = library.search("13A");
        assertFalse(sockets.isEmpty());
        assertTrue(sockets.stream().anyMatch(c -> c.id().contains("SOCK-13A")));
    }

    @Test
    void updateCost() throws SQLException {
        library.ensureInitialized();
        library.updateCost("CABLE-1.5-PVC", 9.99);
        Optional<ElectricalComponent> updated = repository.findById("CABLE-1.5-PVC");
        assertTrue(updated.isPresent());
        assertEquals(9.99, updated.get().unitCostGhs(), 1e-9);
    }

    @Test
    void insertCustomComponent() throws SQLException {
        db.initSchema();
        ElectricalComponent custom = ElectricalComponent.builder(
                        "CUSTOM-X", "Custom widget", ComponentCategory.OTHER, "other_custom")
                .unit("pcs")
                .unitCostGhs(1.0)
                .build();
        repository.insert(custom);
        assertEquals(1, repository.count());
        assertTrue(repository.findById("CUSTOM-X").isPresent());
    }

    @Test
    void ensureInitializedIsIdempotent() {
        library.ensureInitialized();
        int first = library.count();
        library.ensureInitialized();
        assertEquals(first, library.count());
    }
}
