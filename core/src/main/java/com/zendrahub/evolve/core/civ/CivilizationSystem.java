package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.SimSystem;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Settlement;

/** Settlement needs, planning, labour, construction, trade and outposts. */
public final class CivilizationSystem implements SimSystem {

    @Override
    public void step(SimContext ctx) {
        // Implemented by the civilization subsystem.
    }

    /**
     * Founds a town (parentId -1) or an outpost of an existing town.
     *
     * @return the new settlement, or null if the site is invalid
     */
    public Settlement foundSettlement(SimContext ctx, String name, GridPos home, int population, int parentId,
                                      long causeId) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
