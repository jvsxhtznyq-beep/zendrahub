package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeTest {
    private static final GridPos BUYER = new GridPos(6, 12);
    private static final GridPos SELLER = new GridPos(16, 12);

    /** Bare plains everywhere except a forest around the seller, who has timber to spare. */
    private static WorldState world() {
        WorldState st = TestWorlds.uniform(24, 24, Biome.PLAINS, 0.1);
        TestWorlds.setBiome(st, SELLER, 2, Biome.FOREST, 0.9);
        return st;
    }

    @Test
    void routeOpensToASupplierAndClosesWhenItsTimberRunsOut() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = world();
        CivilizationSystem civ = new CivilizationSystem();
        // Ten people cannot spare settlers for an outpost, so trade is the way to get timber.
        Settlement buyer = TestWorlds.found(civ, st, cfg, "Ashford", BUYER, 10);
        Settlement seller = TestWorlds.found(civ, st, cfg, "Timberholt", SELLER, 20);
        seller.timber = 300;

        List<WorldAction> actions = TestWorlds.run(civ, st, cfg, 2);
        assertEquals(Strategy.TRADE, buyer.strategy, String.join("\n", buyer.lastDecision.reasons()));
        TradeRoute route = Trade.activeRequestRoute(st, buyer);
        assertNotNull(route);
        assertEquals(seller.id, route.toId);
        assertTrue(actions.stream().anyMatch(a -> a instanceof WorldAction.LayRoad r && r.routeId() == route.id));
        assertTrue(route.timberMoved > 0, "timber flowed on the first days");
        assertTrue(route.foodMoved + route.stoneMoved > 0, "the buyer paid");
        assertFalse(st.events.ofType(EventType.TRADE_ROUTE_OPENED).isEmpty());

        // The supplier's stock and forest are gone: deliveries stop and the route fails.
        seller.timber = 0;
        TestWorlds.setTimber(st, SELLER, 2, 0);
        TestWorlds.run(civ, st, cfg, cfg.civ.tradeFailDays + 3);

        assertFalse(route.active);
        assertTrue(route.closedDay > 0);
        assertFalse(st.events.ofType(EventType.TRADE_ROUTE_CLOSED).isEmpty());
        assertTrue(Memories.recall(TestWorlds.ctx(st, cfg), buyer, MemoryKind.TRADE_FAILURE) > 0);
    }

    @Test
    void failedTradeMakesTradeLessAttractiveNextTime() {
        SimConfig cfg = SimConfig.defaults();
        WorldState fresh = world();
        WorldState burned = world();
        CivilizationSystem civ = new CivilizationSystem();
        Settlement a = TestWorlds.found(civ, fresh, cfg, "Ashford", BUYER, 10);
        Settlement b = TestWorlds.found(civ, burned, cfg, "Ashford", BUYER, 10);
        TestWorlds.found(civ, fresh, cfg, "Timberholt", SELLER, 20).timber = 300;
        TestWorlds.found(civ, burned, cfg, "Timberholt", SELLER, 20).timber = 300;
        Memories.add(TestWorlds.ctx(burned, cfg), b, MemoryKind.TRADE_FAILURE, 0.6, "they let us down");

        double trust = civ.preview(TestWorlds.ctx(fresh, cfg), a).scores().get(Strategy.TRADE);
        double wary = civ.preview(TestWorlds.ctx(burned, cfg), b).scores().get(Strategy.TRADE);
        assertTrue(trust > 0);
        assertEquals(trust * (1 - 0.5 * 0.6), wary, 1e-9);
    }
}
