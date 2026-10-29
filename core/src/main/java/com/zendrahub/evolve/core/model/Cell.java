package com.zendrahub.evolve.core.model;

/** Ecological state of one grid cell (one chunk in game). Mutable simulation data. */
public final class Cell {
    public final int x;
    public final int z;
    public Biome biome;
    public int regionId;

    /** Standing timber in logs. */
    public double timber;
    /** Edible plants in food units. */
    public double forage;
    /** Grazing animals (deer-like herbivores), fractional for smooth dynamics. */
    public double herbivores;
    /** Share of the cell covered by structures and fields, 0..1; reduces natural capacity. */
    public double builtArea;
    /** Planted saplings that mature into timber over time. */
    public double saplings;
    /** Last day the host measured this cell from the real world, or -1. */
    public int lastObservedDay = -1;

    public Cell(int x, int z, Biome biome) {
        this.x = x;
        this.z = z;
        this.biome = biome;
    }

    public GridPos pos() {
        return new GridPos(x, z);
    }
}
