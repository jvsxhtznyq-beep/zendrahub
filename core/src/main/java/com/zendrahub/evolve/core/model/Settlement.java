package com.zendrahub.evolve.core.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** A town or an outpost: people, stocks, strategy and memories. */
public final class Settlement {
    public final int id;
    public String name;
    /** Parent town for outposts, -1 for independent towns. */
    public final int parentId;
    public final GridPos home;
    public final int foundedDay;

    public int population;
    public double food;
    public double timber;
    public double stone;

    public Strategy strategy = Strategy.EXPAND;
    /** Day the current strategy was adopted. */
    public int strategySinceDay;
    /** The most recent strategic evaluation, or null before the first one. */
    public Decision lastDecision;
    public Material preferredMaterial = Material.TIMBER;
    public final List<MemoryEntry> memory = new ArrayList<>();
    /** People assigned to each job on the last simulated day. */
    public final Map<Job, Integer> labor = new EnumMap<>(Job.class);

    public boolean abandoned;
    public int abandonedDay = -1;

    /** Lifetime statistics, for inspection and benchmarks. */
    public double timberHarvested;
    public double foodProduced;
    public int peakPopulation;

    public Settlement(int id, String name, int parentId, GridPos home, int foundedDay) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.home = home;
        this.foundedDay = foundedDay;
        this.strategySinceDay = foundedDay;
    }

    public boolean isOutpost() {
        return parentId >= 0;
    }

    public boolean isActive() {
        return !abandoned;
    }

    /** Sum of the faded weights of memories of the given kind. */
    public double recall(MemoryKind kind, int today, double halfLifeDays) {
        double total = 0;
        for (MemoryEntry m : memory) {
            if (m.kind() == kind) {
                total += m.weightAt(today, halfLifeDays);
            }
        }
        return total;
    }
}
