package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.Simulation;
import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.GridPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * World jobs in the order the simulation produced their actions. Each tick the
 * queue runs ready jobs until the block budget is spent, skipping jobs that
 * wait for unloaded chunks, and acknowledges finished actions.
 */
final class JobQueue {
    /** Jobs examined per tick at most, so a long backlog of waiting jobs stays cheap. */
    private static final int MAX_SCAN = 64;

    private final List<WorldJob> jobs = new ArrayList<>();
    private final Set<Long> queued = new HashSet<>();
    private int scanStart;

    /** Adds jobs for actions the simulation produced since the last sync. */
    void sync(Simulation sim) {
        for (WorldAction action : sim.pendingActions()) {
            if (queued.add(action.id())) {
                jobs.add(WorldJob.of(action));
            }
        }
    }

    void clear() {
        jobs.clear();
        queued.clear();
        scanStart = 0;
    }

    void tick(WorldAccess world, Simulation sim, int budget) {
        if (jobs.isEmpty()) {
            return;
        }
        int used = 0;
        int scanned = 0;
        int i = Math.min(scanStart, jobs.size() - 1);
        boolean progressed = false;
        while (!jobs.isEmpty() && used < budget && scanned < Math.min(MAX_SCAN, jobs.size())) {
            if (i >= jobs.size()) {
                i = 0;
            }
            WorldJob job = jobs.get(i);
            scanned++;
            try {
                used += job.run(world, budget - used);
            } catch (RuntimeException e) {
                EvolveMod.LOG.error("World job for action {} failed; skipping it", job.actionId(), e);
                finish(sim, i, job);
                continue;
            }
            if (job.isDone()) {
                finish(sim, i, job);
                progressed = true;
                continue;
            }
            if (!job.isWaiting()) {
                progressed = true;
                break; // out of budget mid-job: resume here next tick
            }
            i++;
        }
        // Oldest jobs keep priority; only rotate when everything ahead is waiting.
        scanStart = progressed ? 0 : i;
    }

    private void finish(Simulation sim, int index, WorldJob job) {
        jobs.remove(index);
        queued.remove(job.actionId());
        sim.acknowledge(job.actionId());
    }

    /** Cells with tree work queued, which timber surveys must not measure yet. */
    Set<GridPos> busyCells() {
        Set<GridPos> cells = new HashSet<>();
        for (WorldJob job : jobs) {
            GridPos c = job.busyCell();
            if (c != null) {
                cells.add(c);
            }
        }
        return cells;
    }

    int size() {
        return jobs.size();
    }

    int waiting() {
        int n = 0;
        for (Iterator<WorldJob> it = jobs.iterator(); it.hasNext(); ) {
            if (it.next().isWaiting()) {
                n++;
            }
        }
        return n;
    }
}
