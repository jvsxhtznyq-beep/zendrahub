package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.zendrahub.evolve.core.civ.TestWorlds.HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutpostTest {

    private static Settlement outpostOf(WorldState st, Settlement town) {
        for (Settlement s : st.settlements) {
            if (s.parentId == town.id) {
                return s;
            }
        }
        return null;
    }

    @Test
    void depletedTownFoundsAnOutpostInTheForest() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.depletedForest();
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
        List<WorldAction> actions = TestWorlds.run(civ, st, cfg, 1);

        Settlement outpost = outpostOf(st, town);
        assertNotNull(outpost, "an outpost should be founded on the first review");
        assertTrue(outpost.isOutpost());
        assertTrue(outpost.home.distance(town.home) >= cfg.civ.outpostMinDistance);
        assertTrue(LocalArea.standingTimber(st, outpost.home, cfg.civ.workRadius) >= cfg.civ.outpostMinStanding);
        assertEquals(20, town.population + outpost.population, "settlers come from the town");

        // The founding is caused by the strategy change, which records its scores.
        SimEvent founded = st.events.ofType(EventType.OUTPOST_FOUNDED).get(0);
        SimEvent changed = st.events.ofType(EventType.STRATEGY_CHANGED).get(0);
        assertEquals(changed.id(), founded.causeId());
        assertEquals(changed.id(), st.events.causalChain(founded.id()).get(0).id());
        assertTrue(changed.message().contains("OUTPOST"), changed.message());

        // A supply road joins them, and the host is told to lay it.
        TradeRoute road = Trade.supplyRoad(st, outpost);
        assertNotNull(road);
        assertTrue(actions.stream().anyMatch(a -> a instanceof WorldAction.LayRoad r && r.routeId() == road.id));
    }

    @Test
    void outpostShipsTimberHomeAlongItsSupplyRoad() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.depletedForest();
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
        TestWorlds.run(civ, st, cfg, 90);

        Settlement outpost = outpostOf(st, town);
        assertNotNull(outpost);
        assertFalse(outpost.abandoned, "the forest is far from logged out");
        assertTrue(outpost.timberHarvested > 50, "outpost logged " + outpost.timberHarvested);
        TradeRoute road = Trade.supplyRoad(st, outpost);
        assertNotNull(road);
        assertTrue(road.timberMoved > 0, "timber shipped home: " + road.timberMoved);
        assertTrue(st.buildings.stream().anyMatch(b -> b.settlementId == outpost.id && b.isComplete()),
                "the outpost raised its hall");
    }

    @Test
    void shippedTimberLosesAShareThatGrowsWithDistanceAndGravity() {
        CivConfig cfg = new CivConfig();
        assertTrue(Outposts.haulLoss(cfg, 4, 1.0) < Outposts.haulLoss(cfg, 8, 1.0));
        assertTrue(Outposts.haulLoss(cfg, 8, 1.0) < Outposts.haulLoss(cfg, 8, 1.5));
        assertEquals(cfg.maxHaulLoss, Outposts.haulLoss(cfg, 200, 1.8), 1e-12);

        SimConfig sim = SimConfig.defaults();
        WorldState st = TestWorlds.depletedForest();
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, sim, "Oakridge", HOME, 20);
        TestWorlds.run(civ, st, sim, 1);
        Settlement outpost = outpostOf(st, town);
        assertNotNull(outpost);
        // Finish its hall and give it a surplus so today's work is hauling.
        for (Building b : st.buildings) {
            if (b.settlementId == outpost.id && !b.isComplete()) {
                b.progress = 1;
                b.builtDay = st.day;
            }
        }
        outpost.timber = 300;
        town.timber = 0;

        SimContext ctx = TestWorlds.ctx(st, sim);
        Ledger ledger = new Ledger(st);
        LocalArea area = LocalArea.survey(ctx, outpost.home, sim.civ.workRadius);
        Labor.Workday work = Labor.work(ctx, outpost, area, Needs.assess(ctx, outpost, area, ledger), ledger);

        double loss = Outposts.haulLoss(sim.civ, outpost.home.distance(town.home),
                Outposts.routeGravity(st, outpost.home, town.home));
        assertTrue(work.shipped() > 0);
        assertEquals(work.shipped() * (1 - loss), work.delivered(), 1e-9);
        assertEquals(work.delivered(), town.timber, 1e-9);
        assertEquals(work.shipped(), Trade.supplyRoad(st, outpost).timberMoved, 1e-9);
    }

    @Test
    void outpostIsAbandonedOnceItsForestIsLoggedOut() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.depletedForest();
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
        TestWorlds.run(civ, st, cfg, 1);
        Settlement outpost = outpostOf(st, town);
        assertNotNull(outpost);
        int before = town.population + outpost.population;
        TestWorlds.setTimber(st, outpost.home, cfg.civ.workRadius, 1.0);
        outpost.timber = 0;
        TestWorlds.run(civ, st, cfg, 1);

        assertTrue(outpost.abandoned);
        assertEquals(0, outpost.population);
        assertTrue(town.population >= before - 1, "the settlers went home");
        assertFalse(Trade.supplyRoad(st, outpost) != null, "the supply road closed");
        assertFalse(st.events.ofType(EventType.OUTPOST_ABANDONED).isEmpty());
    }
}
