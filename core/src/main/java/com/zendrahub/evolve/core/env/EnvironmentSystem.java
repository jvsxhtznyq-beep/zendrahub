package com.zendrahub.evolve.core.env;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.SimSystem;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.EnvironmentProfile;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Material;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Season;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.List;
import java.util.Locale;

/**
 * Evolves each region's environmental rules and produces weather.
 *
 * <p>Rules, applied to every region every day:
 * <ol>
 *   <li>Gravity anomalies oscillate around their base; stable gravity relaxes to base.</li>
 *   <li>Deforestation feedback: while a region holds less than half its original
 *       forest, its fertility target falls (erosion) and its storminess target
 *       rises (no windbreak), in proportion to the loss. Values approach their
 *       targets slowly, and return to base once the forest recovers.</li>
 *   <li>Storms strike with daily chance equal to storminess, knocking down
 *       timber and damaging buildings.</li>
 * </ol>
 * Every property stays within the bounds in {@link EnvironmentProfile}. Changes
 * are reported when a value crosses a band boundary, at most once per region per day.
 */
public final class EnvironmentSystem implements SimSystem {
    // Randomness streams 100-199 (see DetRandom).
    private static final long S_STORM = 100;
    private static final long S_STORM_SEVERITY = 101;
    private static final long S_STORM_CELL = 102;
    private static final long S_STORM_TREES = 103;
    private static final long S_STORM_BUILDING = 104;

    /** How far back to look for the forest loss that explains an erosion shift. */
    private static final int CAUSE_LOOKBACK_EVENTS = 500;

    @Override
    public void step(SimContext ctx) {
        WorldState state = ctx.state;
        EnvConfig cfg = ctx.config.env;
        reportSeason(ctx);

        List<Region> regions = state.regions;
        double[] severity = null;
        for (Region r : regions) {
            evolve(ctx, cfg, r);
            if (ctx.random(S_STORM, r.id) < r.env.storminess) {
                if (severity == null) {
                    severity = new double[regions.size()];
                }
                severity[r.id] = 0.5 + 0.5 * ctx.random(S_STORM_SEVERITY, r.id);
            }
        }
        if (severity != null) {
            storms(ctx, cfg, severity);
        }
    }

    /** Plant growth multiplier in a region today: its fertility times the season's growth. */
    public static double growthFactor(WorldState state, Region region) {
        return region.env.fertility * state.season().growth;
    }

    private static void reportSeason(SimContext ctx) {
        WorldState state = ctx.state;
        if (state.day <= 0) {
            return;
        }
        Season today = state.season();
        if (today != state.seasonOn(state.day - 1)) {
            ctx.emit(EventType.SEASON_CHANGED, -1, -1, null,
                    capitalise(today.name()) + " begins (year " + (state.year() + 1) + ")");
        }
    }

    private static void evolve(SimContext ctx, EnvConfig cfg, Region r) {
        EnvironmentProfile e = r.env;
        double oldGravity = e.gravity;
        double oldFertility = e.fertility;
        double oldStorminess = e.storminess;
        double oldWoodDecay = e.woodDecay;

        // 1. Gravity: anomalies follow their oscillation, stable regions relax to base.
        String fluxTurn = null;
        if (e.gravityFlux) {
            int day = ctx.day();
            double yesterday = fluxGravity(e, day - 1);
            double today = fluxGravity(e, day);
            double tomorrow = fluxGravity(e, day + 1);
            e.gravity = today;
            if (today > yesterday && today >= tomorrow) {
                fluxTurn = String.format(Locale.ROOT, "gravity peaks at %.2f and begins to fall", today);
            } else if (today < yesterday && today <= tomorrow) {
                fluxTurn = String.format(Locale.ROOT, "gravity bottoms out at %.2f and begins to rise", today);
            }
        } else {
            e.gravity += (e.baseGravity - e.gravity) * cfg.relaxRate;
        }

        // 2. Deforestation feedback on fertility and storminess.
        double fertilityTarget = e.baseFertility;
        double stormTarget = e.baseStorminess;
        double loss = deforestationLoss(cfg, r);
        if (loss > 0) {
            fertilityTarget = e.baseFertility * (1.0 - cfg.erosionMaxFertilityLoss * loss);
            stormTarget = e.baseStorminess + cfg.windbreakMaxStormGain * loss;
        }
        e.fertility += (fertilityTarget - e.fertility) * cfg.feedbackRate;
        e.storminess += (stormTarget - e.storminess) * cfg.feedbackRate;
        e.woodDecay += (e.baseWoodDecay - e.woodDecay) * cfg.relaxRate;
        e.clamp();

        reportShifts(ctx, cfg, r, oldGravity, oldFertility, oldStorminess, oldWoodDecay, loss > 0, fluxTurn);
    }

