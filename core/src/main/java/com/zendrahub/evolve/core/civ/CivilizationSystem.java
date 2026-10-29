package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.SimSystem;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Decision;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Material;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.List;
import java.util.Locale;

/**
 * Settlement needs, planning, labour, construction, trade and outposts.
 *
 * <p>Each day every active settlement, in id order: notes storms and
 * environment shifts in its memory, reviews its strategy when due (every few
 * days, at once after a shortage or a shift in its region), acts on it (trade
 * routes, outposts), starts its next building, works, eats and burns
 * firewood, builds, and grows or shrinks. Then all buildings wear and are
 * repaired or collapse. Nothing here is scripted: the same rules produce
 * different histories from different world states.
 */
public final class CivilizationSystem implements SimSystem {
    /** Building index for the step in progress; rebuilt from the state each step. */
    private Ledger ledger;

    @Override
    public void step(SimContext ctx) {
        WorldState st = ctx.state;
        ledger = new Ledger(st);
        try {
            List<SimEvent> today = Events.today(st);
            int count = st.settlements.size();
            for (int i = 0; i < count; i++) {
                Settlement s = st.settlements.get(i);
                if (!s.abandoned) {
                    live(ctx, s, today);
                }
            }
            Upkeep.run(ctx, ledger, today);
        } finally {
            ledger = null;
        }
    }

    private void live(SimContext ctx, Settlement s, List<SimEvent> today) {
        CivConfig cfg = ctx.config.civ;
        boolean ownsBuildings = !ledger.of(s).isEmpty();
        long shift = Memories.observe(ctx, s, today, ownsBuildings);
        LocalArea area = LocalArea.survey(ctx, s.home, cfg.workRadius);
        Needs needs = Needs.assess(ctx, s, area, ledger);

        if (s.isOutpost()) {
            if (Outposts.review(ctx, s, area, ledger)) {
                return;
            }
            if (shift >= 0) {
                refreshStyle(ctx, s, area, shift);
            }
        } else {
            boolean first = s.lastDecision == null;
            boolean due = first || shift >= 0
                    || Math.floorMod(ctx.day() + s.id, cfg.strategyInterval) == 0;
            // A town's first review answers its founding: what it found around it.
            long reviewCause = shift >= 0 ? shift : (first ? foundingEvent(ctx.state, s) : -1);
            long changed = due ? review(ctx, s, area, needs, reviewCause) : -1;
            long cause = changed >= 0 ? changed : shift;
            Trade.manage(ctx, s, area, needs, ledger, cause);
            Outposts.manageTown(ctx, this, s, needs, ledger, cause);
        }

        if (needs.project == null && needs.plan != null) {
            Construction.tryStart(ctx, s, needs.plan, ledger, needs.firewoodPerDay * cfg.firewoodReserveDays);
        }
        needs = Needs.assess(ctx, s, area, ledger);
        Labor.Workday work = Labor.work(ctx, s, area, needs, ledger);
        if (!s.isOutpost()) {
            Trade.exchange(ctx, s, needs, ledger);
        }

        Population.eat(ctx, s, needs.foodPerDay);
        double burned = Math.min(s.timber, needs.firewoodPerDay);
        s.timber -= burned;
        long shortage = checkShortages(ctx, s, needs, needs.firewoodPerDay - burned);
        if (shortage >= 0 && !s.isOutpost() && s.population > 0) {
            LocalArea now = LocalArea.survey(ctx, s.home, cfg.workRadius);
            review(ctx, s, now, Needs.assess(ctx, s, now, ledger), shortage);
        }

        Construction.progress(ctx, s, work.builders(), ledger);
        Population.grow(ctx, s, ledger.housing(s), needs.reserveTarget);
        if (s.population <= 0) {
            if (s.isOutpost()) {
                Outposts.abandon(ctx, s, ledger, "its people are gone", true);
            } else {
                abandonTown(ctx, s);
            }
        }
    }

    /** The settlement's SETTLEMENT_FOUNDED event, or -1 if it has left the retained history. */
    private static long foundingEvent(WorldState st, Settlement s) {
        List<SimEvent> founded = st.events.filter(e -> e.type() == EventType.SETTLEMENT_FOUNDED
                && e.settlementId() == s.id);
        return founded.isEmpty() ? -1 : founded.get(founded.size() - 1).id();
    }

