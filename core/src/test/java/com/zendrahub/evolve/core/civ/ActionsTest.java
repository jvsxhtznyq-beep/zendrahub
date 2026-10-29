package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.zendrahub.evolve.core.civ.TestWorlds.HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** World actions must describe exactly the changes the simulation made. */
class ActionsTest {

    @Test
    void actionsMatchStateChanges() {
        for (WorldState st : new WorldState[]{TestWorlds.abundantForest(), TestWorlds.depletedForest()}) {
            SimConfig cfg = SimConfig.defaults();
            CivilizationSystem civ = new CivilizationSystem();
            double timberBefore = TestWorlds.totalTimber(st);
            Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
            List<WorldAction> actions = TestWorlds.run(civ, st, cfg, 80);

            double felled = 0;
            int fellActions = 0;
            double planted = 0;
            Set<Integer> constructed = new HashSet<>();
            Set<Long> ids = new HashSet<>();
            for (WorldAction a : actions) {
                assertTrue(ids.add(a.id()), "action ids are unique");
                switch (a) {
                    case WorldAction.FellTrees f -> {
                        felled += f.logs();
                        fellActions++;
                        assertTrue(f.logs() > 0);
                        assertNotNull(st.settlement(f.settlementId()));
                    }
                    case WorldAction.PlantSaplings p -> planted += p.count();
                    case WorldAction.Construct c -> {
                        Building b = st.building(c.buildingId());
                        assertNotNull(b);
                        assertTrue(b.isComplete(), "construct refers to a finished building");
                        constructed.add(b.id);
                    }
                    case WorldAction.Decay d -> assertNotNull(st.building(d.buildingId()));
                    case WorldAction.LayRoad r -> assertNotNull(st.route(r.routeId()));
                }
            }
            double removed = timberBefore - TestWorlds.totalTimber(st);
            assertTrue(Math.abs(felled - removed) <= 0.5 * fellActions + 1,
                    "felled " + felled + " vs removed " + removed);
            assertEquals(TestWorlds.totalSaplings(st), planted, 1e-9);
            for (Building b : st.buildings) {
                if (b.isComplete()) {
                    assertTrue(constructed.contains(b.id), "completed building " + b.id + " was shown");
                }
            }
            assertFalse(constructed.isEmpty());
            assertTrue(town.timberHarvested > 0);
        }
    }
}
