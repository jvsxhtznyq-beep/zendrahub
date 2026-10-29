package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Decision;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hand-built worlds for civilization tests. Only the civilization system runs
 * (no environment or ecosystem), so nothing regrows: cells get extra forage to
 * stand in for regrowth over a test's span.
 */
final class TestWorlds {
    static final long SEED = 1234L;
    static final GridPos HOME = new GridPos(8, 12);

    private TestWorlds() {
    }

    /** A grid of one biome in one temperate region (gravity 1, no storms). */
    static WorldState uniform(int width, int height, Biome biome, double timberShare) {
        WorldState st = new WorldState(SEED, width, height);
        Region region = new Region(0, "Testland", 0, 0, width, height);
        region.env.set(1.0, 1.0, 0.002, 0.0);
        st.regions.add(region);
        for (Cell c : st.cells()) {
            fill(c, biome, timberShare);
            c.regionId = 0;
        }
        return st;
    }

    static void fill(Cell c, Biome biome, double timberShare) {
        c.biome = biome;
        c.timber = biome.timberCapacity * timberShare;
        c.forage = biome.forageCapacity * 4;
        c.herbivores = biome.forageCapacity * 0.1;
    }

    /** Sets the standing timber of every cell within a square radius. */
    static void setTimber(WorldState st, GridPos center, int radius, double logs) {
        for (Cell c : cellsAround(st, center, radius)) {
            c.timber = logs;
        }
    }

    static void setBiome(WorldState st, GridPos center, int radius, Biome biome, double timberShare) {
        for (Cell c : cellsAround(st, center, radius)) {
            fill(c, biome, timberShare);
        }
    }

    static List<Cell> cellsAround(WorldState st, GridPos center, int radius) {
        List<Cell> out = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                Cell c = st.cell(center.x() + dx, center.z() + dz);
                if (c != null) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** Gives the region covering everything new environmental rules. */
    static Region env(WorldState st, double gravity, double fertility, double woodDecay) {
        Region r = st.regions.get(0);
        r.env.set(gravity, fertility, woodDecay, 0.0);
        return r;
    }

    /** Adds a region over a rectangle of cells (x0, z0 inclusive; x1, z1 exclusive). */
    static Region addRegion(WorldState st, String name, int x0, int z0, int x1, int z1, double gravity,
                            double woodDecay) {
        Region r = new Region(st.regions.size(), name, x0, z0, x1, z1);
        r.env.set(gravity, 1.0, woodDecay, 0.0);
        st.regions.add(r);
        for (Cell c : st.cells()) {
            if (r.contains(c.x, c.z)) {
                c.regionId = r.id;
            }
        }
        return r;
    }

    static SimContext ctx(WorldState st, SimConfig cfg) {
        return new SimContext(st, cfg);
    }

    static Settlement found(CivilizationSystem civ, WorldState st, SimConfig cfg, String name, GridPos home,
                            int population) {
        return Objects.requireNonNull(civ.foundSettlement(ctx(st, cfg), name, home, population, -1, -1),
                "founding " + name + " failed");
    }

    /** Simulates one day of civilization only. */
    static void day(CivilizationSystem civ, WorldState st, SimConfig cfg) {
        st.day++;
        civ.step(ctx(st, cfg));
    }

    /** Simulates days, collecting every world action produced (including any queued before). */
    static List<WorldAction> run(CivilizationSystem civ, WorldState st, SimConfig cfg, int days) {
        List<WorldAction> actions = drain(st);
        for (int i = 0; i < days; i++) {
            day(civ, st, cfg);
            actions.addAll(drain(st));
        }
        return actions;
    }

    static List<WorldAction> drain(WorldState st) {
        List<WorldAction> out = new ArrayList<>(st.pendingActions);
        st.pendingActions.clear();
        return out;
    }

    /** Pins a settlement's strategy so the periodic review does not run during a short test. */
    static void pinStrategy(Settlement s, Strategy strategy, SimConfig cfg) {
        cfg.civ.strategyInterval = 1_000_000;
        s.strategy = strategy;
        Map<Strategy, Double> scores = new EnumMap<>(Strategy.class);
        for (Strategy k : Strategy.values()) {
            scores.put(k, k == strategy ? 1.0 : 0.0);
        }
        s.lastDecision = new Decision(0, strategy, scores, List.of("pinned by test"), -1);
    }

    /** A forest whose cells within three of HOME were logged down to a few logs each. */
    static WorldState depletedForest() {
        WorldState st = uniform(24, 24, Biome.FOREST, 0.9);
        setTimber(st, HOME, 3, 5.0);
        return st;
    }

    static WorldState abundantForest() {
        return uniform(24, 24, Biome.FOREST, 0.9);
    }

    static double totalTimber(WorldState st) {
        double t = 0;
        for (Cell c : st.cells()) {
            t += c.timber;
        }
        return t;
    }

    static double totalSaplings(WorldState st) {
        double t = 0;
        for (Cell c : st.cells()) {
            t += c.saplings;
        }
        return t;
    }
}
