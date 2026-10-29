package com.zendrahub.evolve.core.event;

/**
 * One entry in the world's history. Unused references are -1.
 *
 * @param causeId id of the earlier event that led to this one, so causal chains
 *                (timber shortage, then outpost, then new building style) can be
 *                followed backwards; -1 when there is no single cause
 */
public record SimEvent(long id, int day, EventType type, int settlementId, int regionId,
                       int cellX, int cellZ, String message, long causeId) {

    public boolean hasCell() {
        return cellX >= 0 && cellZ >= 0;
    }

    @Override
    public String toString() {
        return "day " + day + " [" + type + "] " + message;
    }
}
