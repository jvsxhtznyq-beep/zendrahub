package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Job;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.zendrahub.evolve.core.civ.TestWorlds.HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConserveTest {

    private record Day(double logs, double saplingsAdded, int plantActions, int replanters, WorldState world,
                       double[] before) {
    }

    private static Day oneDay(Strategy strategy) {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.uniform(24, 24, Biome.FOREST, 0.3);
        CivilizationSystem civ = new CivilizationSystem();
        Settlement town = TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
        town.timber = 0;
        town.food = 300;
        TestWorlds.pinStrategy(town, strategy, cfg);
        double[] before = new double[st.cells().length];
        for (int i = 0; i < before.length; i++) {
            before[i] = st.cells()[i].timber;
        }
        double saplings = TestWorlds.totalSaplings(st);
        List<WorldAction> actions = TestWorlds.run(civ, st, cfg, 1);
        int plantActions = 0;
        double planted = 0;
        for (WorldAction a : actions) {
            if (a instanceof WorldAction.PlantSaplings p) {
                plantActions++;
                planted += p.count();
            }
        }
        assertEquals(TestWorlds.totalSaplings(st) - saplings, planted, 1e-9, "actions report every sapling");
        return new Day(town.timberHarvested, TestWorlds.totalSaplings(st) - saplings, plantActions,
                town.labor.get(Job.REPLANT), st, before);
    }

    @Test
    void conservingLogsLessAndReplants() {
        Day expand = oneDay(Strategy.EXPAND);
        Day conserve = oneDay(Strategy.CONSERVE);

        assertTrue(expand.logs() > 0);
        assertTrue(conserve.logs() > 0, "conserving still cuts what regrows");
        assertTrue(conserve.logs() < expand.logs(), conserve.logs() + " vs " + expand.logs());
        assertEquals(0, expand.saplingsAdded(), 1e-9);
        assertTrue(conserve.saplingsAdded() > 0);
        assertTrue(conserve.plantActions() > 0);
        assertTrue(conserve.replanters() > 0);
    }

    @Test
    void conservingNeverTakesMoreThanASmallShareOfAnyStand() {
        Day conserve = oneDay(Strategy.CONSERVE);
        Cell[] cells = conserve.world().cells();
        double share = new CivConfig().selectiveLoggingShare;
        for (int i = 0; i < cells.length; i++) {
            double cut = conserve.before()[i] - cells[i].timber;
            assertTrue(cut <= conserve.before()[i] * share + 1e-9, "cell " + i + " lost " + cut);
        }
    }

    @Test
    void expandingClearsTheNearestStandsFirst() {
        Day expand = oneDay(Strategy.EXPAND);
        WorldState st = expand.world();
        Cell home = st.cell(HOME);
        Cell edge = st.cell(HOME.x() + 2, HOME.z() + 2);
        assertTrue(home.timber < expand.before()[HOME.z() * st.width + HOME.x()]);
        assertEquals(expand.before()[edge.z * st.width + edge.x], edge.timber, 1e-9);
    }
}
