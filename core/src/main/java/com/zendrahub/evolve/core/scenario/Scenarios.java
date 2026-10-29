package com.zendrahub.evolve.core.scenario;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.Simulation;
import com.zendrahub.evolve.core.WorldGen;
import com.zendrahub.evolve.core.WorldGenOptions;
import com.zendrahub.evolve.core.eco.Ecology;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.ArrayList;
import java.util.List;

/**
 * Reproducible starting conditions for experiments, the demo and benchmarks.
 * Scenarios only choose where things start; everything after that is the
 * ordinary simulation.
 */
public final class Scenarios {
    public static final String HOME_TOWN = "Oakridge";
    public static final String NEIGHBOUR_TOWN = "Stonebrook";

    private Scenarios() {
    }

    /**
     * The milestone-1 pair. The world, the towns and the code are identical;
     * with {@code depleted} the forest within 3 cells of Oakridge has been
     * logged down to 5% before the town is founded.
     */
    public static Simulation forestTown(long seed, boolean depleted, SimConfig config) {
        WorldState s = WorldGen.generate(seed, WorldGenOptions.defaults());
        GridPos home = forestSite(s);
        if (depleted) {
            deplete(s, home, 3, 0.05);
        }
        Simulation sim = new Simulation(s, config);
        sim.foundSettlement(HOME_TOWN, home, 20);
        GridPos neighbour = stoneSite(s, home, 9);
        if (neighbour != null) {
            sim.foundSettlement(NEIGHBOUR_TOWN, neighbour, 16);
        }
        return sim;
    }

    /** Fixes the gravity of the region containing {@code cell} (base and current) and stops any flux. */
    public static void setRegionGravity(WorldState s, GridPos cell, double gravity) {
        Region r = s.regionAt(cell);
        r.env.gravityFlux = false;
        r.env.baseGravity = gravity;
        r.env.gravity = gravity;
        r.env.clamp();
    }

    /**
     * The buildable land cell with the most standing timber within two cells,
     * preferring cells near the centre of the world on ties.
     */
    public static GridPos forestSite(WorldState s) {
        GridPos best = null;
        double bestScore = -1;
        double cx = (s.width - 1) / 2.0;
        double cz = (s.height - 1) / 2.0;
        int margin = Math.min(3, Math.min(s.width, s.height) / 4);
        for (int z = margin; z < s.height - margin; z++) {
            for (int x = margin; x < s.width - margin; x++) {
                Cell c = s.cell(x, z);
                if (!c.biome.buildable() || c.biome == Biome.MOUNTAIN) {
                    continue;
                }
                double timber = sumAround(s, x, z, 2, true);
                double centrality = 1.0 / (1.0 + Math.hypot(x - cx, z - cz));
                double score = timber + centrality;
                if (score > bestScore) {
                    bestScore = score;
                    best = new GridPos(x, z);
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("world has no buildable land");
        }
        return best;
    }

    /**
     * A livable site at least {@code minDistance} cells from {@code awayFrom}
     * that also has good quarrying, or null if the world has none. Livable
     * means enough wild food and farmland within reach to feed a young town:
     * a stone site in barren mountains would only starve.
     */
    public static GridPos stoneSite(WorldState s, GridPos awayFrom, int minDistance) {
        return neighbourSite(s, awayFrom, minDistance, Integer.MAX_VALUE);
    }

    /**
     * Like {@link #stoneSite}, limited to sites between {@code minDistance} and
     * {@code maxDistance} cells (king moves) from {@code awayFrom}. Hosts use it
     * to place a neighbouring town that can survive and trade.
     */
    public static GridPos neighbourSite(WorldState s, GridPos awayFrom, int minDistance, int maxDistance) {
        GridPos best = null;
        double bestScore = -1;
        for (int z = 1; z < s.height - 1; z++) {
            for (int x = 1; x < s.width - 1; x++) {
                Cell c = s.cell(x, z);
                GridPos p = new GridPos(x, z);
                int distance = p.chebyshev(awayFrom);
                if (!c.biome.buildable() || c.biome == Biome.MOUNTAIN || c.biome == Biome.DESERT
                        || distance < minDistance || distance > maxDistance) {
                    continue;
                }
                double stone = 0;
                double food = 0;
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        Cell n = s.cell(x + dx, z + dz);
                        if (n != null) {
                            stone += n.biome.stoneRichness;
                            food += n.forage + n.herbivores * 8 + (n.biome.farmSuitability >= 0.4 ? 40 : 0);
                        }
                    }
                }
                if (food < MIN_FOOD_POTENTIAL) {
                    continue;
                }
                double score = stone * 10 + food * 0.02 - p.distance(awayFrom) * 0.2;
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        return best;
    }

    /** Wild food plus farmland bonus within two cells that a new town needs (see {@link #stoneSite}). */
    private static final double MIN_FOOD_POTENTIAL = 900;

    /** Spread-out town sites for large-world benchmarks: the best cell in each tile of a lattice. */
    public static List<GridPos> latticeSites(WorldState s, int count, int spacing) {
        List<GridPos> sites = new ArrayList<>();
        for (int tz = 0; tz + spacing <= s.height && sites.size() < count; tz += spacing) {
            for (int tx = 0; tx + spacing <= s.width && sites.size() < count; tx += spacing) {
                GridPos best = null;
                double bestScore = -1;
                for (int z = tz + 1; z < tz + spacing - 1; z++) {
                    for (int x = tx + 1; x < tx + spacing - 1; x++) {
                        Cell c = s.cell(x, z);
                        if (c == null || !c.biome.buildable()) {
                            continue;
                        }
                        double score = sumAround(s, x, z, 1, true) + sumAround(s, x, z, 1, false);
                        if (score > bestScore) {
                            bestScore = score;
                            best = new GridPos(x, z);
                        }
                    }
                }
                if (best != null) {
                    sites.add(best);
                }
            }
        }
        return sites;
    }

    /** Removes standing timber and saplings around a cell, keeping {@code keepShare} of the timber. */
    public static void deplete(WorldState s, GridPos center, int radius, double keepShare) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                Cell c = s.cell(center.x() + dx, center.z() + dz);
                if (c != null) {
                    Ecology.harvestTimber(c, c.timber * (1.0 - keepShare));
                    c.saplings = 0;
                }
            }
        }
    }

    private static double sumAround(WorldState s, int x, int z, int r, boolean timber) {
        double total = 0;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                Cell n = s.cell(x + dx, z + dz);
                if (n != null) {
                    total += timber ? n.timber : n.forage;
                }
            }
        }
        return total;
    }
}
