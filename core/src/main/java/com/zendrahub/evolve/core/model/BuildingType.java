package com.zendrahub.evolve.core.model;

/** What a settlement can build. Footprint is the in-game size in blocks (square). */
public enum BuildingType {
    HOUSE(4, 0, 5),
    STOREHOUSE(0, 250, 7),
    FARM(0, 0, 9),
    LUMBER_CAMP(0, 60, 5),
    OUTPOST_HALL(6, 120, 7),
    TRADING_POST(0, 80, 5);

    /** People housed. */
    public final int housing;
    /** Extra storage for each of food, timber and stone. */
    public final int storage;
    /** Side length of the structure in blocks. */
    public final int footprint;

    BuildingType(int housing, int storage, int footprint) {
        this.housing = housing;
        this.storage = storage;
        this.footprint = footprint;
    }
}
