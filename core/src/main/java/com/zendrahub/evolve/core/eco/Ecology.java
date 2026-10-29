package com.zendrahub.evolve.core.eco;

import com.zendrahub.evolve.core.model.Cell;

/**
 * The ecosystem's public interface to other subsystems: how much a cell
 * offers and how taking from it works. Every extraction goes through here, so
 * the ecosystem sees exactly what civilization removed.
 */
public final class Ecology {
    /** Standing timber, in logs, that counts as a fully forested cell. */
    public static final double FULL_FOREST_LOGS = 200.0;

    private Ecology() {
    }

    /** Forest cover of a cell, 0..1, measured in absolute standing timber. */
    public static double forestCover(Cell c) {
        return Math.min(1.0, Math.max(0.0, c.timber / FULL_FOREST_LOGS));
    }

    /** Timber the cell can hold given its biome and what is built on it. */
    public static double timberCapacity(Cell c) {
        return c.biome.timberCapacity * unbuiltShare(c);
    }

    /** Wild forage the cell can hold given its biome and what is built on it. */
    public static double forageCapacity(Cell c) {
        return c.biome.forageCapacity * unbuiltShare(c);
    }

    /** Share of the cell still open to nature, 0..1. */
    public static double unbuiltShare(Cell c) {
        return Math.min(1.0, Math.max(0.0, 1.0 - c.builtArea));
    }

    /**
     * Returns built-over ground to nature, for example when a building's ruin
     * has crumbled away: frees {@code area} of the cell's built share and seeds
     * it with {@code saplings} so forest can return.
     */
    public static void reclaim(Cell c, double area, double saplings) {
        c.builtArea = Math.max(0.0, c.builtArea - Math.max(0.0, area));
        plant(c, saplings);
    }

    /** Removes up to {@code want} logs of standing timber; returns what was taken. */
    public static double harvestTimber(Cell c, double want) {
        double take = Math.max(0.0, Math.min(want, c.timber));
        c.timber -= take;
        return take;
    }

    /** Gathers up to {@code want} food units of wild plants; returns what was taken. */
    public static double gatherForage(Cell c, double want) {
        double take = Math.max(0.0, Math.min(want, c.forage));
        c.forage -= take;
        return take;
    }

    /** Hunts up to {@code want} animals; returns how many were taken. */
    public static double hunt(Cell c, double want) {
        double take = Math.max(0.0, Math.min(want, c.herbivores));
        c.herbivores -= take;
        return take;
    }

    /** Plants saplings; the ecosystem matures them into standing timber over time. */
    public static void plant(Cell c, double saplings) {
        if (saplings > 0) {
            c.saplings += saplings;
        }
    }
}
