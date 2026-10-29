package com.zendrahub.evolve.core;

import com.zendrahub.evolve.core.civ.CivConfig;
import com.zendrahub.evolve.core.eco.EcoConfig;
import com.zendrahub.evolve.core.env.EnvConfig;

/**
 * Every tunable rule parameter, grouped by subsystem. Defaults are the
 * calibrated values; tests and experiments change them explicitly.
 */
public final class SimConfig {
    public final EnvConfig env = new EnvConfig();
    public final EcoConfig eco = new EcoConfig();
    public final CivConfig civ = new CivConfig();

    /**
     * Upper bound on actions waiting for the host. A host that falls behind
     * loses the oldest cosmetic actions (tree felling, planting, decay) first;
     * construction and roads are always kept.
     */
    public int maxPendingActions = 4000;

    public static SimConfig defaults() {
        return new SimConfig();
    }
}
