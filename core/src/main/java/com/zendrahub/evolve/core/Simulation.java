package com.zendrahub.evolve.core;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.civ.CivilizationSystem;
import com.zendrahub.evolve.core.eco.EcosystemSystem;
import com.zendrahub.evolve.core.env.EnvironmentSystem;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Runs a world one day at a time. Each day the environment changes first,
 * then the ecosystem responds to it, then settlements plan and act on both.
 * The three subsystems share nothing but the world state, so every
 * interaction between them is visible there and in the event log.
 *
 * <p>A run is deterministic: the same state and config always produce the same
 * next state, whether the run continued in memory or was saved and loaded.
 */
public final class Simulation {
    /** Kinds of world action, for headless statistics. */
    public enum ActionKind { FELL_TREES, PLANT_SAPLINGS, CONSTRUCT, DECAY, LAY_ROAD }

    private final WorldState state;
    private final SimConfig config;
    private final EnvironmentSystem environment = new EnvironmentSystem();
    private final EcosystemSystem ecosystem = new EcosystemSystem();
    private final CivilizationSystem civilization = new CivilizationSystem();

    private boolean headless = true;
    private final Map<ActionKind, Long> discardedActions = new EnumMap<>(ActionKind.class);

    public Simulation(WorldState state, SimConfig config) {
        this.state = state;
        this.config = config;
    }

    public static Simulation generate(long seed, WorldGenOptions options, SimConfig config) {
        return new Simulation(WorldGen.generate(seed, options), config);
    }

    public WorldState state() {
        return state;
    }

    public SimConfig config() {
        return config;
    }

    /** A context for queries and host-initiated changes between steps. */
    public SimContext context() {
        return new SimContext(state, config);
    }

    /**
     * Headless runs (the default) count and discard world actions after every
     * step. A host that renders the world turns this off and acknowledges the
     * actions it has carried out.
     */
    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public boolean isHeadless() {
        return headless;
    }

    /** Simulates one day. */
    public void step() {
        state.day++;
        SimContext ctx = new SimContext(state, config);
        environment.step(ctx);
        ecosystem.step(ctx);
        civilization.step(ctx);
        settleActions();
    }

    public void advance(int days) {
        for (int i = 0; i < days; i++) {
            step();
        }
    }

    /** Actions the host has not yet acknowledged, oldest first. */
    public List<WorldAction> pendingActions() {
        return Collections.unmodifiableList(state.pendingActions);
    }

    /** Marks an action as carried out by the host. Returns false if it was not pending. */
    public boolean acknowledge(long actionId) {
        for (Iterator<WorldAction> it = state.pendingActions.iterator(); it.hasNext(); ) {
            if (it.next().id() == actionId) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Founds an independent town at a cell, as a player or host would. Null if the site is invalid. */
    public Settlement foundSettlement(String name, GridPos home, int population) {
        return civilization.foundSettlement(context(), name, home, population, -1, -1);
    }

    /** Reports the real standing timber the host measured in a cell. */
    public void observeTimber(GridPos cell, double measuredLogs) {
        if (state.inBounds(cell) && measuredLogs >= 0) {
            ecosystem.observeTimber(context(), cell, measuredLogs);
        }
    }

    /** Headless only: how many actions of each kind were produced and discarded so far. */
    public Map<ActionKind, Long> discardedActions() {
        return Collections.unmodifiableMap(discardedActions);
    }

    private void settleActions() {
        List<WorldAction> pending = state.pendingActions;
        if (headless) {
            for (WorldAction a : pending) {
                discardedActions.merge(kindOf(a), 1L, Long::sum);
            }
            pending.clear();
            return;
        }
        // A host that cannot keep up loses the oldest cosmetic actions first.
        for (Iterator<WorldAction> it = pending.iterator();
             it.hasNext() && pending.size() > config.maxPendingActions; ) {
            WorldAction a = it.next();
            if (!(a instanceof WorldAction.Construct) && !(a instanceof WorldAction.LayRoad)) {
                it.remove();
            }
        }
    }

    public static ActionKind kindOf(WorldAction a) {
        return switch (a) {
            case WorldAction.FellTrees f -> ActionKind.FELL_TREES;
            case WorldAction.PlantSaplings p -> ActionKind.PLANT_SAPLINGS;
            case WorldAction.Construct c -> ActionKind.CONSTRUCT;
            case WorldAction.Decay d -> ActionKind.DECAY;
            case WorldAction.LayRoad r -> ActionKind.LAY_ROAD;
        };
    }
}
