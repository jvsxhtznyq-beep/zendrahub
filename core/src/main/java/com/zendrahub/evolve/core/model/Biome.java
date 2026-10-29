package com.zendrahub.evolve.core.model;

/**
 * Coarse terrain classes. Capacities are per cell (one chunk): timber in logs
 * (one log = one log block in game), forage in food units, stone richness and
 * farm suitability as 0..1 factors.
 */
public enum Biome {
    FOREST(240, 40, 0.2, 0.7),
    PLAINS(30, 80, 0.1, 1.0),
    HILLS(110, 40, 0.8, 0.5),
    WETLAND(80, 60, 0.05, 0.8),
    DESERT(4, 8, 0.4, 0.1),
    MOUNTAIN(25, 12, 1.0, 0.2),
    WATER(0, 0, 0.0, 0.0);

    /** Standing timber a mature cell of this biome supports, in logs. */
    public final double timberCapacity;
    /** Edible plants a cell supports when undisturbed, in food units. */
    public final double forageCapacity;
    /** How productive quarrying is here, 0..1. */
    public final double stoneRichness;
    /** How well fields grow here, 0..1, before the region's fertility applies. */
    public final double farmSuitability;

    Biome(double timberCapacity, double forageCapacity, double stoneRichness, double farmSuitability) {
        this.timberCapacity = timberCapacity;
        this.forageCapacity = forageCapacity;
        this.stoneRichness = stoneRichness;
        this.farmSuitability = farmSuitability;
    }

    public boolean buildable() {
        return this != WATER;
    }
}
