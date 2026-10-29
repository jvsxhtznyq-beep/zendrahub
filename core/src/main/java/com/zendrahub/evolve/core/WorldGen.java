package com.zendrahub.evolve.core;

import com.zendrahub.evolve.core.eco.EcoConfig;
import com.zendrahub.evolve.core.eco.EcosystemSystem;
import com.zendrahub.evolve.core.eco.Ecology;
import com.zendrahub.evolve.core.env.RegionArchetype;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.EnvironmentProfile;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.WorldState;
import com.zendrahub.evolve.core.util.DetRandom;
import com.zendrahub.evolve.core.util.Noise;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Procedural generation of terrain, regions and their environmental rules. */
public final class WorldGen {
    // Randomness streams 1-99 (see DetRandom).
    private static final long S_ELEVATION = 1;
    private static final long S_MOISTURE = 2;
    private static final long S_TIMBER = 3;
    private static final long S_ARCHETYPE = 4;
    private static final long S_JITTER = 5;
    private static final long S_FLUX = 6;
    private static final long S_NAME = 7;

    /** Terrain feature sizes, in cells. */
    private static final double ELEVATION_SCALE = 9.0;
    private static final double MOISTURE_SCALE = 7.0;
    private static final double TIMBER_SCALE = 3.0;

    /**
     * Biome shares by rank, so every world gets a comparable mix whatever the
     * noise distribution: the lowest cells are lakes, the highest mountains
     * then hills; the rest split by moisture.
     */
    private static final double WATER_SHARE = 0.04;
    private static final double MOUNTAIN_SHARE = 0.06;
    private static final double HILLS_SHARE = 0.12;
    private static final double WETLAND_SHARE = 0.10;
    private static final double FOREST_SHARE = 0.42;
    private static final double DESERT_SHARE = 0.08;

    /** New cells start with timber between these shares of capacity. */
    private static final double MIN_TIMBER_SHARE = 0.6;
    private static final double MAX_TIMBER_SHARE = 1.0;
    /** New cells start with this share of their forage capacity, and herbivores at equilibrium. */
    private static final double INITIAL_FORAGE_SHARE = 0.8;

    /** Values a region may differ from its archetype by: +-4% gravity, +-10% everything else. */
    private static final double GRAVITY_JITTER = 0.04;
    private static final double JITTER = 0.10;

    private static final String[] NAME_PREFIXES = {
            "Ash", "Briar", "Cinder", "Dun", "Elder", "Fern", "Gloam", "Hollow", "Iron", "Juniper", "Kestrel",
            "Lark", "Moss", "North", "Oak", "Pale", "Raven", "Silver", "Thorn", "Umber", "Vesper", "Willow",
            "Wren", "Yarrow"
    };
    private static final String[] NAME_SUFFIXES = {
            "ford", "mere", "wick", "holm", "stead", "fell", "dale", "moor", "brook", "crest"
    };

    private static final EcoConfig DEFAULT_ECO = new EcoConfig();

    private WorldGen() {
    }

    /** A complete world: noise-based biomes, initial ecology, regions with environment profiles. */
    public static WorldState generate(long seed, WorldGenOptions options) {
        WorldState state = new WorldState(seed, options.width(), options.height());
        RegionArchetype[] archetypes = assignRegions(state, options.regionSize());

        Cell[] cells = state.cells();
        int n = cells.length;
        double[] elevation = new double[n];
        double[] moisture = new double[n];
        for (int i = 0; i < n; i++) {
            Cell c = cells[i];
            RegionArchetype a = archetypes[c.regionId];
            elevation[i] = Noise.fractal(seed, S_ELEVATION, c.x, c.z, ELEVATION_SCALE) + a.elevationBias;
            moisture[i] = Noise.fractal(seed, S_MOISTURE, c.x, c.z, MOISTURE_SCALE) + a.moistureBias;
        }

        double[] sortedElevation = elevation.clone();
        Arrays.sort(sortedElevation);
        double[] sortedMoisture = moisture.clone();
        Arrays.sort(sortedMoisture);
        double waterMax = quantile(sortedElevation, WATER_SHARE);
        double mountainMin = quantile(sortedElevation, 1.0 - MOUNTAIN_SHARE);
        double hillsMin = quantile(sortedElevation, 1.0 - MOUNTAIN_SHARE - HILLS_SHARE);
        double lowland = quantile(sortedElevation, 0.5);
        double wetMin = quantile(sortedMoisture, 1.0 - WETLAND_SHARE);
        double forestMin = quantile(sortedMoisture, 1.0 - WETLAND_SHARE - FOREST_SHARE);
        double desertMax = quantile(sortedMoisture, DESERT_SHARE);

        for (int i = 0; i < n; i++) {
            double e = elevation[i];
            double m = moisture[i];
            Biome biome;
            if (e <= waterMax) {
                biome = Biome.WATER;
            } else if (e >= mountainMin) {
                biome = Biome.MOUNTAIN;
            } else if (e >= hillsMin) {
                biome = Biome.HILLS;
            } else if (m >= wetMin) {
                biome = e < lowland ? Biome.WETLAND : Biome.FOREST;
            } else if (m >= forestMin) {
                biome = Biome.FOREST;
            } else if (m <= desertMax) {
                biome = Biome.DESERT;
            } else {
                biome = Biome.PLAINS;
            }
            Cell c = cells[i];
            double share = MIN_TIMBER_SHARE + (MAX_TIMBER_SHARE - MIN_TIMBER_SHARE)
                    * Noise.value(seed, S_TIMBER, c.x, c.z, TIMBER_SCALE);
            populateCell(c, biome, share);
        }

        refreshRegionBaselines(state);
        state.events.record(state.day, EventType.WORLD_CREATED, -1, -1, -1, -1,
                "World created: " + state.width + "x" + state.height + " cells, " + describeRegions(archetypes), -1);
        return state;
    }

