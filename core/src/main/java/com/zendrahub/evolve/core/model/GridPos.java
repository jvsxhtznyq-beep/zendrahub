package com.zendrahub.evolve.core.model;

/**
 * A cell coordinate on the simulation grid. In the Minecraft mod one cell is
 * one chunk (16 x 16 blocks); the host maps cell (0, 0) to an anchor chunk.
 */
public record GridPos(int x, int z) {

    public GridPos offset(int dx, int dz) {
        return new GridPos(x + dx, z + dz);
    }

    /** Distance in cells along the longer axis (king moves). */
    public int chebyshev(GridPos other) {
        return Math.max(Math.abs(x - other.x), Math.abs(z - other.z));
    }

    public double distance(GridPos other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + z + ")";
    }
}
