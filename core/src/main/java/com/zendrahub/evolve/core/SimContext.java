package com.zendrahub.evolve.core;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.WorldState;
import com.zendrahub.evolve.core.util.DetRandom;

/**
 * What a subsystem sees during one step: the state, the tuning, seeded
 * randomness keyed by the current day, and channels for recording history and
 * telling the host world what changed.
 */
public final class SimContext {
    public final WorldState state;
    public final SimConfig config;

    public SimContext(WorldState state, SimConfig config) {
        this.state = state;
        this.config = config;
    }

    public int day() {
        return state.day;
    }

    /** Uniform [0, 1) draw for this day; see {@link DetRandom} for stream id ranges. */
    public double random(long stream, long a, long b) {
        return DetRandom.unit(state.seed, state.day, stream, a, b);
    }

    public double random(long stream, long a) {
        return DetRandom.unit(state.seed, state.day, stream, a);
    }

    /** Rounds an expected count to a whole number without bias, using this day's randomness. */
    public int round(double expected, long stream, long a, long b) {
        return DetRandom.stochasticRound(expected, state.seed, state.day, stream, a, b);
    }

    public SimEvent emit(EventType type, int settlementId, int regionId, GridPos cell, String message,
                         long causeId) {
        int cx = cell == null ? -1 : cell.x();
        int cz = cell == null ? -1 : cell.z();
        return state.events.record(state.day, type, settlementId, regionId, cx, cz, message, causeId);
    }

    public SimEvent emit(EventType type, int settlementId, int regionId, GridPos cell, String message) {
        return emit(type, settlementId, regionId, cell, message, -1);
    }

    public void fellTrees(int settlementId, GridPos cell, int logs) {
        if (logs > 0) {
            queue(new WorldAction.FellTrees(state.nextActionId++, settlementId, cell, logs));
        }
    }

    public void plantSaplings(int settlementId, GridPos cell, int count) {
        if (count > 0) {
            queue(new WorldAction.PlantSaplings(state.nextActionId++, settlementId, cell, count));
        }
    }

    public void construct(int buildingId) {
        queue(new WorldAction.Construct(state.nextActionId++, buildingId));
    }

    public void decay(int buildingId, double condition) {
        queue(new WorldAction.Decay(state.nextActionId++, buildingId, condition));
    }

    public void layRoad(int routeId) {
        queue(new WorldAction.LayRoad(state.nextActionId++, routeId));
    }

    private void queue(WorldAction action) {
        state.pendingActions.add(action);
    }
}
