package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.ArrayList;
import java.util.List;

/**
 * Buildings indexed by owner, rebuilt from the world state at the start of
 * every step (it holds no state of its own). New buildings must be added
 * through {@link #add} so the index stays in sync.
 */
final class Ledger {
    private final WorldState state;
    private final List<List<Building>> bySettlement = new ArrayList<>();

    Ledger(WorldState state) {
        this.state = state;
        for (Building b : state.buildings) {
            listFor(b.settlementId).add(b);
        }
    }

    WorldState state() {
        return state;
    }

    List<Building> of(Settlement s) {
        return listFor(s.id);
    }

    void add(Building b) {
        state.buildings.add(b);
        listFor(b.settlementId).add(b);
    }

    /** The building under construction, or null. */
    Building project(Settlement s) {
        for (Building b : of(s)) {
            if (!b.isComplete() && !b.abandoned) {
                return b;
            }
        }
        return null;
    }

    int housing(Settlement s) {
        int housing = 0;
        for (Building b : of(s)) {
            if (b.isActive()) {
                housing += b.type.housing * b.stories;
            }
        }
        return housing;
    }

    double storage(Settlement s, CivConfig cfg) {
        double cap = cfg.baseStorage;
        for (Building b : of(s)) {
            if (b.isActive()) {
                cap += b.type.storage;
            }
        }
        return cap;
    }

    int countActive(Settlement s, BuildingType type) {
        int n = 0;
        for (Building b : of(s)) {
            if (b.type == type && b.isActive()) {
                n++;
            }
        }
        return n;
    }

    boolean has(Settlement s, BuildingType type) {
        return countActive(s, type) > 0;
    }

    private List<Building> listFor(int settlementId) {
        int index = Math.max(0, settlementId - 1);
        while (bySettlement.size() <= index) {
            bySettlement.add(new ArrayList<>());
        }
        return bySettlement.get(index);
    }
}
