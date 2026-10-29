package com.zendrahub.evolve.core.model;

/** A standing exchange between two settlements; in game it is laid out as a road. */
public final class TradeRoute {
    public final int id;
    /** The settlement that asked for the route. */
    public final int fromId;
    /** The settlement supplying it. */
    public final int toId;
    public final int establishedDay;
    public boolean active = true;
    public int closedDay = -1;
    public int lastTradeDay = -1;
    public double timberMoved;
    public double foodMoved;
    public double stoneMoved;

    public TradeRoute(int id, int fromId, int toId, int establishedDay) {
        this.id = id;
        this.fromId = fromId;
        this.toId = toId;
        this.establishedDay = establishedDay;
    }

    public boolean connects(int settlementId) {
        return fromId == settlementId || toId == settlementId;
    }

    public int other(int settlementId) {
        return fromId == settlementId ? toId : fromId;
    }

    /**
     * True for a supply road: it joins a town and its own outpost (or two
     * outposts of one town), so goods are hauled along it, not traded.
     */
    public boolean isSupplyRoad(WorldState state) {
        Settlement a = state.settlement(fromId);
        Settlement b = state.settlement(toId);
        return a != null && b != null
                && (a.parentId == b.id || b.parentId == a.id || (a.parentId >= 0 && a.parentId == b.parentId));
    }
}
