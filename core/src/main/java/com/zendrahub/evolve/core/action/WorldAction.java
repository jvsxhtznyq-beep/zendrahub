package com.zendrahub.evolve.core.action;

import com.zendrahub.evolve.core.model.GridPos;

/**
 * A change the simulation made that the host world should show. The simulation
 * has already applied the change to its own state; the host (the Minecraft mod)
 * carries it out in blocks, over as many ticks as its budget needs, and then
 * acknowledges it. Unacknowledged actions are saved and redelivered after a
 * restart, so hosts must apply them idempotently. Headless runs discard them.
 */
public sealed interface WorldAction {

    long id();

    /** Remove standing trees in the cell totalling about {@code logs} log blocks. */
    record FellTrees(long id, int settlementId, GridPos cell, int logs) implements WorldAction {
    }

    /** Plant {@code count} saplings on open ground in the cell. */
    record PlantSaplings(long id, int settlementId, GridPos cell, int count) implements WorldAction {
    }

    /** Build (or finish building) the structure; details are read from the building. */
    record Construct(long id, int buildingId) implements WorldAction {
    }

    /** Show wear on a building: condition 1 is pristine, 0 means gone and reclaimed by nature. */
    record Decay(long id, int buildingId, double condition) implements WorldAction {
    }

    /** Lay a road along a trade route between its two settlements. */
    record LayRoad(long id, int routeId) implements WorldAction {
    }
}
