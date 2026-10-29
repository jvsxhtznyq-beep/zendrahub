package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.zendrahub.evolve.core.civ.TestWorlds.HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleTest {

    @Test
    void starvationKillsAndIsRemembered() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.uniform(24, 24, Biome.DESERT, 0.5);
        for (Cell c : st.cells()) {
            c.forage = 0;
            c.herbivores = 0;
        }
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, cfg, "Dustwell", HOME, 20);
        town.food = 0;
        TestWorlds.run(civ, st, cfg, 3);

        assertTrue(town.population < 20, "population " + town.population);
        assertFalse(st.events.ofType(EventType.STARVATION).isEmpty());
        assertTrue(Memories.recall(TestWorlds.ctx(st, cfg), town, MemoryKind.FAMINE) > 0);
        assertFalse(st.events.ofType(EventType.FOOD_SHORTAGE).isEmpty());
    }

    @Test
    void famineRaisesTheFoodReserveTarget() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.abundantForest();
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
        SimContext ctx = TestWorlds.ctx(st, cfg);
        LocalArea area = LocalArea.survey(ctx, town.home, cfg.civ.workRadius);
        double before = Needs.assess(ctx, town, area, new Ledger(st)).reserveTarget;
        Memories.add(ctx, town, MemoryKind.FAMINE, 1.0, "the hungry spring");
        double after = Needs.assess(ctx, town, area, new Ledger(st)).reserveTarget;
        assertEquals(before + cfg.civ.famineReserveBonus, after, 1e-9);
    }

    @Test
    void abandonedTownDecaysToRuinsThatNatureReclaims() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.abundantForest();
        TestWorlds.env(st, 1.0, 1.0, 0.03);
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
        List<Building> starters = st.buildings.stream().filter(b -> b.settlementId == town.id).toList();
        assertFalse(starters.isEmpty());
        town.population = 0;

        List<WorldAction> actions = TestWorlds.run(civ, st, cfg, 120);

        assertTrue(town.abandoned);
        assertFalse(st.events.ofType(EventType.SETTLEMENT_ABANDONED).isEmpty());
        for (Building b : starters) {
            assertEquals(0.0, b.condition, 1e-12, "building " + b.id + " should be a ruin");
            assertTrue(actions.stream().anyMatch(a -> a instanceof WorldAction.Decay d
                    && d.buildingId() == b.id && d.condition() == 0.0));
        }
        Cell home = st.cell(HOME);
        assertEquals(0.0, home.builtArea, 1e-9, "ruins free their land");
        assertTrue(home.saplings > 0, "saplings take root in the ruins");
        assertEquals(starters.size(), st.events.ofType(EventType.BUILDING_RUINED).size());
        for (SimEvent e : st.events.ofType(EventType.RECLAIMED)) {
            assertEquals(EventType.BUILDING_RUINED, st.events.byId(e.causeId()).type());
        }
        // Decay was shown on the way down, not only at the end.
        long partial = actions.stream().filter(a -> a instanceof WorldAction.Decay d && d.condition() > 0).count();
        assertTrue(partial >= starters.size());
    }

    @Test
    void activeTownsRepairWhatDecays() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.abundantForest();
        TestWorlds.env(st, 1.0, 1.0, 0.02);
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
        TestWorlds.run(civ, st, cfg, 60);
        for (Building b : st.buildings) {
            if (b.settlementId == town.id && b.isComplete()) {
                assertTrue(b.condition >= cfg.civ.repairThreshold - 0.03, "building " + b.id + " at " + b.condition);
            }
        }
    }

    @Test
    void foundingRejectsInvalidSites() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.abundantForest();
        st.cell(2, 2).biome = Biome.WATER;
        CivilizationSystem civ = new CivilizationSystem();
        SimContext ctx = TestWorlds.ctx(st, cfg);
        Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);

        assertNull(civ.foundSettlement(ctx, "Lake", new GridPos(2, 2), 10, -1, -1), "water");
        assertNull(civ.foundSettlement(ctx, "Crowded", HOME.offset(2, 1), 10, -1, -1), "too close");
        assertNull(civ.foundSettlement(ctx, "Oakridge", new GridPos(20, 20), 10, -1, -1), "duplicate name");
        assertNull(civ.foundSettlement(ctx, "Nowhere", new GridPos(30, 30), 10, -1, -1), "out of bounds");
        assertNull(civ.foundSettlement(ctx, "Orphan", new GridPos(20, 20), 4, 99, -1), "missing parent");
        assertNull(civ.foundSettlement(ctx, "Exodus", new GridPos(20, 20), 25, town.id, -1), "too many settlers");
        assertNotNull(civ.foundSettlement(ctx, "Farhaven", new GridPos(20, 20), 10, -1, -1));
    }
}
