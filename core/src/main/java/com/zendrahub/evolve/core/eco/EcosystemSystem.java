package com.zendrahub.evolve.core.eco;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.SimSystem;
import com.zendrahub.evolve.core.env.EnvironmentSystem;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Season;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.List;
import java.util.Locale;

/**
 * Vegetation, wildlife and reclamation dynamics on every cell.
 *
 * <p>Each day, in two phases so that the order cells are visited in never
 * matters:
 * <ol>
 *   <li>Per cell, from a snapshot of yesterday's forest cover: timber grows
 *       logistically toward capacity, helped by seed rain from forested
 *       neighbours and a soil seed bank; saplings mature; forage regrows;
 *       herbivores eat, breed in proportion to how well fed they are and how
 *       good their habitat (forest cover) is, and die of age, hunger and crowding.</li>
 *   <li>Hungry or exposed animals migrate to their most attractive neighbour,
 *       with all moves computed before any is applied.</li>
 * </ol>
 * Then region totals are refreshed and region-level changes reported, with
 * hysteresis so a value hovering at a threshold does not produce a stream of events.
 */
public final class EcosystemSystem implements SimSystem {
    private static final int[] NEIGHBOUR_DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] NEIGHBOUR_DZ = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final double MEAN_SEASON_GROWTH = meanSeasonGrowth();
    /** Years of dynamics run to settle a new world's (or cell's) forage and wildlife. */
    private static final int SETTLE_YEARS = 4;

    // Per-cell scratch buffers, reused across steps.
    private double[] cover = new double[0];
    private double[] fed = new double[0];
    private double[] habitat = new double[0];
    private double[] attraction = new double[0];
    private double[] inflow = new double[0];
    private double[] outflow = new double[0];
    private double[] regionGrowth = new double[0];

    @Override
    public void step(SimContext ctx) {
        WorldState state = ctx.state;
        EcoConfig cfg = ctx.config.eco;
        Cell[] cells = state.cells();
        int n = cells.length;
        ensureBuffers(n, state.regions.size());

        double season = state.season().growth;
        List<Region> regions = state.regions;
        for (Region r : regions) {
            regionGrowth[r.id] = EnvironmentSystem.growthFactor(state, r);
        }
        for (int i = 0; i < n; i++) {
            cover[i] = Ecology.forestCover(cells[i]);
        }

        for (int i = 0; i < n; i++) {
            Cell c = cells[i];
            int rid = c.regionId;
            boolean inRegion = rid >= 0 && rid < regions.size();
            double g = inRegion ? regionGrowth[rid] : season;
            double fertility = inRegion ? regions.get(rid).env.fertility : 1.0;
            growTimber(cfg, c, g, neighbourCover(state, c));
            growForage(cfg, c, g);
            double quality = habitatQuality(cfg, cover[i]);
            habitat[i] = quality;
            fed[i] = breed(cfg, c, quality, season, fertility);
        }
        migrate(cfg, state, cells);

        refreshRegionStats(state);
        initialiseNewRegions(state, cfg);
        reportRegions(ctx, cfg);
    }

    /**
     * Regions a host filled in since yesterday get settled wildlife and their
     * baselines. When the whole world is new, the whole world is settled at once
     * (with migration); a region filled in later is settled on its own.
     */
    private static void initialiseNewRegions(WorldState state, EcoConfig cfg) {
        boolean anyNew = false;
        boolean allNew = true;
        for (Region r : state.regions) {
            boolean populated = r.forestCover > 0 || r.herbivores > 0;
            boolean fresh = !hasBaseline(r) && populated;
            anyNew |= fresh;
            allNew &= !hasBaseline(r);
        }
        if (!anyNew) {
            return;
        }
        if (allNew) {
            settleWildlife(state, cfg);
            for (Region r : state.regions) {
                recordBaseline(state, r);
            }
            return;
        }
        for (Region r : state.regions) {
            if (!hasBaseline(r) && (r.forestCover > 0 || r.herbivores > 0)) {
                for (int z = r.z0; z < r.z1; z++) {
                    for (int x = r.x0; x < r.x1; x++) {
                        settleWildlife(cfg, state.cell(x, z), r.env.fertility, state.daysPerSeason);
                    }
                }
                recordBaseline(state, r);
            }
        }
    }

    /** The host measured the real standing timber of a cell; reconcile the simulation with it. */
    public void observeTimber(SimContext ctx, GridPos pos, double measuredLogs) {
        Cell c = ctx.state.cell(pos);
        if (c == null || !(measuredLogs >= 0)) {
            return;
        }
        double modelled = c.timber;
        c.timber = measuredLogs;
        c.lastObservedDay = ctx.day();
        EcoConfig cfg = ctx.config.eco;
        double diff = Math.abs(measuredLogs - modelled);
        if (diff >= cfg.observationEventMinLogs && diff >= cfg.observationEventShare * Math.max(modelled, measuredLogs)) {
            ctx.emit(EventType.OBSERVATION, -1, c.regionId, pos, String.format(Locale.ROOT,
                    "Survey of cell %s found %d logs standing where %d were expected; the model now uses the survey",
                    pos, Math.round(measuredLogs), Math.round(modelled)));
        }
    }

    // ---- shared definitions -------------------------------------------------------------

    /** Habitat quality of a cell for grazing animals, from its forest cover: floor..1. */
    public static double habitatQuality(EcoConfig cfg, double forestCover) {
        double share = cfg.habitatFullCover <= 0 ? 1.0 : Math.min(1.0, forestCover / cfg.habitatFullCover);
        return cfg.habitatFloor + (1.0 - cfg.habitatFloor) * Math.max(0.0, share);
    }

    /**
     * Animals a cell supports before crowding deaths set in: its forage
     * capacity, scaled by habitat quality and by the land's productivity
     * (fertility), so populations stay within what the forage can regrow.
     */
    public static double herbivoreCapacity(EcoConfig cfg, Cell c, double fertility) {
        return capacityFor(cfg, c, habitatQuality(cfg, Ecology.forestCover(c)), fertility);
    }

    /**
     * The population a cell settles at over a year: births (which slow as the
     * habitat fills) balance deaths at the average season's growth. Poor
     * habitat lowers both capacity and birth rate, so it settles far lower.
     */
    public static double herbivoreEquilibrium(EcoConfig cfg, Cell c, double fertility) {
        double quality = habitatQuality(cfg, Ecology.forestCover(c));
        double birthRate = cfg.herbivoreBirthRate * quality * MEAN_SEASON_GROWTH;
        if (birthRate <= cfg.herbivoreBaseDeath || c.biome == Biome.WATER) {
            return 0;
        }
        return capacityFor(cfg, c, quality, fertility) * (1.0 - cfg.herbivoreBaseDeath / birthRate);
    }

    /**
     * Runs a cell's own forage and wildlife dynamics (no migration, timber held
     * fixed) through {@value #SETTLE_YEARS} years of seasons at the given
     * fertility, leaving it as it would stand at the start of spring. New cells
     * then begin on their natural cycle instead of drifting toward it.
     */
    public static void settleWildlife(EcoConfig cfg, Cell c, double fertility, int daysPerSeason) {
        if (c.biome == Biome.WATER || Ecology.forageCapacity(c) <= 0) {
            c.herbivores = 0;
            return;
        }
        if (c.herbivores <= 0) {
            c.herbivores = Math.max(herbivoreEquilibrium(cfg, c, fertility), cfg.extinctionThreshold * 10);
        }
        double quality = habitatQuality(cfg, Ecology.forestCover(c));
        Season[] seasons = Season.values();
        int season = Math.max(1, daysPerSeason);
        int days = SETTLE_YEARS * seasons.length * season;
        for (int d = 0; d < days; d++) {
            Season s = seasons[(d / season) % seasons.length];
            growForage(cfg, c, fertility * s.growth);
            breed(cfg, c, quality, s.growth, fertility);
        }
    }

    /**
     * Recomputes every region's mean forest cover and total herbivores from its cells.
     */
    public static void refreshRegionStats(WorldState state) {
        List<Region> regions = state.regions;
        int count = regions.size();
        if (count == 0) {
            return;
        }
        double[] coverSum = new double[count];
        double[] herbivoreSum = new double[count];
        for (Cell c : state.cells()) {
            int rid = c.regionId;
            if (rid >= 0 && rid < count) {
                coverSum[rid] += Ecology.forestCover(c);
                herbivoreSum[rid] += c.herbivores;
            }
        }
        for (Region r : regions) {
            r.forestCover = coverSum[r.id] / Math.max(1, r.cellCount());
            r.herbivores = herbivoreSum[r.id];
        }
    }

    /**
     * Runs the whole world's forage and wildlife dynamics, migration included and
     * timber held fixed, through {@value #SETTLE_YEARS} years of seasons, leaving
     * it as it would stand at the start of spring: a new world's animals then
     * begin on their natural cycle and distribution instead of drifting toward it.
     */
    public static void settleWildlife(WorldState state, EcoConfig cfg) {
        EcosystemSystem scratch = new EcosystemSystem();
        Cell[] cells = state.cells();
        int n = cells.length;
        scratch.ensureBuffers(n, state.regions.size());
        double[] fertility = new double[n];
        for (int i = 0; i < n; i++) {
            Cell c = cells[i];
            Region r = state.region(c.regionId);
            fertility[i] = r == null ? 1.0 : r.env.fertility;
            scratch.habitat[i] = habitatQuality(cfg, Ecology.forestCover(c));
            if (c.biome == Biome.WATER || Ecology.forageCapacity(c) <= 0) {
                c.herbivores = 0;
            } else if (c.herbivores <= 0) {
                c.herbivores = herbivoreEquilibrium(cfg, c, fertility[i]);
            }
        }
        Season[] seasons = Season.values();
        int perSeason = Math.max(1, state.daysPerSeason);
        int days = SETTLE_YEARS * seasons.length * perSeason;
        for (int d = 0; d < days; d++) {
            Season s = seasons[(d / perSeason) % seasons.length];
            for (int i = 0; i < n; i++) {
                Cell c = cells[i];
                growForage(cfg, c, fertility[i] * s.growth);
                scratch.fed[i] = breed(cfg, c, scratch.habitat[i], s.growth, fertility[i]);
            }
            scratch.migrate(cfg, state, cells);
        }
    }

    /**
     * Refreshes a region's totals and records them as the baselines that later
     * change (deforestation, hunting) is measured against.
     */
    public static void recordBaseline(WorldState state, Region r) {
        double coverSum = 0;
        double herbivoreSum = 0;
        for (int z = r.z0; z < r.z1; z++) {
            for (int x = r.x0; x < r.x1; x++) {
                Cell c = state.cell(x, z);
                if (c != null) {
                    coverSum += Ecology.forestCover(c);
                    herbivoreSum += c.herbivores;
                }
            }
        }
        r.forestCover = coverSum / Math.max(1, r.cellCount());
        r.herbivores = herbivoreSum;
        r.initialForestCover = r.forestCover;
        r.initialHerbivores = r.herbivores;
        r.forestDepleted = false;
        r.wildlifeDepleted = false;
    }

    private static boolean hasBaseline(Region r) {
        return r.initialForestCover > 0 || r.initialHerbivores > 0;
    }

    // ---- phase 1: per-cell growth --------------------------------------------------------

    private static void growTimber(EcoConfig cfg, Cell c, double g, double neighbourCover) {
        double capacity = Ecology.timberCapacity(c);
        double timber = c.timber;
        double growth = 0;
        if (capacity > 0 && timber < capacity) {
            double room = 1.0 - timber / capacity;
            double logistic = cfg.timberGrowthRate * g * timber * room;
            double seeding = cfg.timberGrowthRate * g * capacity * room
                    * (cfg.seedRainShare * neighbourCover + cfg.seedBankShare);
            growth = Math.max(0, logistic) + Math.max(0, seeding);
        }
        double matured = 0;
        if (c.saplings > 0) {
            matured = Math.min(c.saplings, c.saplings * cfg.saplingMaturation * g);
            c.saplings -= matured;
            if (c.saplings < 1e-6) {
                c.saplings = 0;
            }
        }
        double next = timber + growth + matured * cfg.logsPerSapling;
        if (next > capacity) {
            // Growth stops at capacity; a real forest denser than the model expects is left alone.
            next = Math.max(timber, capacity);
        }
        c.timber = next;
    }

    private static void growForage(EcoConfig cfg, Cell c, double g) {
        double capacity = Ecology.forageCapacity(c);
        double forage = c.forage;
        if (capacity > 0 && forage < capacity) {
            double room = 1.0 - forage / capacity;
            forage += cfg.forageGrowthRate * g * room * (forage + cfg.forageSeedShare * capacity);
        }
        c.forage = Math.min(Math.max(forage, 0), capacity);
    }

    /** Animals eat, breed and die for one day; returns the share of their food need that was met. */
    private static double breed(EcoConfig cfg, Cell c, double quality, double season, double fertility) {
        double h = c.herbivores;
        if (h <= 0) {
            c.herbivores = 0;
            return 1.0;
        }
        double need = h * cfg.herbivoreEatRate;
        double eaten = Math.min(c.forage, need);
        c.forage -= eaten;
        double fedShare = need > 0 ? eaten / need : 1.0;

        double capacity = capacityFor(cfg, c, quality, fertility);
        double room = capacity > 0 ? Math.max(0.0, 1.0 - h / capacity) : 0.0;
        double births = cfg.herbivoreBirthRate * h * fedShare * quality * season * room;
        double deaths = cfg.herbivoreBaseDeath * h + cfg.herbivoreStarvationDeath * h * (1.0 - fedShare);
        if (h > capacity) {
            deaths += cfg.herbivoreCrowdingDeath * (h - capacity);
        }
        h = Math.max(0, h + births - deaths);
        c.herbivores = h < cfg.extinctionThreshold ? 0 : h;
        return fedShare;
    }

    private static double capacityFor(EcoConfig cfg, Cell c, double habitatQuality, double fertility) {
        return Ecology.forageCapacity(c) * cfg.herbivoresPerForage * habitatQuality * Math.max(0.0, fertility);
    }

    private double neighbourCover(WorldState state, Cell c) {
        double sum = 0;
        int count = 0;
        for (int k = 0; k < NEIGHBOUR_DX.length; k++) {
            int x = c.x + NEIGHBOUR_DX[k];
            int z = c.z + NEIGHBOUR_DZ[k];
            if (state.inBounds(x, z)) {
                sum += cover[z * state.width + x];
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    // ---- phase 2: migration -------------------------------------------------------------

    private void migrate(EcoConfig cfg, WorldState state, Cell[] cells) {
        int n = cells.length;
        for (int i = 0; i < n; i++) {
            Cell c = cells[i];
            attraction[i] = c.biome == Biome.WATER ? -1.0 : habitat[i] * c.forage / (c.herbivores + 1.0);
            inflow[i] = 0;
            outflow[i] = 0;
        }
        boolean anyMoves = false;
        for (int i = 0; i < n; i++) {
            Cell c = cells[i];
            double h = c.herbivores;
            if (h <= 0) {
                continue;
            }
            boolean stranded = c.biome == Biome.WATER;
            boolean hungry = fed[i] < cfg.migrationFedThreshold;
            boolean exposed = habitat[i] < cfg.migrationHabitatThreshold;
            if (!stranded && !hungry && !exposed) {
                continue;
            }
            double bar = stranded ? 0.0 : attraction[i] * cfg.migrationAdvantage;
            int best = -1;
            for (int k = 0; k < NEIGHBOUR_DX.length; k++) {
                int x = c.x + NEIGHBOUR_DX[k];
                int z = c.z + NEIGHBOUR_DZ[k];
                if (!state.inBounds(x, z)) {
                    continue;
                }
                int j = z * state.width + x;
                if (attraction[j] > bar) {
                    bar = attraction[j];
                    best = j;
                }
            }
            if (best >= 0) {
                double move = stranded ? h : h * cfg.migrationRate;
                outflow[i] += move;
                inflow[best] += move;
                anyMoves = true;
            }
        }
        if (!anyMoves) {
            return;
        }
        for (int i = 0; i < n; i++) {
            if (outflow[i] != 0 || inflow[i] != 0) {
                Cell c = cells[i];
                c.herbivores = Math.max(0, c.herbivores - outflow[i] + inflow[i]);
            }
        }
    }

    // ---- region reporting -----------------------------------------------------------------

    private static void reportRegions(SimContext ctx, EcoConfig cfg) {
        for (Region r : ctx.state.regions) {
            if (r.initialForestCover > cfg.forestEventMinInitialCover) {
                double ratio = r.forestCover / r.initialForestCover;
                if (!r.forestDepleted && ratio < cfg.forestDepletedRatio) {
                    r.forestDepleted = true;
                    ctx.emit(EventType.FOREST_DEPLETED, -1, r.id, r.center(), String.format(Locale.ROOT,
                            "The forests of %s are thinning: %s cover, down from %s", r.name,
                            percent(r.forestCover), percent(r.initialForestCover)));
                } else if (r.forestDepleted && ratio > cfg.forestRecoveredRatio) {
                    r.forestDepleted = false;
                    ctx.emit(EventType.FOREST_RECOVERED, -1, r.id, r.center(), String.format(Locale.ROOT,
                            "The forests of %s have grown back to %s cover", r.name, percent(r.forestCover)));
                }
            }
            if (r.initialHerbivores > cfg.wildlifeEventMinInitial) {
                double ratio = r.herbivores / r.initialHerbivores;
                if (!r.wildlifeDepleted && ratio < cfg.wildlifeDeclineRatio) {
                    r.wildlifeDepleted = true;
                    ctx.emit(EventType.WILDLIFE_DECLINE, -1, r.id, r.center(), String.format(Locale.ROOT,
                            "Game is scarce in %s: about %d animals left of %d", r.name,
                            Math.round(r.herbivores), Math.round(r.initialHerbivores)));
                } else if (r.wildlifeDepleted && ratio > cfg.wildlifeRecoveredRatio) {
                    r.wildlifeDepleted = false;
                    ctx.emit(EventType.WILDLIFE_RECOVERY, -1, r.id, r.center(), String.format(Locale.ROOT,
                            "Wildlife has returned to %s: about %d animals", r.name, Math.round(r.herbivores)));
                }
            }
        }
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private static double meanSeasonGrowth() {
        double sum = 0;
        for (Season s : Season.values()) {
            sum += s.growth;
        }
        return sum / Season.values().length;
    }

    private void ensureBuffers(int cells, int regions) {
        if (cover.length != cells) {
            cover = new double[cells];
            fed = new double[cells];
            habitat = new double[cells];
            attraction = new double[cells];
            inflow = new double[cells];
            outflow = new double[cells];
        }
        if (regionGrowth.length < regions) {
            regionGrowth = new double[regions];
        }
    }
}
