package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Region;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes each region's gravity physical: players inside the simulated area get
 * their region's gravity (and matching fall damage); anywhere else, normal
 * gravity. Modifiers are transient and only re-sent when the value changes.
 */
final class GravityApplier {
    /** Keyed by player object, so a respawned player (a new object) is updated again. */
    private final Map<ServerPlayer, Double> applied = new HashMap<>();

    void apply(WorldAccess w, List<ServerPlayer> all) {
        applied.keySet().retainAll(all);
        for (ServerPlayer player : all) {
            double gravity = 1.0;
            if (w != null && McCompat.isIn(player, w.level)) {
                GridPos cell = w.cellAt(player.blockPosition());
                Region region = cell == null ? null : w.state.regionAt(cell);
                if (region != null) {
                    gravity = region.env.gravity;
                }
            }
            Double last = applied.get(player);
            if (last != null && Math.abs(last - gravity) < 0.005) {
                continue;
            }
            McCompat.setRegionGravity(player, gravity);
            applied.put(player, gravity);
        }
    }

    /** Restores normal gravity for everyone (simulation stopped or reset). */
    void reset(List<ServerPlayer> all) {
        for (ServerPlayer player : all) {
            if (applied.containsKey(player)) {
                McCompat.setRegionGravity(player, 1.0);
            }
        }
        applied.clear();
    }
}
