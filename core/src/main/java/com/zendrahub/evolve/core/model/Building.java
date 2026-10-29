package com.zendrahub.evolve.core.model;

/** A structure owned by a settlement, from planned through built to ruined. */
public final class Building {
    public final int id;
    public final int settlementId;
    public final BuildingType type;
    public final Material material;
    /** Number of storeys; capped by gravity (taller in low gravity). */
    public final int stories;
    public final GridPos cell;
    public final int plannedDay;

    /** Day construction finished, or -1 while under construction. */
    public int builtDay = -1;
    /** Construction progress, 0..1. */
    public double progress;
    /** Structural condition, 0..1. Storms and decay lower it; 0 means reclaimed by nature. */
    public double condition = 1.0;
    /** Set when the owning settlement abandoned it; abandoned buildings decay and are not repaired. */
    public boolean abandoned;

    /** Block position of the structure's origin in the host world, once the host placed it. */
    public boolean hasAnchor;
    public int anchorX;
    public int anchorY;
    public int anchorZ;

    public Building(int id, int settlementId, BuildingType type, Material material, int stories,
                    GridPos cell, int plannedDay) {
        this.id = id;
        this.settlementId = settlementId;
        this.type = type;
        this.material = material;
        this.stories = stories;
        this.cell = cell;
        this.plannedDay = plannedDay;
    }

    public boolean isComplete() {
        return builtDay >= 0;
    }

    /** Standing and usable: complete, not abandoned and not reduced to a ruin. */
    public boolean isActive() {
        return isComplete() && !abandoned && condition > 0;
    }

    public void setAnchor(int x, int y, int z) {
        hasAnchor = true;
        anchorX = x;
        anchorY = y;
        anchorZ = z;
    }
}
