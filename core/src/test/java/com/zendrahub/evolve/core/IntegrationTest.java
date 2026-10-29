package com.zendrahub.evolve.core;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.WorldState;
import com.zendrahub.evolve.core.persist.StateCodec;
import com.zendrahub.evolve.core.scenario.Scenarios;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three systems running together: determinism, persistence and genuine cross-system causation. */
class IntegrationTest {
    private static final long SEED = 20261029L;

    private static Simulation town(long seed, boolean depleted) {
        return Scenarios.forestTown(seed, depleted, SimConfig.defaults());
    }

    @Test
    void sameSeedSameHistory() {
        Simulation a = town(SEED, true);
        Simulation b = town(SEED, true);
        a.advance(150);
        b.advance(150);
        assertEquals(StateCodec.toJson(a.state()), StateCodec.toJson(b.state()));
    }

    @Test
    void savedAndResumedRunMatchesUninterruptedRun() {
        Simulation continuous = town(SEED, true);
        continuous.advance(70);
        Simulation resumed = new Simulation(StateCodec.fromJson(StateCodec.toJson(continuous.state())),
                SimConfig.defaults());
        continuous.advance(90);
        resumed.advance(90);
        assertEquals(StateCodec.toJson(continuous.state()), StateCodec.toJson(resumed.state()));
    }

    @Test
    void differentSeedsGiveDifferentWorlds() {
        Simulation a = town(1, false);
        Simulation b = town(2, false);
        a.advance(40);
        b.advance(40);
        assertFalse(StateCodec.toJson(a.state()).equals(StateCodec.toJson(b.state())));
    }