    /** Gravity of an anomalous region on a given day, within bounds. */
    public static double fluxGravity(EnvironmentProfile e, int day) {
        double g = e.baseGravity + e.fluxAmplitude * Math.sin(2 * Math.PI * day / e.fluxPeriodDays + e.fluxPhase);
        return EnvironmentProfile.clamp(g, EnvironmentProfile.GRAVITY_MIN, EnvironmentProfile.GRAVITY_MAX);
    }

    /**
     * How much of the deforestation-feedback effect applies: 0 while the region
     * keeps at least {@code deforestationThreshold} of its original cover, rising
     * to 1 when no forest is left.
     */
    public static double deforestationLoss(EnvConfig cfg, Region r) {
        if (r.initialForestCover <= cfg.deforestationMinInitialCover) {
            return 0;
        }
        double ratio = r.forestCover / r.initialForestCover;
        if (ratio >= cfg.deforestationThreshold) {
            return 0;
        }
        return Math.min(1.0, 1.0 - ratio / cfg.deforestationThreshold);
    }

    private static void reportShifts(SimContext ctx, EnvConfig cfg, Region r, double oldGravity,
                                     double oldFertility, double oldStorminess, double oldWoodDecay,
                                     boolean deforested, String fluxTurn) {
        EnvironmentProfile e = r.env;
        StringBuilder sb = new StringBuilder();
        boolean erosion = false;
        if (fluxTurn != null) {
            sb.append(fluxTurn);
        } else if (!e.gravityFlux && crossed(oldGravity, e.gravity, cfg.gravityShiftBand)) {
            append(sb, "gravity", oldGravity, e.gravity);
        }
        if (crossed(oldFertility, e.fertility, cfg.fertilityShiftBand)) {
            append(sb, "fertility", oldFertility, e.fertility);
            erosion |= deforested && e.fertility < oldFertility;
        }
        if (crossed(oldStorminess, e.storminess, cfg.stormShiftBand)) {
            append(sb, "storminess", oldStorminess, e.storminess);
            erosion |= deforested && e.storminess > oldStorminess;
        }
        if (crossed(oldWoodDecay, e.woodDecay, cfg.woodDecayShiftBand)) {
            append(sb, "wood decay", oldWoodDecay, e.woodDecay);
        }
        if (sb.isEmpty()) {
            return;
        }
        String message;
        long cause = -1;
        if (erosion) {
            message = "Deforestation is changing " + r.name + " (forest at "
                    + percent(r.forestCover / r.initialForestCover) + " of its original cover): " + sb;
            cause = latestForestLoss(ctx.state, r.id);
        } else {
            message = "Conditions shift in " + r.name + ": " + sb;
        }
        ctx.emit(EventType.ENVIRONMENT_SHIFT, -1, r.id, r.center(), message, cause);
    }

    private static long latestForestLoss(WorldState state, int regionId) {
        List<SimEvent> recent = state.events.recent(CAUSE_LOOKBACK_EVENTS);
        for (int i = recent.size() - 1; i >= 0; i--) {
            SimEvent ev = recent.get(i);
            if (ev.regionId() == regionId && ev.type() == EventType.FOREST_DEPLETED) {
                return ev.id();
            }
        }
        return -1;
    }

