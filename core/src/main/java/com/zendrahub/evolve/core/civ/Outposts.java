package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.Locale;

/** Outposts: choosing sites, founding them, shipping timber home and abandoning them. */
final class Outposts {

    /** A place to log for a town, valued by reachable timber net of hauling loss. */
    record Site(GridPos cell, double standing, double distance, double loss, double value, boolean existing) {
    }

    private Outposts() {
    }

    /** Share of hauled timber lost on the way: grows with distance and gravity. */
    static double haulLoss(CivConfig cfg, double distance, double gravity) {
        return Math.min(cfg.maxHaulLoss, cfg.haulLossPerCell * distance * gravity);
    }

    /** Mean gravity of the two ends of a route. */
    static double routeGravity(WorldState st, GridPos a, GridPos b) {
        return (Construction.gravityAt(st, a) + Construction.gravityAt(st, b)) / 2.0;
    }

    static int activeOutposts(WorldState st, Settlement town) {
        int n = 0;
        for (Settlement o : st.settlements) {
            if (o.parentId == town.id && !o.abandoned) {
                n++;
            }
        }
        return n;
    }

    /** The town's working outpost valued as a site, or else the best new site, or null. */
    static Site currentOrBestSite(SimContext ctx, Settlement town, Needs needs, Ledger ledger) {
        WorldState st = ctx.state;
        Site current = null;
        for (Settlement o : st.settlements) {
            if (o.parentId == town.id && !o.abandoned) {
                Site s = value(ctx, town, o.home, needs, true);
                if (current == null || s.value() > current.value()) {
                    current = s;
                }
            }
        }
        return current != null ? current : bestSite(ctx, town, needs);
    }