    /**
     * Regions and environment profiles only; every cell starts as empty plains.
     * Hosts call {@link #populateCell} for each cell with what they read from the real world.
     * Region baselines (initial forest cover and wildlife) are taken on the first
     * simulated day, or at once with {@link #refreshRegionBaselines}.
     */
    public static WorldState blank(long seed, WorldGenOptions options) {
        WorldState state = new WorldState(seed, options.width(), options.height());
        for (Cell c : state.cells()) {
            c.timber = 0;
            c.forage = 0;
            c.herbivores = 0;
        }
        RegionArchetype[] archetypes = assignRegions(state, options.regionSize());
        state.events.record(state.day, EventType.WORLD_CREATED, -1, -1, -1, -1,
                "World laid out: " + state.width + "x" + state.height + " cells, " + describeRegions(archetypes)
                        + "; terrain comes from the host world", -1);
        return state;
    }

    /** Sets a cell's biome and a consistent initial ecology, with timber at the given share of capacity. */
    public static void populateCell(Cell cell, Biome biome, double timberShare) {
        double share = Double.isNaN(timberShare) ? 0.0 : Math.min(1.0, Math.max(0.0, timberShare));
        cell.biome = biome;
        cell.timber = Ecology.timberCapacity(cell) * share;
        cell.forage = Ecology.forageCapacity(cell) * INITIAL_FORAGE_SHARE;
        cell.saplings = 0;
        // The region's fertility is not known here; settling (see refreshRegionBaselines) adjusts for it.
        cell.herbivores = EcosystemSystem.herbivoreEquilibrium(DEFAULT_ECO, cell, 1.0);
    }

    /**
     * Settles every cell's forage and wildlife on its natural seasonal cycle at
     * its region's fertility, then records each region's forest cover and
     * wildlife as the baselines that change is measured against. Call after
     * populating cells; if a host does not, the ecosystem does it per region on
     * the first simulated day.
     */
    public static void refreshRegionBaselines(WorldState state) {
        EcosystemSystem.settleWildlife(state, DEFAULT_ECO);
        for (Region r : state.regions) {
            EcosystemSystem.recordBaseline(state, r);
        }
    }

    /**
     * Splits the grid into square regions and gives each an archetype and a
     * profile. The region holding the grid's centre (where a host anchors the
     * player's start) is always temperate; the rest are drawn from a seeded
     * shuffle bag, so every archetype appears before any repeats.
     */
    private static RegionArchetype[] assignRegions(WorldState state, int regionSize) {
        int cols = ceilDiv(state.width, regionSize);
        int rows = ceilDiv(state.height, regionSize);
        int count = cols * rows;
        int centreRegion = (state.height / 2 / regionSize) * cols + (state.width / 2 / regionSize);
        RegionArchetype[] archetypes = drawArchetypes(state.seed, count, centreRegion);

        Set<String> usedNames = new HashSet<>();
        for (int rz = 0; rz < rows; rz++) {
            for (int rx = 0; rx < cols; rx++) {
                int id = rz * cols + rx;
                int x0 = rx * regionSize;
                int z0 = rz * regionSize;
                int x1 = Math.min(state.width, x0 + regionSize);
                int z1 = Math.min(state.height, z0 + regionSize);
                RegionArchetype a = archetypes[id];
                Region r = new Region(id, regionName(state.seed, id, a, usedNames), x0, z0, x1, z1);
                applyArchetype(state.seed, r, a);
                state.regions.add(r);
                for (int z = z0; z < z1; z++) {
                    for (int x = x0; x < x1; x++) {
                        state.cell(x, z).regionId = id;
                    }
                }
            }
        }
        return archetypes;
    }

