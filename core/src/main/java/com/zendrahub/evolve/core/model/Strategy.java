package com.zendrahub.evolve.core.model;

/** A settlement's current strategic posture, chosen by utility scoring. */
public enum Strategy {
    /** Grow: build housing and storage, use local resources freely. */
    EXPAND,
    /** Limit logging to what regrows, replant, prefer non-timber materials. */
    CONSERVE,
    /** Obtain scarce resources from another settlement over a trade route. */
    TRADE,
    /** Found an outpost where the scarce resource is plentiful and ship it home. */
    OUTPOST
}
