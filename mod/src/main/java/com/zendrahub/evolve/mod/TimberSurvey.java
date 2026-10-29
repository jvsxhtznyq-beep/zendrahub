package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.Simulation;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Measures real forests and reports them to the simulation, so trees players
 * cut or plant change what settlements see. Surveys loaded chunks around
 * players, one chunk per call, each at most once per simulated day.
 */
final class TimberSurvey {
    private static final int RADIUS = 3;
    private static final int SIDE = 2 * RADIUS + 1;

    private final Map<GridPos, Integer> lastSurveyDay = new HashMap<>();
    private int cursor;

    void tick(WorldAccess w, Simulation sim, List<ServerPlayer> players, Set<GridPos> busy) {
        if (players.isEmpty()) {
            return;
        }
        int area = SIDE * SIDE;
        for (int attempt = 0; attempt < 16; attempt++) {
            int k = cursor++;
            if (cursor < 0) {
                cursor = 0;
            }
            ServerPlayer player = players.get(Math.floorMod(k / area, players.size()));
            GridPos center = w.cellAt(player.blockPosition());
            if (center == null) {
                continue;
            }
            int i = Math.floorMod(k, area);
            GridPos target = center.offset(i % SIDE - RADIUS, i / SIDE - RADIUS);
            Cell cell = w.state.cell(target);
            if (cell == null || cell.biome == Biome.WATER || busy.contains(target)
                    || lastSurveyDay.getOrDefault(target, -1) == w.state.day || !w.cellLoaded(target)) {
                continue;
            }
            sim.observeTimber(target, countLogs(w, target));
            lastSurveyDay.put(target, w.state.day);
            return;
        }
    }

    /**
     * Log blocks belonging to natural trees in a chunk: columns crowned by
     * leaves or logs, counted down to the ground. Columns at placed buildings
     * are skipped, so timber frames do not count as forest.
     */
    static int countLogs(WorldAccess w, GridPos cell) {
        int minX = w.minBlockX(cell);
        int minZ = w.minBlockZ(cell);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = minX + dx;
                int z = minZ + dz;
                if (w.inAnyFootprint(x, z, 1)) {
                    continue;
                }
                int y = w.surfaceY(x, z) - 1;
                BlockState top = w.get(m.set(x, y, z));
                if (!top.is(BlockTags.LEAVES) && !top.is(BlockTags.LOGS)) {
                    continue;
                }
                for (int i = 0; i < 48; i++, y--) {
                    BlockState s = w.get(m.set(x, y, z));
                    if (s.is(BlockTags.LOGS)) {
                        count++;
                    } else if (!WorldAccess.isOpen(s)) {
                        break;
                    }
                }
            }
        }
        return count;
    }
}
