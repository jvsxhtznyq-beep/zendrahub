package com.zendrahub.evolve.core;

/**
 * Size and layout of a generated world.
 *
 * @param width      cells along x (one cell is one chunk in game)
 * @param height     cells along z
 * @param regionSize side length of an environment region, in cells
 */
public record WorldGenOptions(int width, int height, int regionSize) {

    public WorldGenOptions {
        if (width <= 0 || height <= 0 || regionSize <= 0) {
            throw new IllegalArgumentException("sizes must be positive");
        }
    }

    public static WorldGenOptions defaults() {
        return new WorldGenOptions(32, 32, 8);
    }
}
