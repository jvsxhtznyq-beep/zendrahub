package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.ArrayList;
import java.util.List;

/** Queries over the event log that civilization needs each day. */
final class Events {
    private Events() {
    }

    /** Events recorded so far today (by the environment and ecosystem, which run first). */
    static List<SimEvent> today(WorldState st) {
        int n = 64;
        while (true) {
            List<SimEvent> recent = st.events.recent(n);
            if (recent.size() < n || recent.get(0).day() < st.day) {
                List<SimEvent> out = new ArrayList<>();
                for (SimEvent e : recent) {
                    if (e.day() == st.day) {
                        out.add(e);
                    }
                }
                return out;
            }
            n *= 2;
        }
    }

    /** Id of the newest event of a type for a settlement among the last {@code maxScan} events, or -1. */
    static long latest(WorldState st, int settlementId, EventType type, int maxScan) {
        List<SimEvent> recent = st.events.recent(maxScan);
        for (int i = recent.size() - 1; i >= 0; i--) {
            SimEvent e = recent.get(i);
            if (e.type() == type && e.settlementId() == settlementId) {
                return e.id();
            }
        }
        return -1;
    }
}
