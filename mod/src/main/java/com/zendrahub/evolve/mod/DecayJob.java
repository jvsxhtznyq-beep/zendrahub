package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.GridPos;

import java.util.List;

/**
 * Shows a building's condition: weathering and collapse in proportion to the
 * damage, and at condition 0 the ruin's removal and reforestation.
 */
final class DecayJob implements WorldJob {
    private final WorldAction.Decay action;
    private List<Blueprint.Placement> plan;
    private int index;
    private boolean done;
    private boolean waiting;

    DecayJob(WorldAction.Decay action) {
        this.action = action;
    }

    @Override
    public long actionId() {
        return action.id();
    }

    @Override
    public GridPos busyCell() {
        return null;
    }

    @Override
    public int run(WorldAccess w, int budget) {
        waiting = false;
        Building b = w.state.building(action.buildingId());
        if (b == null || !b.hasAnchor) {
            done = true; // never built in this world: nothing to show
            return 0;
        }
        if (plan == null) {
            if (!w.footprintLoaded(b)) {
                waiting = true;
                return 0;
            }
            plan = Structures.decayPlan(w, b, action.condition());
        }
        Jobs.Progress progress = Jobs.place(w, plan, index, budget);
        index = progress.index();
        waiting = progress.waiting();
        if (index >= plan.size()) {
            done = true;
        }
        return progress.used();
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isWaiting() {
        return waiting;
    }
}
