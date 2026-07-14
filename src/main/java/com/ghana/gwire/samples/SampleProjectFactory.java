package com.ghana.gwire.samples;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.OpeningType;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.domain.project.ProjectSettings;

/**
 * Builds sample Ghanaian residential projects for demos, packaging, and Phase 15 templates.
 *
 * <p>Layouts cover a 1-bed bungalow, a 2-storey house, and the classic three-bedroom
 * bungalow (~12 m × 10 m) typical of common Ghana housing stock for L.I. 2008 domestic
 * wiring exercises.
 */
public final class SampleProjectFactory {

    public static final String SAMPLE_NAME = "Sample 3-Bed Ghana Bungalow";
    public static final String ONE_BED_NAME = "Sample 1-Bed Ghana Bungalow";
    public static final String TWO_STOREY_NAME = "Sample 2-Storey Ghana House";
    public static final String RESOURCE_PATH = "/samples/ghana-3bed-house.gwire";

    private SampleProjectFactory() {
    }

    /**
     * Compact single-storey 1-bedroom bungalow: Living, Kitchen, Bedroom, Bathroom,
     * and a small front veranda.
     */
    public static Project createOneBedBungalow() {
        Project project = new Project(ONE_BED_NAME);
        project.settings().setHouseType("1-bedroom bungalow (Ghana residential)");
        project.settings().setSupplyType(ProjectSettings.SupplyType.SINGLE_PHASE_230V);
        project.floorPlan().setGridMm(500);
        project.floorPlan().setSnapToGrid(true);

        // Compact plan (~8 m × 7 m) — origin bottom-left of plot
        Room living = room("Living", 0, 0, 4000, 3500);
        Room kitchen = room("Kitchen", 4000, 0, 3000, 2500);
        Room bedroom = room("Bedroom", 0, 3500, 4000, 3000);
        Room bath = room("Bathroom", 4000, 2500, 2500, 2000);
        Room veranda = room("Veranda", 4000, 4500, 3000, 1500);

        for (Room r : new Room[]{living, kitchen, bedroom, bath, veranda}) {
            project.floorPlan().addRoom(r);
            addRectWalls(project, r);
        }

        addDoor(project, living, 0.2, true);
        addDoor(project, kitchen, 0.4, false);
        addDoor(project, bedroom, 0.3, false);
        addDoor(project, bath, 0.5, false);

        // Living
        light(project, living, 0.5, 0.5);
        socket2(project, living, 0.15, 0.15);
        socket2(project, living, 0.85, 0.15);
        socket2(project, living, 0.15, 0.85);
        switch1(project, living, 0.08, 0.15);

        // Kitchen
        light(project, kitchen, 0.5, 0.5);
        socket2(project, kitchen, 0.2, 0.15);
        socket2(project, kitchen, 0.8, 0.15);
        device(project, kitchen, "SW-45A-DP", "switch_45a_dp", "Cooker switch", 0.85, 0.85);
        switch1(project, kitchen, 0.1, 0.2);

        // Bedroom
        light(project, bedroom, 0.5, 0.5);
        socket2(project, bedroom, 0.15, 0.15);
        socket2(project, bedroom, 0.85, 0.15);
        switch1(project, bedroom, 0.1, 0.2);

        // Bathroom
        device(project, bath, "LIGHT-BULKHEAD", "light_bulkhead", "Bulkhead light", 0.5, 0.5);
        socket2(project, bath, 0.2, 0.15);
        switch1(project, bath, 0.1, 0.2);

        // Veranda
        light(project, veranda, 0.5, 0.5);
        switch1(project, veranda, 0.1, 0.3);

        // Global protection (service intake proxy)
        device(project, null, "DB-8WAY", "db_8way", "Main consumer unit", 7500, 1500);
        device(project, null, "RCCB-63-30", "rccb_30ma", "Main RCCB 63A 30mA", 7500, 2000);
        device(project, null, "EARTH-ROD-16", "earth_rod", "Earth rod", 200, -800);
        device(project, null, "EARTH-CLAMP", "earth_clamp", "Earth clamp", 400, -800);

        return project;
    }

