package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.util.DetRandom;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Helpers shared by the world jobs. */
final class Jobs {
    private static final long COLUMN_STREAM = 402;

    /** Where a placement run stopped: the next index, block changes made, and whether it hit an unloaded chunk. */
    record Progress(int index, int used, boolean waiting) {
    }

    private Jobs() {
    }

    /** The 256 columns of a chunk (index = z * 16 + x) in an order seeded by the action. */
    static int[] columnOrder(long seed, long actionId) {
        Integer[] cols = new Integer[256];
        long[] keys = new long[256];
        for (int i = 0; i < 256; i++) {
            cols[i] = i;
            keys[i] = DetRandom.hash(seed, COLUMN_STREAM, actionId, i);
        }
        Arrays.sort(cols, Comparator.comparingLong(i -> keys[i]));
        int[] out = new int[256];
        for (int i = 0; i < 256; i++) {
            out[i] = cols[i];
        }
        return out;
    }

    /**
     * Places blueprint entries from {@code from} on, until the budget of real
     * changes is spent or a placement falls in an unloaded chunk. Entries already
     * in place cost nothing, which makes re-running a plan cheap and idempotent.
     */
    static Progress place(WorldAccess w, List<Blueprint.Placement> plan, int from, int budget) {
        int i = from;
        int used = 0;
        int reads = 0;
        while (i < plan.size() && used < budget && reads < budget * 20) {
            Blueprint.Placement p = plan.get(i);
            if (!w.loaded(p.pos())) {
                return new Progress(i, used, true);
            }
            reads++;
            if (w.set(p.pos(), p.state())) {
                used++;
            }
            i++;
        }
        return new Progress(i, used, false);
    }
}
