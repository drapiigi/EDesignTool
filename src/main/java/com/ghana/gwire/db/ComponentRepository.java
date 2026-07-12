package com.ghana.gwire.db;

import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC access for the components catalogue table.
 */
public final class ComponentRepository {

    private static final Logger log = LoggerFactory.getLogger(ComponentRepository.class);

    private static final String COLUMNS = """
            id, name, category, description, ghana_reference, standard_size, unit,
            unit_cost_ghs, symbol_key, current_rating_a, voltage_rating_v, poles,
            cross_section_mm2, resistance_ohm_per_km, voltage_drop_mv_per_am,
            power_w, notes, active
            """;

    private final DatabaseManager db;

    public ComponentRepository(DatabaseManager db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public List<ElectricalComponent> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM components WHERE active = TRUE ORDER BY category, name";
        return queryList(sql);
    }

    public List<ElectricalComponent> findByCategory(ComponentCategory category) throws SQLException {
        Objects.requireNonNull(category, "category");
        String sql = "SELECT " + COLUMNS + " FROM components WHERE active = TRUE AND category = ? ORDER BY name";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, category.name());
            return mapAll(ps.executeQuery());
        }
    }

    public Optional<ElectricalComponent> findById(String id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM components WHERE id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Case-insensitive search across id, name, description, standard size, and symbol key.
     */
    public List<ElectricalComponent> search(String text) throws SQLException {
        if (text == null || text.isBlank()) {
            return findAll();
        }
        String pattern = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
        String sql = "SELECT " + COLUMNS + """
                 FROM components
                 WHERE active = TRUE
                   AND (
                     LOWER(id) LIKE ?
                     OR LOWER(name) LIKE ?
                     OR LOWER(COALESCE(description, '')) LIKE ?
                     OR LOWER(COALESCE(standard_size, '')) LIKE ?
                     OR LOWER(symbol_key) LIKE ?
                   )
                 ORDER BY category, name
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) {
                ps.setString(i, pattern);
            }
            return mapAll(ps.executeQuery());
        }
    }

    public void updateCost(String id, double costGhs) throws SQLException {
        String sql = "UPDATE components SET unit_cost_ghs = ? WHERE id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setDouble(1, costGhs);
            ps.setString(2, id);
            int n = ps.executeUpdate();
            if (n == 0) {
                log.warn("updateCost: no row for id={}", id);
            }
        }
    }

    public void insert(ElectricalComponent c) throws SQLException {
        String sql = """
                INSERT INTO components (
                  id, name, category, description, ghana_reference, standard_size, unit,
                  unit_cost_ghs, symbol_key, current_rating_a, voltage_rating_v, poles,
                  cross_section_mm2, resistance_ohm_per_km, voltage_drop_mv_per_am,
                  power_w, notes, active
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            bindComponent(ps, c);
            ps.executeUpdate();
        }
    }

    public void insertAll(List<ElectricalComponent> components) throws SQLException {
        Connection conn = db.getConnection();
        boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (ElectricalComponent c : components) {
                insert(c);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    public int count() throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT COUNT(*) FROM components");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private List<ElectricalComponent> queryList(String sql) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapAll(rs);
        }
    }

    private static List<ElectricalComponent> mapAll(ResultSet rs) throws SQLException {
        List<ElectricalComponent> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    private static ElectricalComponent mapRow(ResultSet rs) throws SQLException {
        return ElectricalComponent.builder(
                        rs.getString("id"),
                        rs.getString("name"),
                        ComponentCategory.valueOf(rs.getString("category")),
                        rs.getString("symbol_key")
                )
                .description(rs.getString("description"))
                .ghanaReference(rs.getString("ghana_reference"))
                .standardSize(rs.getString("standard_size"))
                .unit(rs.getString("unit"))
                .unitCostGhs(rs.getDouble("unit_cost_ghs"))
                .currentRatingA(getDoubleObj(rs, "current_rating_a"))
                .voltageRatingV(getDoubleObj(rs, "voltage_rating_v"))
                .poles(getIntObj(rs, "poles"))
                .crossSectionMm2(getDoubleObj(rs, "cross_section_mm2"))
                .resistanceOhmPerKm(getDoubleObj(rs, "resistance_ohm_per_km"))
                .voltageDropMvPerAm(getDoubleObj(rs, "voltage_drop_mv_per_am"))
                .powerW(getDoubleObj(rs, "power_w"))
                .notes(rs.getString("notes"))
                .active(rs.getBoolean("active"))
                .build();
    }

    private static void bindComponent(PreparedStatement ps, ElectricalComponent c) throws SQLException {
        ps.setString(1, c.id());
        ps.setString(2, c.name());
        ps.setString(3, c.category().name());
        ps.setString(4, c.description());
        ps.setString(5, c.ghanaReference());
        ps.setString(6, c.standardSize());
        ps.setString(7, c.unit());
        ps.setDouble(8, c.unitCostGhs());
        ps.setString(9, c.symbolKey());
        setDoubleObj(ps, 10, c.currentRatingA());
        setDoubleObj(ps, 11, c.voltageRatingV());
        if (c.poles() == null) {
            ps.setNull(12, Types.INTEGER);
        } else {
            ps.setInt(12, c.poles());
        }
        setDoubleObj(ps, 13, c.crossSectionMm2());
        setDoubleObj(ps, 14, c.resistanceOhmPerKm());
        setDoubleObj(ps, 15, c.voltageDropMvPerAm());
        setDoubleObj(ps, 16, c.powerW());
        ps.setString(17, c.notes());
        ps.setBoolean(18, c.active());
    }

    private static Double getDoubleObj(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private static Integer getIntObj(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static void setDoubleObj(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.DOUBLE);
        } else {
            ps.setDouble(idx, value);
        }
    }
}
