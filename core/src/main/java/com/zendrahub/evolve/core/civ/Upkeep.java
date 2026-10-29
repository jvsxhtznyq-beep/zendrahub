package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.eco.Ecology;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.List;
import java.util.Locale;

/**
 * Wear, repair and ruin. Buildings lose condition at their region's wood decay
 * rate scaled by material (abandoned ones much faster); active settlements
 * repair them; at zero a building collapses and nature takes the site back.
 */
final class Upkeep {
    private static final double[] THRESHOLDS = {0.75, 0.5, 0.25};

    private Upkeep() {
    }

    static void run(SimContext ctx, Ledger ledger, List<SimEvent> today) {
        WorldState st = ctx.state;
        boolean[] stormRegions = new boolean[st.regions.size()];
        for (SimEvent e : today) {
            if (e.type() == EventType.STORM && e.regionId() >= 0 && e.regionId() < stormRegions.length) {
                stormRegions[e.regionId()] = true;
            }
        }
        // Index loop: ruins never add buildings, but stay robust if that changes.
        for (int i = 0; i < st.buildings.size(); i++) {
            Building b = st.buildings.get(i);
            if (b.isComplete() && b.condition > 0) {
                wear(ctx, b, ledger, stormRegions);
            }
        }
    }

    static double decayRate(CivConfig cfg, Building b, Region region) {
        double base = region == null ? 0.002 : region.env.woodDecay;
        double material = switch (b.material) {
            case TIMBER -> cfg.timberDecayFactor;
            case MUD_BRICK -> cfg.mudBrickDecayFactor;
            case STONE -> cfg.stoneDecayFactor;
        };
        return base * material * (b.abandoned ? cfg.abandonedDecayMultiplier : 1.0);
    }

    private static void wear(SimContext ctx, Building b, Ledger ledger, boolean[] stormRegions) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        Cell cell = st.cell(b.cell);
        Region region = st.region(cell.regionId);
        Settlement owner = st.settlement(b.settlementId);
        double before = b.condition;
        b.condition = Math.max(0, b.condition - decayRate(cfg, b, region));

        if (!b.abandoned && owner != null && !owner.abandoned && b.condition < cfg.repairThreshold) {
            double timber = Construction.repairTimber(b);
            double stone = Construction.repairStone(b);
            if (owner.timber >= timber && owner.stone >= stone) {
                owner.timber -= timber;
                owner.stone -= stone;
                b.condition = 1.0;
                if (before < THRESHOLDS[0]) {
                    ctx.construct(b.id);
                }
                return;
            }
        }
        if (b.condition <= 0) {
            ruin(ctx, b, owner, cell);
            return;
        }
        boolean reported = false;
        for (double t : THRESHOLDS) {
            if (before > t && b.condition <= t) {
                ctx.decay(b.id, b.condition);
                reported = true;
                break;
            }
        }
        boolean stormHit = cell.regionId >= 0 && cell.regionId < stormRegions.length && stormRegions[cell.regionId];
        if (!reported && stormHit && b.condition < 0.95) {
            ctx.decay(b.id, b.condition);
        }
    }

    /** The building collapses: its land is freed and saplings take root on the footprint. */
    static void ruin(SimContext ctx, Building b, Settlement owner, Cell cell) {
        b.condition = 0;
        b.abandoned = true;
        cell.builtArea = Math.max(0, cell.builtArea - Construction.footprintShare(b.type));
        int saplings = (int) Math.round(b.type.footprint * b.type.footprint / 10.0);
        Ecology.plant(cell, saplings);
        String ownerName = owner == null ? "an unknown settlement" : owner.name;
        SimEvent ruined = ctx.emit(EventType.BUILDING_RUINED, b.settlementId, cell.regionId, b.cell,
                String.format(Locale.ROOT, "the %s of %s has collapsed", Construction.describe(b), ownerName));
        ctx.emit(EventType.RECLAIMED, b.settlementId, cell.regionId, b.cell,
                String.format(Locale.ROOT, "nature reclaims the ruins at %s: %d saplings take root", b.cell,
                        saplings), ruined.id());
        ctx.decay(b.id, 0);
        ctx.plantSaplings(b.settlementId, b.cell, saplings);
    }
}