    /** The best forested, buildable, unclaimed cell 4..12 cells from the town, or null. */
    static Site bestSite(SimContext ctx, Settlement town, Needs needs) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        Site best = null;
        for (int[] o : LocalArea.offsets(cfg.outpostMaxDistance)) {
            double distance = Math.sqrt(o[0] * o[0] + o[1] * o[1]);
            if (distance < cfg.outpostMinDistance || distance > cfg.outpostMaxDistance) {
                continue;
            }
            Cell c = st.cell(town.home.x() + o[0], town.home.z() + o[1]);
            if (c == null || !c.biome.buildable() || c.builtArea >= 0.5 || nearSettlement(st, c.pos(), cfg)) {
                continue;
            }
            double standing = LocalArea.standingTimber(st, c.pos(), cfg.workRadius);
            if (standing < cfg.outpostMinStanding) {
                continue;
            }
            Site s = value(ctx, town, c.pos(), needs, false);
            if (best == null || s.value() > best.value() + 1e-12) {
                best = s;
            }
        }
        return best;
    }

    private static Site value(SimContext ctx, Settlement town, GridPos cell, Needs needs, boolean existing) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        double distance = town.home.distance(cell);
        double standing = LocalArea.standingTimber(st, cell, cfg.workRadius);
        double loss = haulLoss(cfg, distance, routeGravity(st, town.home, cell));
        double value = Planner.clamp01(standing / (Math.max(0.5, needs.demandPerDay) * cfg.scarcityHorizonDays))
                * (1 - loss);
        return new Site(cell, standing, distance, loss, value, existing);
    }

    static boolean nearSettlement(WorldState st, GridPos pos, CivConfig cfg) {
        for (Settlement s : st.settlements) {
            if (!s.abandoned && s.home.chebyshev(pos) <= cfg.minSettlementSpacing) {
                return true;
            }
        }
        return false;
    }

    /** A town following the OUTPOST strategy founds an outpost when it has none to spare people for. */
    static void manageTown(SimContext ctx, CivilizationSystem civ, Settlement town, Needs needs, Ledger ledger,
                           long causeId) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        if (town.strategy != Strategy.OUTPOST || town.population < cfg.minPopulationForOutpost
                || activeOutposts(st, town) >= 1 + town.population / 40) {
            return;
        }
        for (Settlement o : st.settlements) {
            if (o.parentId == town.id && ctx.day() - o.foundedDay < cfg.outpostSpacingDays) {
                return;
            }
        }
        Site site = bestSite(ctx, town, needs);
        if (site == null || site.value() <= 0.05) {
            return;
        }
        int settlers = (int) Math.round(town.population * cfg.outpostSettlerShare);
        settlers = Math.max(cfg.outpostMinSettlers, Math.min(cfg.outpostMaxSettlers, settlers));
        if (town.population - settlers < cfg.outpostMinSettlers) {
            return;
        }
        long cause = causeId >= 0 ? causeId : Events.latest(st, town.id, EventType.STRATEGY_CHANGED, 500);
        Settlement outpost = civ.foundSettlement(ctx, name(st, town), site.cell(), settlers, town.id, cause);
        if (outpost != null) {
            Memories.add(ctx, town, MemoryKind.OUTPOST_FOUNDED, 0.3,
                    String.format(Locale.ROOT, "founded %s at %s", outpost.name, site.cell()));
        }
    }

    private static String name(WorldState st, Settlement town) {
        String base = town.name + " Camp";
        if (st.settlementByName(base) == null) {
            return base;
        }
        for (int i = 2; ; i++) {
            String candidate = base + " " + i;
            if (st.settlementByName(candidate) == null) {
                return candidate;
            }
        }
    }

    /**
     * Daily check for an outpost: abandoned when its people are gone, its site is
     * logged out, or its town no longer needs timber. Returns true if abandoned.
     */
    static boolean review(SimContext ctx, Settlement outpost, LocalArea area, Ledger ledger) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        Settlement parent = st.settlement(outpost.parentId);
        boolean parentGone = parent == null || parent.abandoned;
        if (outpost.population <= 0) {
            abandon(ctx, outpost, ledger, "its people are gone", true);
            return true;
        }
        if (area.standingTimber < cfg.outpostMinStanding && outpost.timber < 20) {
            abandon(ctx, outpost, ledger, String.format(Locale.ROOT, "the forest is logged out (%.0f logs left)",
                    area.standingTimber), outpost.timberHarvested < 200);
            return true;
        }
        if (!parentGone && parent.strategy != Strategy.OUTPOST
                && ctx.day() - outpost.foundedDay >= cfg.outpostMinDays) {
            LocalArea parentArea = LocalArea.survey(ctx, parent.home, cfg.workRadius);
            Needs parentNeeds = Needs.assess(ctx, parent, parentArea, ledger);
            if (Planner.scarcity(cfg, parentArea, parentNeeds) < 0.3) {
                abandon(ctx, outpost, ledger, parent.name + " no longer needs its timber", false);
                return true;
            }
        }
        return false;
    }

    static void abandon(SimContext ctx, Settlement outpost, Ledger ledger, String reason, boolean failure) {
        WorldState st = ctx.state;
        CivConfig cfg = ctx.config.civ;
        Settlement parent = st.settlement(outpost.parentId);
        outpost.abandoned = true;
        outpost.abandonedDay = ctx.day();
        if (parent != null && !parent.abandoned) {
            double keep = 1 - haulLoss(cfg, outpost.home.distance(parent.home),
                    routeGravity(st, outpost.home, parent.home));
            double cap = ledger.storage(parent, cfg);
            parent.population += outpost.population;
            parent.food = Math.min(cap, parent.food + outpost.food * keep);
            parent.timber = Math.min(cap, parent.timber + outpost.timber * keep);
            parent.stone = Math.min(cap, parent.stone + outpost.stone * keep);
        }
        outpost.population = 0;
        outpost.food = 0;
        outpost.timber = 0;
        outpost.stone = 0;
        for (Building b : ledger.of(outpost)) {
            b.abandoned = true;
            if (!b.isComplete()) {
                b.condition = 0;
            }
        }
        for (TradeRoute r : st.routes) {
            if (r.active && r.connects(outpost.id)) {
                Trade.close(ctx, r, outpost.name + " was abandoned", false);
            }
        }
        Region region = st.regionAt(outpost.home);
        ctx.emit(EventType.OUTPOST_ABANDONED, outpost.id, region == null ? -1 : region.id, outpost.home,
                String.format(Locale.ROOT, "%s is abandoned: %s", outpost.name, reason));
        if (parent != null && !parent.abandoned) {
            Memories.add(ctx, parent, MemoryKind.OUTPOST_ABANDONED, failure ? 0.8 : 0.2,
                    outpost.name + ": " + reason);
        }
    }
}