    /**
     * The milestone claim: the same code, from different starting forests,
     * leads towns down different strategic paths. Checked over many seeds so
     * it is a property of the rules, not of one lucky world.
     */
    @Test
    void depletedForestsPushTownsOffTheExpansionPath() {
        int seeds = 10;
        int abundantDeviated = 0;
        int depletedDeviated = 0;
        int diverged = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            Set<Strategy> a = strategiesUsed(town(seed, false), 200);
            Set<Strategy> b = strategiesUsed(town(seed, true), 200);
            if (!a.equals(EnumSet.of(Strategy.EXPAND))) {
                abundantDeviated++;
            }
            if (!b.equals(EnumSet.of(Strategy.EXPAND))) {
                depletedDeviated++;
            }
            if (!a.equals(b)) {
                diverged++;
            }
        }
        assertTrue(depletedDeviated > abundantDeviated,
                "depleted towns changed strategy in " + depletedDeviated + " worlds, abundant in " + abundantDeviated);
        assertTrue(depletedDeviated >= seeds / 2, "depleted towns changed strategy in only " + depletedDeviated);
        assertTrue(diverged >= seeds / 3, "histories diverged in only " + diverged + " of " + seeds + " worlds");
    }

    private static Set<Strategy> strategiesUsed(Simulation sim, int days) {
        Settlement home = sim.state().settlementByName(Scenarios.HOME_TOWN);
        Set<Strategy> used = EnumSet.of(home.strategy);
        for (int d = 0; d < days; d++) {
            sim.step();
            used.add(home.strategy);
        }
        return used;
    }

    /** Civilization changes the ecosystem: logging thins the home region's forest and its wildlife follows. */
    @Test
    void settlementsReshapeTheirEcosystem() {
        Simulation untouched = Simulation.generate(SEED, WorldGenOptions.defaults(), SimConfig.defaults());
        Simulation settled = town(SEED, false);
        Settlement home = settled.state().settlementByName(Scenarios.HOME_TOWN);
        Region region = settled.state().regionAt(home.home);
        untouched.advance(200);
        settled.advance(200);
        double coverUntouched = untouched.state().region(region.id).forestCover;
        double coverSettled = settled.state().region(region.id).forestCover;
        assertTrue(home.timberHarvested > 0, "the town never logged");
        assertTrue(coverSettled < coverUntouched,
                "forest cover with a town " + coverSettled + " vs. without " + coverUntouched);
    }

    /** Decisions are explained, and strategic turns can be traced back to their cause. */
    @Test
    void decisionsCarryScoresReasonsAndCauses() {
        Simulation sim = town(SEED, true);
        sim.advance(200);
        WorldState s = sim.state();
        Settlement home = s.settlementByName(Scenarios.HOME_TOWN);
        assertNotNull(home.lastDecision, "no strategic evaluation happened");
        assertEquals(Strategy.values().length, home.lastDecision.scores().size());
        assertFalse(home.lastDecision.reasons().isEmpty());
        // Every change of course states its scores and its main reason.
        List<SimEvent> changes = s.events.filter(e -> e.settlementId() == home.id
                && e.type() == EventType.STRATEGY_CHANGED);
        assertFalse(changes.isEmpty(), "a town in a depleted forest never changed course");
        for (SimEvent e : changes) {
            for (Strategy st : Strategy.values()) {
                assertTrue(e.message().contains(st.name()), "unexplained: " + e);
            }
        }
        // The first one answers the founding, and outposts and trade routes link to the decisions behind them.
        assertTrue(s.events.causalChain(changes.get(0).id()).size() > 1, "no cause for: " + changes.get(0));
        List<SimEvent> acts = s.events.filter(e -> (e.settlementId() == home.id
                && e.type() == EventType.TRADE_ROUTE_OPENED)
                || (e.type() == EventType.OUTPOST_FOUNDED && s.settlement(e.settlementId()) != null
                && s.settlement(e.settlementId()).parentId == home.id));
        for (SimEvent act : acts) {
            assertTrue(s.events.causalChain(act.id()).size() > 1, "no recorded cause for: " + act);
        }
    }

    /** The environment changes behaviour: lower gravity lets the same town build taller. */
    @Test
    void gravityShapesConstruction() {
        double low = meanStoreys(0.6);
        double high = meanStoreys(1.6);
        assertTrue(low > high, "mean storeys at gravity 0.6 = " + low + ", at 1.6 = " + high);
    }

    private static double meanStoreys(double gravity) {
        Simulation sim = town(SEED, false);
        Settlement home = sim.state().settlementByName(Scenarios.HOME_TOWN);
        Scenarios.setRegionGravity(sim.state(), home.home, gravity);
        sim.advance(200);
        double total = 0;
        int n = 0;
        for (Building b : sim.state().buildings) {
            if (b.settlementId == home.id && b.isComplete() && b.builtDay > 0) {
                total += b.stories;
                n++;
            }
        }
        assertTrue(n > 0, "no buildings completed at gravity " + gravity);
        return total / n;
    }

    /** A host sees every change as an action, and nothing is lost before it acknowledges. */
    @Test
    void hostModeKeepsActionsUntilAcknowledged() {
        Simulation sim = town(SEED, false);
        sim.setHeadless(false);
        // founding queued the starter buildings
        long constructs = sim.pendingActions().stream().filter(a -> a instanceof WorldAction.Construct).count();
        assertTrue(constructs > 0);
        sim.advance(30);
        List<WorldAction> pending = List.copyOf(sim.pendingActions());
        assertTrue(pending.stream().anyMatch(a -> a instanceof WorldAction.FellTrees), "no logging reached the host");
        for (WorldAction a : pending) {
            assertTrue(sim.acknowledge(a.id()));
        }
        assertTrue(sim.pendingActions().isEmpty());
        assertFalse(sim.acknowledge(pending.get(0).id()));
    }

    @Test
    void headlessRunsDiscardActionsButCountThem() {
        Simulation sim = town(SEED, false);
        sim.advance(30);
        assertTrue(sim.pendingActions().isEmpty());
        assertTrue(sim.discardedActions().getOrDefault(Simulation.ActionKind.FELL_TREES, 0L) > 0);
    }

    @Test
    void hostObservationsOverrideTheModel() {
        Simulation sim = town(SEED, false);
        Settlement home = sim.state().settlementByName(Scenarios.HOME_TOWN);
        sim.observeTimber(home.home, 3);
        assertEquals(3, sim.state().cell(home.home).timber, 1e-9);
        assertEquals(sim.state().day, sim.state().cell(home.home).lastObservedDay);
    }

    /** Outposts in low-gravity regions (tall buildings) still get their hall built. */
    @Test
    void outpostsBuildTheirHallsEvenWhereGravityIsLow() {
        Simulation sim = town(SEED, false);
        Settlement home = sim.state().settlementByName(Scenarios.HOME_TOWN);
        Scenarios.setRegionGravity(sim.state(), home.home, 0.45);
        sim.advance(240);
        WorldState s = sim.state();
        int checked = 0;
        for (Settlement o : s.settlements) {
            if (o.parentId != home.id || o.abandoned || s.day - o.foundedDay < 60) {
                continue;
            }
            checked++;
            boolean hall = s.buildings.stream().anyMatch(b -> b.settlementId == o.id
                    && b.type == com.zendrahub.evolve.core.model.BuildingType.OUTPOST_HALL && b.isComplete());
            assertTrue(hall, o.name + " (founded day " + o.foundedDay + ") has no finished hall");
        }
        assertTrue(checked > 0, "no outpost lasted long enough to check");
    }
}