    private static RegionArchetype[] drawArchetypes(long seed, int count, int centreRegion) {
        RegionArchetype[] kinds = RegionArchetype.values();
        // Temperate goes in twice: it is the most common kind of land.
        RegionArchetype[] bag = new RegionArchetype[kinds.length + 1];
        RegionArchetype[] out = new RegionArchetype[count];
        int used = bag.length;
        int round = 0;
        for (int i = 0; i < count; i++) {
            if (used == bag.length) {
                System.arraycopy(kinds, 0, bag, 0, kinds.length);
                bag[kinds.length] = RegionArchetype.TEMPERATE;
                for (int j = bag.length - 1; j > 0; j--) {
                    int k = DetRandom.below(j + 1, seed, S_ARCHETYPE, round, j);
                    RegionArchetype t = bag[j];
                    bag[j] = bag[k];
                    bag[k] = t;
                }
                used = 0;
                round++;
            }
            out[i] = bag[used++];
        }
        if (centreRegion >= 0 && centreRegion < count && out[centreRegion] != RegionArchetype.TEMPERATE) {
            // Swap the centre's draw with the first temperate one, keeping the overall mix.
            for (int i = 0; i < count; i++) {
                if (out[i] == RegionArchetype.TEMPERATE) {
                    out[i] = out[centreRegion];
                    break;
                }
            }
            out[centreRegion] = RegionArchetype.TEMPERATE;
        }
        return out;
    }

    private static void applyArchetype(long seed, Region r, RegionArchetype a) {
        EnvironmentProfile env = r.env;
        double gravity = a.gravity == 1.0 ? 1.0 : a.gravity * jitter(seed, r.id, 0, GRAVITY_JITTER);
        env.set(gravity,
                a.fertility * jitter(seed, r.id, 1, JITTER),
                a.woodDecay * jitter(seed, r.id, 2, JITTER),
                a.storminess * jitter(seed, r.id, 3, JITTER));
        if (a.fluxAmplitude > 0) {
            env.gravityFlux = true;
            env.fluxAmplitude = a.fluxAmplitude * jitter(seed, r.id, 4, JITTER);
            env.fluxPeriodDays = 60 + 60 * DetRandom.unit(seed, S_FLUX, r.id, 0);
            env.fluxPhase = 2 * Math.PI * DetRandom.unit(seed, S_FLUX, r.id, 1);
            env.gravity = env.baseGravity + env.fluxAmplitude * Math.sin(env.fluxPhase);
            env.clamp();
        }
    }

    private static double jitter(long seed, int regionId, int property, double spread) {
        return 1.0 + spread * (2 * DetRandom.unit(seed, S_JITTER, regionId, property) - 1);
    }

    private static String regionName(long seed, int id, RegionArchetype a, Set<String> used) {
        int combos = NAME_PREFIXES.length * NAME_SUFFIXES.length;
        int start = DetRandom.below(combos, seed, S_NAME, id);
        for (int k = 0; k < combos; k++) {
            int idx = (start + k) % combos;
            String name = NAME_PREFIXES[idx / NAME_SUFFIXES.length] + NAME_SUFFIXES[idx % NAME_SUFFIXES.length]
                    + " " + a.noun;
            if (used.add(name)) {
                return name;
            }
        }
        String name = NAME_PREFIXES[start / NAME_SUFFIXES.length] + NAME_SUFFIXES[start % NAME_SUFFIXES.length]
                + " " + a.noun + " " + (id + 1);
        used.add(name);
        return name;
    }

    private static String describeRegions(RegionArchetype[] archetypes) {
        Map<RegionArchetype, Integer> counts = new EnumMap<>(RegionArchetype.class);
        for (RegionArchetype a : archetypes) {
            counts.merge(a, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder().append(archetypes.length).append(" regions (");
        boolean first = true;
        for (Map.Entry<RegionArchetype, Integer> e : counts.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(e.getKey().noun).append(" x").append(e.getValue());
        }
        return sb.append(')').toString();
    }

    private static double quantile(double[] sorted, double q) {
        if (sorted.length == 0) {
            return 0;
        }
        double clamped = Math.min(1.0, Math.max(0.0, q));
        return sorted[(int) Math.floor(clamped * (sorted.length - 1))];
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
