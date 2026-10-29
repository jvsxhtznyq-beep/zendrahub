package com.zendrahub.evolve.core.eco;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.SimSystem;
import com.zendrahub.evolve.core.model.GridPos;

/** Vegetation, wildlife and reclamation dynamics on every cell. */
public final class EcosystemSystem implements SimSystem {

    @Override
    public void step(SimContext ctx) {
        // Implemented by the ecosystem subsystem.
    }

    /** The host measured the real standing timber of a cell; reconcile the simulation with it. */
    public void observeTimber(SimContext ctx, GridPos pos, double measuredLogs) {
        // Implemented by the ecosystem subsystem.
    }
}