    private static void storms(SimContext ctx, EnvConfig cfg, double[] severity) {
        WorldState state = ctx.state;
        int regionCount = state.regions.size();
        double[] logsDown = new double[regionCount];

        // Timber knocked down, cell by cell, in every region with a storm today.
        for (Region r : state.regions) {
            double s = severity[r.id];
            if (s <= 0) {
                continue;
            }
            for (int z = r.z0; z < r.z1; z++) {
                for (int x = r.x0; x < r.x1; x++) {
                    int index = z * state.width + x;
                    if (ctx.random(S_STORM_CELL, r.id, index) >= cfg.stormCellChance) {
                        continue;
                    }
                    Cell c = state.cell(x, z);
                    if (c.timber <= 0) {
                        continue;
                    }
                    double share = cfg.stormMinTimberLoss + (cfg.stormMaxTimberLoss - cfg.stormMinTimberLoss)
                            * ctx.random(S_STORM_TREES, r.id, index);
                    double lost = c.timber * share;
                    c.timber -= lost;
                    logsDown[r.id] += lost;
                    int logs = (int) Math.round(lost);
                    if (logs > 0) {
                        ctx.fellTrees(-1, new GridPos(x, z), logs);
                    }
                }
            }
        }

        // Building damage, one pass over all buildings.
        int[] damagedInRegion = new int[regionCount];
        int settlementSlots = state.settlements.size() + 1;
        int[] damagedBySettlement = new int[settlementSlots];
        double[] lossBySettlement = new double[settlementSlots];
        int[] regionOfSettlementDamage = new int[settlementSlots];
        for (Building b : state.buildings) {
            if (!b.isComplete() || b.abandoned || b.condition <= cfg.stormConditionFloor) {
                continue;
            }
            Region r = state.regionAt(b.cell);
            if (r == null || severity[r.id] <= 0) {
                continue;
            }
            double vulnerability = vulnerability(cfg, b.material) * (1.0 + cfg.storeyVulnerability
                    * Math.max(0, b.stories - 1));
            double damage = cfg.stormBuildingDamage * severity[r.id] * vulnerability
                    * (0.5 + ctx.random(S_STORM_BUILDING, b.id));
            double before = b.condition;
            b.condition = Math.max(cfg.stormConditionFloor, b.condition - damage);
            damagedInRegion[r.id]++;
            if (b.settlementId > 0 && b.settlementId < settlementSlots) {
                damagedBySettlement[b.settlementId]++;
                lossBySettlement[b.settlementId] += before - b.condition;
                regionOfSettlementDamage[b.settlementId] = r.id;
            }
        }

        long[] stormEventId = new long[regionCount];
        for (Region r : state.regions) {
            if (severity[r.id] <= 0) {
                continue;
            }
            String message = String.format(Locale.ROOT,
                    "A storm (strength %.2f) sweeps %s: %d logs of timber blown down, %d buildings damaged",
                    severity[r.id], r.name, Math.round(logsDown[r.id]), damagedInRegion[r.id]);
            stormEventId[r.id] = ctx.emit(EventType.STORM, -1, r.id, r.center(), message).id();
        }
        for (int sid = 1; sid < settlementSlots; sid++) {
            if (damagedBySettlement[sid] == 0) {
                continue;
            }
            Settlement s = state.settlement(sid);
            int regionId = regionOfSettlementDamage[sid];
            String message = String.format(Locale.ROOT, "The storm damaged %d buildings of %s (condition -%.2f on average)",
                    damagedBySettlement[sid], s == null ? "settlement " + sid : s.name,
                    lossBySettlement[sid] / damagedBySettlement[sid]);
            ctx.emit(EventType.BUILDING_DAMAGED, sid, regionId, s == null ? null : s.home, message,
                    stormEventId[regionId]);
        }
    }

    /** Storm vulnerability of a material: timber worst, then mud brick, stone least. */
    public static double vulnerability(EnvConfig cfg, Material m) {
        return switch (m) {
            case TIMBER -> cfg.timberVulnerability;
            case MUD_BRICK -> cfg.mudBrickVulnerability;
            case STONE -> cfg.stoneVulnerability;
        };
    }

    private static boolean crossed(double before, double after, double band) {
        return band > 0 && Math.floor(before / band) != Math.floor(after / band);
    }

    private static void append(StringBuilder sb, String what, double before, double after) {
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(what).append(' ').append(format(before)).append(" -> ").append(format(after));
    }

    private static String format(double v) {
        return v < 0.1 ? String.format(Locale.ROOT, "%.3f", v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private static String capitalise(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
