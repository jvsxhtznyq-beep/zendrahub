package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.eco.Ecology;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** What a settlement can reach: the cells within a square radius of a point and what they hold. */
final class LocalArea {
    /** Average of the four seasons' growth multipliers, for year-round estimates. */
    static final double SEASON_AVERAGE = 0.8;

    private static final int CACHED_RADII = 16;
    private static final int[][][] OFFSETS = new int[CACHED_RADII + 1][][];

    static {
        for (int r = 0; r <= CACHED_RADII; r++) {
            OFFSETS[r] = computeOffsets(r);
        }
    }

    /** Cells nearest first (ties broken by z, then x). */
    final List<Cell> cells;
    final double standingTimber;
    final double timberCapacity;
    final double forage;
    final double herbivores;
    final double saplings;
    /** Highest stone richness among the cells. */
    final double bestStone;
    /** Wetland or desert within reach: soil for mud brick. */
    final boolean mudSoil;
    /** The settlers' estimate of daily timber regrowth, averaged over the year. */
    final double regrowthPerDay;

    private LocalArea(List<Cell> cells, double standingTimber, double timberCapacity, double forage,
                      double herbivores, double saplings, double bestStone, boolean mudSoil, double regrowthPerDay) {
        this.cells = cells;
        this.standingTimber = standingTimber;
        this.timberCapacity = timberCapacity;
        this.forage = forage;
        this.herbivores = herbivores;
        this.saplings = saplings;
        this.bestStone = bestStone;
        this.mudSoil = mudSoil;
        this.regrowthPerDay = regrowthPerDay;
    }

    static LocalArea survey(SimContext ctx, GridPos center, int radius) {
        WorldState st = ctx.state;
        CivConfig cfg = ctx.config.civ;
        List<Cell> cells = cellsAround(st, center, radius);
        double timber = 0, capacity = 0, forage = 0, herbivores = 0, saplings = 0, stone = 0, regrowth = 0;
        boolean mud = false;
        for (Cell c : cells) {
            double k = Ecology.timberCapacity(c);
            timber += c.timber;
            capacity += k;
            forage += c.forage;
            herbivores += c.herbivores;
            saplings += c.saplings;
            stone = Math.max(stone, c.biome.stoneRichness);
            mud |= c.biome == Biome.WETLAND || c.biome == Biome.DESERT;
            Region region = st.region(c.regionId);
            double fertility = region == null ? 1.0 : region.env.fertility;
            if (k > 0 && c.timber < k) {
                regrowth += cfg.assumedTimberGrowthRate * fertility * SEASON_AVERAGE * c.timber * (1 - c.timber / k);
            }
            // Planted saplings mature over about a month; settlers count young trees at half weight.
            regrowth += c.saplings * cfg.logsPerSapling / 60.0 * fertility * SEASON_AVERAGE;
        }
        return new LocalArea(cells, timber, capacity, forage, herbivores, saplings, stone, mud, regrowth);
    }

    /** Standing timber within a square radius, without building a full survey. */
    static double standingTimber(WorldState st, GridPos center, int radius) {
        double timber = 0;
        for (int[] o : offsets(radius)) {
            Cell c = st.cell(center.x() + o[0], center.z() + o[1]);
            if (c != null) {
                timber += c.timber;
            }
        }
        return timber;
    }

    /** Cells within a square radius of a point, nearest first. */
    static List<Cell> cellsAround(WorldState st, GridPos center, int radius) {
        int[][] offsets = offsets(radius);
        List<Cell> out = new ArrayList<>(offsets.length);
        for (int[] o : offsets) {
            Cell c = st.cell(center.x() + o[0], center.z() + o[1]);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    /** (dx, dz) offsets of a square radius, nearest first. */
    static int[][] offsets(int radius) {
        return radius <= CACHED_RADII ? OFFSETS[Math.max(0, radius)] : computeOffsets(radius);
    }

    private static int[][] computeOffsets(int r) {
        List<int[]> list = new ArrayList<>();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                list.add(new int[]{dx, dz});
            }
        }
        list.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1])
                .thenComparingInt(o -> o[1])
                .thenComparingInt(o -> o[0]));
        return list.toArray(new int[0][]);
    }
}
