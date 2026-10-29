package com.zendrahub.evolve.core.env;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.SimSystem;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.WorldState;

/** Evolves each region's environmental rules and produces weather. */
public final class EnvironmentSystem implements SimSystem {

    @Override
    public void step(SimContext ctx) {
        // Implemented by the environment subsystem.
    }

    /** Plant growth multiplier in a region today: its fertility times the season's growth. */
    public static double growthFactor(WorldState state, Region region) {
        return region.env.fertility * state.season().growth;
    }
}
