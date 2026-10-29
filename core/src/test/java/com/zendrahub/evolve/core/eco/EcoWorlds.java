package com.zendrahub.evolve.core.eco;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.SimSystem;
import com.zendrahub.evolve.core.WorldGen;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.WorldState;

/** Small hand-built worlds for environment and ecosystem tests. */
public final class EcoWorlds {
    private EcoWorlds() {
    }

    /**
     * A world of one biome in a single region with the given rules, every cell
     * at {@code timberShare} of its timber capacity, wildlife settled and
     * baselines recorded.
     */
    public static WorldState uniform(int width, int height, Biome biome, double timberShare, double gravity,
                                     double fertility, double woodDecay, double storminess) {
        WorldState s = new WorldState(1234, width, height);
        Region r = new Region(0, "Testmere Vale", 0, 0, width, height);
        r.env.set(gravity, fertility, woodDecay, storminess);
        s.regions.add(r);
        for (Cell c : s.cells()) {
            c.regionId = 0;
            WorldGen.populateCell(c, biome, timberShare);
        }
        WorldGen.refreshRegionBaselines(s);
        return s;
    }

    /** A calm temperate forest world: fertility 1, normal gravity, no storms. */
    public static WorldState forest(int width, int height) {
        return uniform(width, height, Biome.FOREST, 1.0, 1.0, 1.0, 0.002, 0.0);
    }

    /** Simulates {@code days} days with only the given systems, discarding world actions. */
    public static void run(WorldState s, SimConfig cfg, int days, SimSystem... systems) {
        for (int d = 0; d < days; d++) {
            s.day++;
            SimContext ctx = new SimContext(s, cfg);
            for (SimSystem system : systems) {
                system.step(ctx);
            }
            s.pendingActions.clear();
        }
    }

    public static double totalHerbivores(WorldState s) {
        double total = 0;
        for (Cell c : s.cells()) {
            total += c.herbivores;
        }
        return total;
    }

    public static double herbivoresInColumns(WorldState s, int x0, int x1) {
        double total = 0;
        for (Cell c : s.cells()) {
            if (c.x >= x0 && c.x < x1) {
                total += c.herbivores;
            }
        }
        return total;
    }
}
