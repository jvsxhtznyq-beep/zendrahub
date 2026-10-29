package com.zendrahub.evolve.core.model;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.event.EventLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete, serialisable state of one simulated world. Everything the
 * simulation knows lives here, so saving it and loading it back resumes a run
 * exactly. Entity ids are assigned sequentially from 1 and entities are never
 * removed (abandoned settlements and ruins stay in the history), so an id is
 * also its position in its list.
 */
public final class WorldState {
    /** Save format version; bump it and add a migration when the layout changes. */
    public static final int FORMAT_VERSION = 1;

    public final long seed;
    public final int width;
    public final int height;
    private final Cell[] cells;

    /** Days simulated so far; the first step simulates day 1. */
    public int day;
    public int daysPerSeason = 10;

    public final List<Region> regions = new ArrayList<>();
    public final List<Settlement> settlements = new ArrayList<>();
    public final List<Building> buildings = new ArrayList<>();
    public final List<TradeRoute> routes = new ArrayList<>();
    public final List<WorldAction> pendingActions = new ArrayList<>();
    public final EventLog events;

    public long nextActionId = 1;

    /** Host mapping: the chunk that cell (0, 0) corresponds to, once anchored in a game world. */
    public boolean anchored;
    public int originChunkX;
    public int originChunkZ;

    public WorldState(long seed, int width, int height) {
        this(seed, width, height, new EventLog());
    }

    public WorldState(long seed, int width, int height, EventLog events) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("world size must be positive: " + width + "x" + height);
        }
        this.seed = seed;
        this.width = width;
        this.height = height;
        this.events = events;
        this.cells = new Cell[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                cells[z * width + x] = new Cell(x, z, Biome.PLAINS);
            }
        }
    }

    public boolean inBounds(int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < height;
    }

    public boolean inBounds(GridPos p) {
        return inBounds(p.x(), p.z());
    }

    /** The cell at (x, z), or null outside the grid. */
    public Cell cell(int x, int z) {
        return inBounds(x, z) ? cells[z * width + x] : null;
    }

    public Cell cell(GridPos p) {
        return cell(p.x(), p.z());
    }

    /** All cells, row by row (index = z * width + x). Do not replace elements. */
    public Cell[] cells() {
        return cells;
    }

    public Region region(int id) {
        return id >= 0 && id < regions.size() ? regions.get(id) : null;
    }

    public Region regionAt(int x, int z) {
        Cell c = cell(x, z);
        return c == null ? null : region(c.regionId);
    }

    public Region regionAt(GridPos p) {
        return regionAt(p.x(), p.z());
    }

    public Settlement settlement(int id) {
        return id >= 1 && id <= settlements.size() ? settlements.get(id - 1) : null;
    }

    public Building building(int id) {
        return id >= 1 && id <= buildings.size() ? buildings.get(id - 1) : null;
    }

    public TradeRoute route(int id) {
        return id >= 1 && id <= routes.size() ? routes.get(id - 1) : null;
    }

    public int nextSettlementId() {
        return settlements.size() + 1;
    }

    public int nextBuildingId() {
        return buildings.size() + 1;
    }

    public int nextRouteId() {
        return routes.size() + 1;
    }

    public Season season() {
        return seasonOn(day);
    }

    public Season seasonOn(int someDay) {
        return Season.values()[Math.floorMod(Math.floorDiv(someDay, daysPerSeason), 4)];
    }

    public int year() {
        return day / (daysPerSeason * 4);
    }

    public Settlement settlementByName(String name) {
        for (Settlement s : settlements) {
            if (s.name.equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }
}
