package com.zendrahub.evolve.core.model;

/** A rectangular block of cells sharing one set of environmental rules. */
public final class Region {
    public final int id;
    public String name;
    /** Cell bounds: x0 and z0 inclusive, x1 and z1 exclusive. */
    public final int x0;
    public final int z0;
    public final int x1;
    public final int z1;
    public final EnvironmentProfile env = new EnvironmentProfile();

    /** Mean forest cover of the region's cells, 0..1, refreshed every day. */
    public double forestCover;
    /** Forest cover when the region was generated, for change tracking. */
    public double initialForestCover;
    /** Total herbivores in the region, refreshed every day. */
    public double herbivores;
    /** Herbivores when the region was generated, for change tracking. */
    public double initialHerbivores;

    public Region(int id, String name, int x0, int z0, int x1, int z1) {
        this.id = id;
        this.name = name;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
    }

    public boolean contains(int x, int z) {
        return x >= x0 && x < x1 && z >= z0 && z < z1;
    }

    public int cellCount() {
        return (x1 - x0) * (z1 - z0);
    }

    public GridPos center() {
        return new GridPos((x0 + x1) / 2, (z0 + z1) / 2);
    }
}
