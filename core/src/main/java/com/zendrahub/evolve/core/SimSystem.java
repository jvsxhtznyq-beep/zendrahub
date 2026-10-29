package com.zendrahub.evolve.core;

/** One subsystem of the simulation, advanced once per simulated day. */
public interface SimSystem {
    void step(SimContext ctx);
}
