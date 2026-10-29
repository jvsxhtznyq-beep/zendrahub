package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.MemoryEntry;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static com.zendrahub.evolve.core.civ.TestWorlds.HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DeterminismTest {

    private static String run(Supplier<WorldState> world, int days) {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = world.get();
        CivilizationSystem civ = new CivilizationSystem();
        TestWorlds.found(civ, st, cfg, "Oakridge", HOME, 20);
        TestWorlds.found(civ, st, cfg, "Stonebrook", new GridPos(19, 4), 14);
        List<WorldAction> actions = TestWorlds.run(civ, st, cfg, days);
        return fingerprint(st, actions);
    }

    static String fingerprint(WorldState st, List<WorldAction> actions) {
        StringBuilder sb = new StringBuilder();
        for (Settlement s : st.settlements) {
            sb.append(s.id).append(s.name).append(s.population).append(',').append(s.food).append(',')
                    .append(s.timber).append(',').append(s.stone).append(s.strategy).append(s.preferredMaterial)
                    .append(s.labor).append(s.abandoned).append('\n');
            for (MemoryEntry m : s.memory) {
                sb.append(m).append('\n');
            }
        }
        for (Building b : st.buildings) {
            sb.append(b.id).append(b.type).append(b.material).append(b.stories).append(b.cell)
                    .append(b.condition).append(b.progress).append(b.builtDay).append(b.abandoned).append('\n');
        }
        for (TradeRoute r : st.routes) {
            sb.append(r.id).append(r.fromId).append(r.toId).append(r.active).append(r.timberMoved).append('\n');
        }
        for (Cell c : st.cells()) {
            sb.append(c.timber).append(c.forage).append(c.herbivores).append(c.saplings).append(c.builtArea);
        }
        for (SimEvent e : st.events.all()) {
            sb.append(e).append(e.causeId()).append('\n');
        }
        for (WorldAction a : actions) {
            sb.append(a).append('\n');
        }
        return sb.toString();
    }

    @Test
    void identicalRunsAreIdentical() {
        assertEquals(run(TestWorlds::depletedForest, 150), run(TestWorlds::depletedForest, 150));
        assertEquals(run(TestWorlds::abundantForest, 150), run(TestWorlds::abundantForest, 150));
    }

    @Test
    void differentWorldsGiveDifferentHistories() {
        assertNotEquals(run(TestWorlds::depletedForest, 60), run(TestWorlds::abundantForest, 60));
    }
}
