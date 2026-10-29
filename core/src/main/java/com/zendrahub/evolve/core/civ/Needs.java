package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Season;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.TradeRoute;

/** A settlement's requirements today. Pure assessment: computing it changes nothing. */
final class Needs {
    final double foodPerDay;
    /** Food reserve target, in days. */
    final double reserveTarget;
    final double firewoodPerDay;
    /** Firewood per day averaged over the year (winter burns more). */
    final double firewoodAverage;
    final int housing;
    /** People beyond housing capacity, plus room for two more. */
    final int housingGap;
    final double storageCap;
    /** Building under construction, or null. */
    final Building project;
    /** What to build next when nothing is under construction, or null. */
    final Construction.Plan plan;
    final double repairTimber;
    final double repairStone;
    /** Timber promised per day to trade partners this settlement supplies. */
    final double exportPerDay;
    /** Timber wanted in stock soon: ten days of firewood, the next project, repairs, exports. */
    final double timberNeedSoon;
    final double stoneNeedSoon;
    /** Timber the settlement expects to consume per day, for strategic planning. */
    final double demandPerDay;

    private Needs(double foodPerDay, double reserveTarget, double firewoodPerDay, double firewoodAverage,
                  int housing, int housingGap, double storageCap, Building project, Construction.Plan plan,
                  double repairTimber, double repairStone, double exportPerDay, double timberNeedSoon,
                  double stoneNeedSoon, double demandPerDay) {
        this.foodPerDay = foodPerDay;
        this.reserveTarget = reserveTarget;
        this.firewoodPerDay = firewoodPerDay;
        this.firewoodAverage = firewoodAverage;
        this.housing = housing;
        this.housingGap = housingGap;
        this.storageCap = storageCap;
        this.project = project;
        this.plan = plan;
        this.repairTimber = repairTimber;
        this.repairStone = repairStone;
        this.exportPerDay = exportPerDay;
        this.timberNeedSoon = timberNeedSoon;
        this.stoneNeedSoon = stoneNeedSoon;
        this.demandPerDay = demandPerDay;
    }

    static Needs assess(SimContext ctx, Settlement s, LocalArea area, Ledger ledger) {
        CivConfig cfg = ctx.config.civ;
        double foodPerDay = s.population * cfg.foodPerPerson;
        double reserve = cfg.reserveDays + cfg.famineReserveBonus * Memories.recall(ctx, s, MemoryKind.FAMINE);
        double firewoodBase = s.population * cfg.firewoodPerPerson;
        double firewood = ctx.state.season() == Season.WINTER
                ? firewoodBase * cfg.winterFirewoodMultiplier : firewoodBase;
        double firewoodAverage = firewoodBase * (1 + (cfg.winterFirewoodMultiplier - 1) / 4.0);
        int housing = ledger.housing(s);
        int gap = Math.max(0, s.population + 2 - housing);
        double cap = ledger.storage(s, cfg);
        Building project = ledger.project(s);
        Construction.Plan plan = project == null
                ? Construction.nextPlan(ctx, s, area, ledger, foodPerDay, reserve, housing, cap) : null;

        double repairTimber = 0;
        double repairStone = 0;
        for (Building b : ledger.of(s)) {
            if (b.isActive() && b.condition < 0.85) {
                repairTimber += Construction.repairTimber(b);
                repairStone += Construction.repairStone(b);
            }
        }
        double export = 0;
        for (TradeRoute r : ctx.state.routes) {
            if (r.active && r.toId == s.id && !Trade.isSupplyRoad(ctx.state, r)) {
                export += cfg.tradeMaxPerDay * 0.5;
            }
        }
        double planTimber = plan == null ? 0 : plan.timberCost();
        double planStone = plan == null ? 0 : plan.stoneCost();
        double timberSoon = firewood * 10 + planTimber + repairTimber + export * 5;
        double stoneSoon = planStone + repairStone;

        // Planning demand: firewood, the houses the housing gap will take within a month, exports, upkeep.
        double houseTimber = Construction.timberCost(BuildingType.HOUSE, s.preferredMaterial, 1);
        double housesNeeded = Math.ceil(gap / 4.0);
        double demand = firewoodAverage + (housesNeeded * houseTimber + planTimber + repairTimber) / 30.0 + export;
        return new Needs(foodPerDay, reserve, firewood, firewoodAverage, housing, gap, cap, project, plan,
                repairTimber, repairStone, export, timberSoon, stoneSoon, demand);
    }
}
