package com.zendrahub.evolve.core.model;

/**
 * One remembered event. Its influence fades with age: see {@link #weightAt(int, double)}.
 *
 * @param weight initial strength, typically 0..1 scaled by severity
 */
public record MemoryEntry(MemoryKind kind, int day, double weight, String note) {

    /** Remaining influence on the given day, halving every {@code halfLifeDays}. */
    public double weightAt(int today, double halfLifeDays) {
        int age = Math.max(0, today - day);
        return weight * Math.pow(0.5, age / halfLifeDays);
    }
}