    /**
     * Two-storey Ghana residential house. Ground: Living, Dining, Kitchen.
     * First floor: Master, Bedroom 2, Bathroom. Protection devices on ground only.
     */
    public static Project createTwoStoreyHouse() {
        Project project = new Project(TWO_STOREY_NAME);
        project.settings().setHouseType("2-storey house (Ghana residential)");
        project.settings().setSupplyType(ProjectSettings.SupplyType.SINGLE_PHASE_230V);
        project.floorPlan().setGridMm(500);
        project.floorPlan().setSnapToGrid(true);

        // —— Ground floor ——
        Room living = room("Living", 0, 0, 4500, 4000);
        Room dining = room("Dining", 4500, 0, 3000, 3000);
        Room kitchen = room("Kitchen", 7500, 0, 3500, 3000);

        for (Room r : new Room[]{living, dining, kitchen}) {
            project.floorPlan().addRoom(r);
            addRectWalls(project, r);
        }

        addDoor(project, living, 0.15, true);
        addDoor(project, kitchen, 0.5, false);

        light(project, living, 0.5, 0.5);
        light(project, living, 0.25, 0.5);
        socket2(project, living, 0.15, 0.12);
        socket2(project, living, 0.85, 0.12);
        socket2(project, living, 0.15, 0.88);
        switch1(project, living, 0.08, 0.15);

        light(project, dining, 0.5, 0.5);
        socket2(project, dining, 0.2, 0.15);
        socket2(project, dining, 0.8, 0.15);
        switch1(project, dining, 0.1, 0.2);

        light(project, kitchen, 0.5, 0.5);
        socket2(project, kitchen, 0.2, 0.15);
        socket2(project, kitchen, 0.5, 0.15);
        socket2(project, kitchen, 0.8, 0.15);
        device(project, kitchen, "SW-45A-DP", "switch_45a_dp", "Cooker switch", 0.85, 0.85);
        switch1(project, kitchen, 0.1, 0.2);

        // Protection devices on ground only (service intake)
        device(project, null, "DB-8WAY", "db_8way", "Main consumer unit", 11000, 2000);
        device(project, null, "RCCB-63-30", "rccb_30ma", "Main RCCB 63A 30mA", 11000, 2500);
        device(project, null, "EARTH-ROD-16", "earth_rod", "Earth rod", 200, -800);
        device(project, null, "EARTH-CLAMP", "earth_clamp", "Earth clamp", 400, -800);
        device(project, null, "SPD-T2", "spd_t2", "Surge protector", 11000, 3000);

        // —— First floor ——
        project.addStorey("First floor", 1);
        project.setActiveStoreyIndex(1);
        project.floorPlan().setGridMm(500);
        project.floorPlan().setSnapToGrid(true);

        Room master = room("Master Bedroom", 0, 0, 4500, 4000);
        Room bed2 = room("Bedroom 2", 4500, 0, 4000, 3500);
        Room bath = room("Bathroom", 8500, 0, 2500, 2500);

        for (Room r : new Room[]{master, bed2, bath}) {
            project.floorPlan().addRoom(r);
            addRectWalls(project, r);
        }

        addDoor(project, master, 0.3, false);
        addDoor(project, bed2, 0.3, false);
        addDoor(project, bath, 0.4, false);

        light(project, master, 0.5, 0.5);
        socket2(project, master, 0.15, 0.15);
        socket2(project, master, 0.85, 0.15);
        socket2(project, master, 0.15, 0.85);
        switch1(project, master, 0.1, 0.2);

        light(project, bed2, 0.5, 0.5);
        socket2(project, bed2, 0.15, 0.15);
        socket2(project, bed2, 0.85, 0.15);
        switch1(project, bed2, 0.1, 0.2);

        device(project, bath, "LIGHT-BULKHEAD", "light_bulkhead", "Bulkhead light", 0.5, 0.5);
        socket2(project, bath, 0.2, 0.15);
        switch1(project, bath, 0.1, 0.2);

        project.setActiveStoreyIndex(0);
        return project;
    }

