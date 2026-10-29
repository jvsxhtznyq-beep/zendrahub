package com.zendrahub.evolve.core.model;

/** Seasons cycle every {@code WorldState.daysPerSeason} days, starting in spring. */
public enum Season {
    SPRING(1.25),
    SUMMER(1.0),
    AUTUMN(0.75),
    WINTER(0.25);

    /** Multiplier on plant growth (trees, forage, fields). */
    public final double growth;

    Season(double growth) {
        this.growth = growth;
    }
}
