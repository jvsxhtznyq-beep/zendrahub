package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.model.Decision;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.zendrahub.evolve.core.civ.TestWorlds.HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerTest {

    private static Settlement townAfterOneDay(WorldState st, int population) {
        SimConfig cfg = SimConfig.defaults();
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, population);
        TestWorlds.day(civ, st, cfg);
        return town;
    }

    @Test
    void abundantForestKeepsExpandingWhileDepletedForestLooksElsewhere() {
        Settlement rich = townAfterOneDay(TestWorlds.abundantForest(), 20);
        Settlement poor = townAfterOneDay(TestWorlds.depletedForest(), 20);

        assertEquals(Strategy.EXPAND, rich.strategy);
        assertNotEquals(Strategy.EXPAND, poor.strategy);
        assertTrue(EnumSet.of(Strategy.CONSERVE, Strategy.TRADE, Strategy.OUTPOST).contains(poor.strategy));
        // Same code, same town; only the forest differs, and the scores show why.
        assertTrue(rich.lastDecision.scores().get(Strategy.EXPAND)
                > poor.lastDecision.scores().get(Strategy.EXPAND) + 0.3);
    }

    @Test
    void populationChangesTheAnswerForTheSameDepletedForest() {
        // 20 people can spare settlers for an outpost; 10 cannot, and no partner exists, so they conserve.
        Settlement large = townAfterOneDay(TestWorlds.depletedForest(), 20);
        Settlement small = townAfterOneDay(TestWorlds.depletedForest(), 10);

        assertEquals(Strategy.OUTPOST, large.strategy);
        assertEquals(Strategy.CONSERVE, small.strategy);
        assertEquals(0.0, small.lastDecision.scores().get(Strategy.OUTPOST), 1e-12);
    }

    @Test
    void decisionCarriesEveryScoreAndItsReasons() {
        Settlement town = townAfterOneDay(TestWorlds.depletedForest(), 20);
        Decision d = town.lastDecision;
        assertNotNull(d);
        assertEquals(EnumSet.allOf(Strategy.class), d.scores().keySet());
        assertFalse(d.reasons().isEmpty());
        assertTrue(d.reasons().get(0).contains("scarcity"), d.reasons().get(0));
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("outpost site")), String.join("\n", d.reasons()));
        assertEquals(d.chosen(), town.strategy);
        assertEquals(1, d.day());
    }

    @Test
    void rememberedTimberShortageRaisesTheAppealOfConservation() {
        SimConfig cfg = SimConfig.defaults();
        WorldState plainWorld = TestWorlds.depletedForest();
        WorldState scarredWorld = TestWorlds.depletedForest();
        CivilizationSystem civ = new CivilizationSystem();
        Settlement plain = TestWorlds.found(civ, plainWorld, cfg, "Oakridge", HOME, 10);
        Settlement scarred = TestWorlds.found(civ, scarredWorld, cfg, "Oakridge", HOME, 10);
        Memories.add(TestWorlds.ctx(scarredWorld, cfg), scarred, MemoryKind.TIMBER_SHORTAGE, 0.6, "a cold winter");

        double without = civ.preview(TestWorlds.ctx(plainWorld, cfg), plain).scores().get(Strategy.CONSERVE);
        double with = civ.preview(TestWorlds.ctx(scarredWorld, cfg), scarred).scores().get(Strategy.CONSERVE);
        // The memory's direct bias, plus a little more: it also tilts building toward stone,
        // which lowers timber demand and lets regrowth cover more of it.
        assertTrue(with - without >= 0.25 * 0.6 - 1e-9, "conserve " + without + " -> " + with);
    }

    @Test
    void scarcityRisesAsTheForestShrinks() {
        SimConfig cfg = SimConfig.defaults();
        double previous = -1;
        for (double logs : new double[]{216, 120, 60, 20, 5}) {
            WorldState st = TestWorlds.abundantForest();
            TestWorlds.setTimber(st, HOME, 3, logs);
            CivilizationSystem civ = new CivilizationSystem();
            Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
            Ledger ledger = new Ledger(st);
            LocalArea area = LocalArea.survey(TestWorlds.ctx(st, cfg), town.home, cfg.civ.workRadius);
            double scarcity = Planner.scarcity(cfg.civ, area,
                    Needs.assess(TestWorlds.ctx(st, cfg), town, area, ledger));
            assertTrue(scarcity >= previous, "scarcity should not fall as logs drop to " + logs);
            previous = scarcity;
        }
        assertEquals(1.0, previous, 1e-9);
    }
}
