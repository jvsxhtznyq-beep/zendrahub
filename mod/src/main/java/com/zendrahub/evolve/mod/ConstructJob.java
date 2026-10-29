package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.GridPos;

import java.util.List;

/**
 * Raises a building: picks and records a site the first time, then places the
 * blueprint a budget at a time. Re-running after a restart reuses the stored
 * anchor and skips blocks that are already in place.
 */
final class ConstructJob implements WorldJob {
    private final WorldAction.Construct action;
    private List<Blueprint.Placement> plan;
    private int index;
    private boolean done;
    private boolean waiting;

    ConstructJob(WorldAction.Construct action) {
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
        if (b == null || b.condition <= 0) {
            done = true;
            return 0;
        }
        if (!b.hasAnchor) {
            Structures.Siting siting = Structures.chooseSite(w, b);
            if (siting == Structures.Siting.WAIT) {
                waiting = true;
                return 0;
            }
            if (siting == Structures.Siting.NO_SITE) {
                // No free ground nearby: the building exists in the simulation only.
                EvolveMod.LOG.warn("No room to place building #{} ({}) near cell {}", b.id, b.type, b.cell);
                done = true;
                return 0;
            }
        }
        if (plan == null) {
            if (!w.footprintLoaded(b)) {
                waiting = true;
                return 0;
            }
            plan = Structures.plan(w, b, true).placements();
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
