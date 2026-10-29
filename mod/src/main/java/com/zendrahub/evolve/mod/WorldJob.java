package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.GridPos;

/**
 * Carries out one world action in blocks, a little each tick. Jobs only touch
 * loaded chunks and wait otherwise; they are idempotent because unfinished
 * actions are redelivered after a restart.
 */
interface WorldJob {

    long actionId();

    /** A cell whose standing timber this job changes (surveys skip it meanwhile), or null. */
    GridPos busyCell();

    /** Makes at most {@code budget} block changes and returns how many it made. */
    int run(WorldAccess world, int budget);

    boolean isDone();

    /** True when the last run stopped because a needed chunk is not loaded. */
    boolean isWaiting();

    static WorldJob of(WorldAction action) {
        return switch (action) {
            case WorldAction.FellTrees a -> new FellTreesJob(a);
            case WorldAction.PlantSaplings a -> new PlantSaplingsJob(a);
            case WorldAction.Construct a -> new ConstructJob(a);
            case WorldAction.Decay a -> new DecayJob(a);
            case WorldAction.LayRoad a -> new LayRoadJob(a);
        };
    }
}