    /** Re-evaluates strategy and building style. Returns the STRATEGY_CHANGED event id, or -1. */
    private long review(SimContext ctx, Settlement s, LocalArea area, Needs needs, long causeId) {
        Decision d = Planner.evaluate(ctx, s, area, needs, ledger, causeId);
        Strategy previous = s.strategy;
        s.lastDecision = d;
        long changed = -1;
        if (d.chosen() != previous) {
            s.strategy = d.chosen();
            Cell home = ctx.state.cell(s.home);
            changed = ctx.emit(EventType.STRATEGY_CHANGED, s.id, home.regionId, s.home,
                    String.format(Locale.ROOT, "%s turns from %s to %s (%s): %s", s.name, previous, d.chosen(),
                            Planner.summary(d), d.reasons().get(0)), causeId).id();
        }
        refreshStyle(ctx, s, area, changed >= 0 ? changed : causeId);
        return changed;
    }

    private void refreshStyle(SimContext ctx, Settlement s, LocalArea area, long causeId) {
        BuildingStyle.Choice style = BuildingStyle.choose(ctx, s, BuildingType.HOUSE, area);
        if (style.material() != s.preferredMaterial) {
            Material old = s.preferredMaterial;
            s.preferredMaterial = style.material();
            Cell home = ctx.state.cell(s.home);
            ctx.emit(EventType.MATERIAL_CHANGED, s.id, home.regionId, s.home,
                    String.format(Locale.ROOT, "%s now builds in %s instead of %s: %s", s.name,
                            style.material(), old, String.join("; ", style.reasons())), causeId);
        }
    }

    /** Emits food and timber shortage events (rate-limited by memory); returns the event that should trigger a review. */
    private long checkShortages(SimContext ctx, Settlement s, Needs needs, double coldDeficit) {
        CivConfig cfg = ctx.config.civ;
        if (s.population <= 0) {
            return -1;
        }
        Cell home = ctx.state.cell(s.home);
        long trigger = -1;
        if (s.food < needs.foodPerDay * 3 && !Memories.recordedWithin(ctx, s, MemoryKind.FOOD_SHORTAGE,
                cfg.shortageCooldownDays)) {
            trigger = ctx.emit(EventType.FOOD_SHORTAGE, s.id, home.regionId, s.home,
                    String.format(Locale.ROOT, "%s is running out of food: %.0f left for %d people", s.name,
                            s.food, s.population)).id();
            Memories.add(ctx, s, MemoryKind.FOOD_SHORTAGE, 0.5, "food ran low");
        }
        // Only real scarcity counts: a short day while timber stands within easy reach is not a shortage.
        LocalArea area = LocalArea.survey(ctx, s.home, cfg.workRadius);
        double capacity = Labor.loggingCapacity(ctx, s, area, ledger);
        boolean cold = coldDeficit > 0.01 && capacity < needs.firewoodPerDay * 1.5;
        boolean blocked = needs.plan != null && needs.project == null && s.timber < needs.plan.timberCost()
                && s.timber + 5 * capacity < needs.plan.timberCost();
        if ((cold || blocked) && !Memories.recordedWithin(ctx, s, MemoryKind.TIMBER_SHORTAGE,
                cfg.shortageCooldownDays)) {
            String what = blocked
                    ? String.format(Locale.ROOT, "needs %.0f logs for a %s, has %.0f",
                    needs.plan.timberCost(), needs.plan.type().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                    s.timber)
                    : String.format(Locale.ROOT, "%.1f logs of firewood short", coldDeficit);
            trigger = ctx.emit(EventType.TIMBER_SHORTAGE, s.id, home.regionId, s.home,
                    s.name + " is short of timber: " + what).id();
            Memories.add(ctx, s, MemoryKind.TIMBER_SHORTAGE, 0.6, what);
        }
        return trigger;
    }

    private void abandonTown(SimContext ctx, Settlement s) {
        WorldState st = ctx.state;
        s.abandoned = true;
        s.abandonedDay = ctx.day();
        s.population = 0;
        for (Building b : ledger.of(s)) {
            b.abandoned = true;
            if (!b.isComplete()) {
                b.condition = 0;
            }
        }
        for (TradeRoute r : st.routes) {
            if (r.active && r.connects(s.id)) {
                Trade.close(ctx, r, s.name + " was abandoned", false);
            }
        }
        Cell home = st.cell(s.home);
        ctx.emit(EventType.SETTLEMENT_ABANDONED, s.id, home.regionId, s.home,
                String.format(Locale.ROOT, "%s is abandoned; its buildings are left to the weather", s.name));
    }

