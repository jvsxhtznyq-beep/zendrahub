package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.MemoryEntry;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Settlement;

import java.util.List;

/** Recording memories and turning today's events into them. */
final class Memories {
    private Memories() {
    }

    /** Adds a memory; when over capacity, the faintest memory is forgotten. */
    static void add(SimContext ctx, Settlement s, MemoryKind kind, double weight, String note) {
        CivConfig cfg = ctx.config.civ;
        s.memory.add(new MemoryEntry(kind, ctx.day(), weight, note));
        while (s.memory.size() > cfg.memoryCapacity) {
            int weakest = 0;
            double weakestWeight = Double.POSITIVE_INFINITY;
            for (int i = 0; i < s.memory.size(); i++) {
                double w = s.memory.get(i).weightAt(ctx.day(), cfg.memoryHalfLifeDays);
                if (w < weakestWeight) {
                    weakestWeight = w;
                    weakest = i;
                }
            }
            s.memory.remove(weakest);
        }
    }

    /** Faded total weight of memories of a kind, capped at 1. */
    static double recall(SimContext ctx, Settlement s, MemoryKind kind) {
        return Math.min(1.0, s.recall(kind, ctx.day(), ctx.config.civ.memoryHalfLifeDays));
    }

    static boolean recordedWithin(SimContext ctx, Settlement s, MemoryKind kind, int days) {
        for (MemoryEntry m : s.memory) {
            if (m.kind() == kind && ctx.day() - m.day() < days) {
                return true;
            }
        }
        return false;
    }

    /**
     * Turns today's storms and environment shifts in the settlement's region into
     * memories. Returns the id of an environment shift that should prompt the
     * settlement to reconsider its plans, or -1.
     */
    static long observe(SimContext ctx, Settlement s, List<SimEvent> today, boolean ownsBuildings) {
        Cell home = ctx.state.cell(s.home);
        int regionId = home == null ? -1 : home.regionId;
        boolean storm = false;
        long shift = -1;
        String shiftNote = null;
        for (SimEvent e : today) {
            if (e.type() == EventType.STORM && e.regionId() == regionId) {
                storm = true;
            } else if (e.type() == EventType.BUILDING_DAMAGED && e.settlementId() == s.id) {
                storm = true;
            } else if (e.type() == EventType.ENVIRONMENT_SHIFT && e.regionId() == regionId) {
                shift = e.id();
                shiftNote = e.message();
            }
        }
        if (storm && ownsBuildings) {
            add(ctx, s, MemoryKind.STORM_DAMAGE, 0.5, "storm damaged our buildings");
        }
        if (shift >= 0) {
            add(ctx, s, MemoryKind.ENVIRONMENT_SHIFT, 0.4, shiftNote);
        }
        return shift;
    }
}
