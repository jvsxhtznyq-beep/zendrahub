package com.zendrahub.evolve.core.model;

/** Primary construction material. Chosen per building from local resources and environment. */
public enum Material {
    /** Logs and planks: cheap where forests stand, rots with the region's wood decay. */
    TIMBER,
    /** Quarried stone: heavy (effort scales with gravity) but durable. */
    STONE,
    /** Mud brick: needs wet or dry soil (wetland or desert nearby), little timber. */
    MUD_BRICK
}