    public static Project createThreeBedBungalow() {
        Project project = new Project(SAMPLE_NAME);
        project.settings().setHouseType("3-bedroom bungalow (Ghana residential)");
        project.settings().setSupplyType(ProjectSettings.SupplyType.SINGLE_PHASE_230V);
        project.floorPlan().setGridMm(500);
        project.floorPlan().setSnapToGrid(true);

        // Rooms (mm) — origin bottom-left of plot
        Room living = room("Living", 0, 0, 4500, 4000);
        Room dining = room("Dining", 4500, 0, 3000, 3000);
        Room kitchen = room("Kitchen", 7500, 0, 3500, 3000);
        Room master = room("Master Bedroom", 0, 4000, 4000, 3500);
        Room bed2 = room("Bedroom 2", 4000, 4000, 3500, 3500);
        Room bed3 = room("Bedroom 3", 7500, 4000, 3500, 3500);
        Room bath = room("Bathroom", 0, 7500, 2500, 2500);
        Room store = room("Store", 2500, 7500, 2000, 2500);
        Room veranda = room("Veranda", 4500, 7500, 6500, 2000);

        for (Room r : new Room[]{living, dining, kitchen, master, bed2, bed3, bath, store, veranda}) {
            project.floorPlan().addRoom(r);
            addRectWalls(project, r);
        }

        // External perimeter highlight walls already covered by room walls

        // Doors (openings on walls)
        addDoor(project, living, 0.15, true);   // front of living
        addDoor(project, kitchen, 0.5, false);
        addDoor(project, master, 0.3, false);
        addDoor(project, bed2, 0.3, false);
        addDoor(project, bed3, 0.3, false);
        addDoor(project, bath, 0.4, false);
        addDoor(project, store, 0.5, false);

        // Electrical devices (catalogue ids from ComponentSeed)
        // Living
        light(project, living, 0.5, 0.5);
        light(project, living, 0.25, 0.5);
        socket2(project, living, 0.15, 0.12);
        socket2(project, living, 0.85, 0.12);
        socket2(project, living, 0.15, 0.88);
        socket2(project, living, 0.85, 0.88);
        switch1(project, living, 0.08, 0.15);

        // Dining
        light(project, dining, 0.5, 0.5);
        socket2(project, dining, 0.2, 0.15);
        socket2(project, dining, 0.8, 0.15);
        switch1(project, dining, 0.1, 0.2);

        // Kitchen — cooker + extra sockets
        light(project, kitchen, 0.5, 0.5);
        socket2(project, kitchen, 0.2, 0.15);
        socket2(project, kitchen, 0.5, 0.15);
        socket2(project, kitchen, 0.8, 0.15);
        socket2(project, kitchen, 0.2, 0.85);
        device(project, kitchen, "SW-45A-DP", "switch_45a_dp", "Cooker switch", 0.85, 0.85);
        switch1(project, kitchen, 0.1, 0.2);

        // Master
        light(project, master, 0.5, 0.5);
        socket2(project, master, 0.15, 0.15);
        socket2(project, master, 0.85, 0.15);
        socket2(project, master, 0.15, 0.85);
        switch1(project, master, 0.1, 0.2);

        // Bed 2 & 3
        for (Room bed : new Room[]{bed2, bed3}) {
            light(project, bed, 0.5, 0.5);
            socket2(project, bed, 0.15, 0.15);
            socket2(project, bed, 0.85, 0.15);
            switch1(project, bed, 0.1, 0.2);
        }

        // Bathroom — bulkhead + limited socket
        device(project, bath, "LIGHT-BULKHEAD", "light_bulkhead", "Bulkhead light", 0.5, 0.5);
        socket2(project, bath, 0.2, 0.15);
        switch1(project, bath, 0.1, 0.2);

        // Store / veranda
        light(project, store, 0.5, 0.5);
        light(project, veranda, 0.3, 0.5);
        light(project, veranda, 0.7, 0.5);
        socket2(project, veranda, 0.2, 0.3);

        // Global protection near kitchen/store (service intake proxy)
        device(project, null, "DB-8WAY", "db_8way", "Main consumer unit", 11000, 2000);
        device(project, null, "RCCB-63-30", "rccb_30ma", "Main RCCB 63A 30mA", 11000, 2500);
        device(project, null, "EARTH-ROD-16", "earth_rod", "Earth rod", 200, -800);
        device(project, null, "EARTH-CLAMP", "earth_clamp", "Earth clamp", 400, -800);
        device(project, null, "SPD-T2", "spd_t2", "Surge protector", 11000, 3000);

        return project;
    }

    private static Room room(String name, double x, double y, double w, double h) {
        return new Room(name, x, y, w, h);
    }

    private static void addRectWalls(Project project, Room r) {
        Vec2 tl = new Vec2(r.x(), r.y());
        Vec2 tr = new Vec2(r.x() + r.widthMm(), r.y());
        Vec2 br = new Vec2(r.x() + r.widthMm(), r.y() + r.heightMm());
        Vec2 bl = new Vec2(r.x(), r.y() + r.heightMm());
        project.floorPlan().addWall(new Wall(tl, tr));
        project.floorPlan().addWall(new Wall(tr, br));
        project.floorPlan().addWall(new Wall(br, bl));
        project.floorPlan().addWall(new Wall(bl, tl));
    }

    private static void addDoor(Project project, Room room, double tAlongBottom, boolean bottom) {
        // Use bottom wall of room (first wall added for room is top in our order: tl-tr is y=min)
        // Find a wall on the room boundary: use bottom edge y = r.y()
        double y = bottom ? room.y() : room.y() + room.heightMm();
        double x1 = room.x();
        double x2 = room.x() + room.widthMm();
        Wall wall = new Wall(new Vec2(x1, y), new Vec2(x2, y));
        project.floorPlan().addWall(wall);
        project.floorPlan().addOpening(new Opening(wall.id(), OpeningType.DOOR, tAlongBottom, 900));
    }

    private static void light(Project p, Room r, double fx, double fy) {
        device(p, r, "LIGHT-LED-9W", "light_led", "LED light · " + r.name(), fx, fy);
    }

    private static void socket2(Project p, Room r, double fx, double fy) {
        device(p, r, "SOCK-13A-2G", "socket_13a_2g", "13A twin socket · " + r.name(), fx, fy);
    }

    private static void switch1(Project p, Room r, double fx, double fy) {
        device(p, r, "SW-1G", "switch_1g", "1-gang switch · " + r.name(), fx, fy);
    }

    private static void device(
            Project p, Room r, String componentId, String symbolKey, String name, double fx, double fy
    ) {
        double x;
        double y;
        String roomId = null;
        if (r != null) {
            x = r.x() + fx * r.widthMm();
            y = r.y() + fy * r.heightMm();
            roomId = r.id();
        } else {
            x = fx;
            y = fy;
        }
        PlacedDevice d = new PlacedDevice(componentId, symbolKey, x, y);
        d.setNameOverride(name);
        d.setRoomId(roomId);
        p.floorPlan().addDevice(d);
    }
}
