package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.model.WorldState;
import com.zendrahub.evolve.core.util.DetRandom;

/** Deterministic names for the settlements the mod founds on its own. */
final class Names {
    private static final String[] TOWNS = {
            "Ashford", "Stonebrook", "Millhaven", "Larkspur", "Thornwick", "Eastmere",
            "Brightwater", "Oakhollow", "Fernvale", "Ironmoor", "Willowmere", "Ravenford"
    };
    private static final long STREAM = 403;

    private Names() {
    }

    /** A town name not yet used in this world, chosen from the seed. */
    static String town(WorldState state) {
        int start = DetRandom.below(TOWNS.length, state.seed, STREAM, state.settlements.size());
        for (int i = 0; i < TOWNS.length; i++) {
            String name = TOWNS[(start + i) % TOWNS.length];
            if (state.settlementByName(name) == null) {
                return name;
            }
        }
        return "Settlement " + state.nextSettlementId();
    }
}