    /**
     * Founds a town (parentId -1) or an outpost of an existing town.
     *
     * @return the new settlement, or null if the site is invalid
     */
    public Settlement foundSettlement(SimContext ctx, String name, GridPos home, int population, int parentId,
                                      long causeId) {
        WorldState st = ctx.state;
        CivConfig cfg = ctx.config.civ;
        if (name == null || name.isBlank() || home == null || !st.inBounds(home) || population <= 0
                || st.settlementByName(name) != null) {
            return null;
        }
        Cell cell = st.cell(home);
        if (!cell.biome.buildable() || Outposts.nearSettlement(st, home, cfg)) {
            return null;
        }
        Settlement parent = null;
        if (parentId >= 0) {
            parent = st.settlement(parentId);
            if (parent == null || parent.abandoned || parent.isOutpost() || parent.population <= population) {
                return null;
            }
        }
        boolean inStep = ledger != null;
        Ledger index = inStep ? ledger : new Ledger(st);
        Settlement s = new Settlement(st.nextSettlementId(), name.trim(), parentId, home, ctx.day());
        st.settlements.add(s);
        s.population = population;
        s.peakPopulation = population;
        Region region = st.region(cell.regionId);
        String regionName = region == null ? "the wilds" : region.name;

        if (parent == null) {
            s.food = cfg.reserveDays * population * cfg.foodPerPerson;
            s.timber = 40 + population;
            s.stone = 20;
            BuildingStyle.Choice style = BuildingStyle.choose(ctx, s, BuildingType.HOUSE);
            s.preferredMaterial = style.material();
            ctx.emit(EventType.SETTLEMENT_FOUNDED, s.id, cell.regionId, home,
                    String.format(Locale.ROOT, "%s is founded at %s in %s with %d people, building in %s",
                            s.name, home, regionName, population, style.material()), causeId);
            // Starter buildings: a storehouse and too few houses, so growth needs construction.
            int houses = Math.max(1, population / 8);
            for (int i = 0; i <= houses; i++) {
                BuildingType type = i == 0 ? BuildingType.STOREHOUSE : BuildingType.HOUSE;
                GridPos site = Construction.siteFor(ctx, s, type);
                if (site != null) {
                    Construction.addComplete(ctx, s, type, style, site, index);
                }
            }
        } else {
            parent.population -= population;
            double food = Math.min(parent.food * 0.5, cfg.reserveDays * population * cfg.foodPerPerson);
            double hall = Construction.timberCost(BuildingType.OUTPOST_HALL, Material.TIMBER, 1);
            double timber = Math.min(parent.timber * 0.5, hall);
            double stone = Math.min(parent.stone * 0.5, Construction.stoneCost(BuildingType.OUTPOST_HALL,
                    Material.STONE, 1));
            parent.food -= food;
            parent.timber -= timber;
            parent.stone -= stone;
            s.food = food;
            s.timber = timber;
            s.stone = stone;
            s.strategy = Strategy.OUTPOST;
            BuildingStyle.Choice style = BuildingStyle.choose(ctx, s, BuildingType.OUTPOST_HALL);
            s.preferredMaterial = style.material();
            long founded = ctx.emit(EventType.OUTPOST_FOUNDED, s.id, cell.regionId, home,
                    String.format(Locale.ROOT, "%s sends %d people to found %s at %s in %s (%.1f cells away)",
                            parent.name, population, s.name, home, regionName, home.distance(parent.home)),
                    causeId).id();
            if (style.material() != parent.preferredMaterial) {
                ctx.emit(EventType.MATERIAL_CHANGED, s.id, cell.regionId, home,
                        String.format(Locale.ROOT, "%s adapts to %s: builds in %s, not %s like %s: %s", s.name,
                                regionName, style.material(), parent.preferredMaterial, parent.name,
                                String.join("; ", style.reasons())), founded);
            }
            Trade.openSupplyRoad(ctx, parent, s, founded);
            LocalArea area = LocalArea.survey(ctx, home, cfg.workRadius);
            Needs needs = Needs.assess(ctx, s, area, index);
            if (needs.plan != null) {
                Construction.tryStart(ctx, s, needs.plan, index, needs.firewoodPerDay * cfg.firewoodReserveDays);
            }
        }
        return s;
    }

    /** Scores the settlement's options as of now without changing anything (for inspection). */
    public Decision preview(SimContext ctx, Settlement s) {
        Ledger index = ledger != null ? ledger : new Ledger(ctx.state);
        LocalArea area = LocalArea.survey(ctx, s.home, ctx.config.civ.workRadius);
        return Planner.evaluate(ctx, s, area, Needs.assess(ctx, s, area, index), index, -1);
    }
}
