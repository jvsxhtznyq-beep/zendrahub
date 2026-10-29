package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.TradeRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays a two-wide dirt path along a trade route, from the requesting
 * settlement's centre to its partner's, following the terrain. Water is
 * bridged with planks; trees and buildings are left alone. The path grows as
 * the chunks along it are loaded.
 */
final class LayRoadJob implements WorldJob {
    private final WorldAction.LayRoad action;
    private List<int[]> columns;
    private int index;
    private boolean done;
    private boolean waiting;

    LayRoadJob(WorldAction.LayRoad action) {
        this.action = action;
    }

    @Override
    public long actionId() {
        return action.id();
    }

    @Override
    public GridPos busyCell() {
        return null;
    }

    @Override
    public int run(WorldAccess w, int budget) {
        waiting = false;
        if (columns == null) {
            TradeRoute route = w.state.route(action.routeId());
            Settlement from = route == null ? null : w.state.settlement(route.fromId);
            Settlement to = route == null ? null : w.state.settlement(route.toId);
            if (from == null || to == null) {
                done = true;
                return 0;
            }
            columns = line(w.centerX(from), w.centerZ(from), w.centerX(to), w.centerZ(to));
        }
        int used = 0;
        while (index < columns.size() && used < budget) {
            int[] c = columns.get(index);
            if (!w.loaded(c[0], c[1])) {
                waiting = true;
                break;
            }
            used += pave(w, c[0], c[1]);
            index++;
        }
        if (index >= columns.size()) {
            done = true;
        }
        return used;
    }

    /** Paves one column; returns the number of blocks changed. */
    private static int pave(WorldAccess w, int x, int z) {
        if (w.inAnyFootprint(x, z, 0)) {
            return 0;
        }
        int ground = w.groundY(x, z);
        int surface = w.surfaceY(x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = ground + 1; y <= surface; y++) {
            if (w.get(m.set(x, y, z)).is(BlockTags.LOGS)) {
                return 0; // a tree stands here
            }
        }
        BlockPos at = new BlockPos(x, ground, z);
        BlockState top = w.get(at);
        int changed = 0;
        if (top.is(Blocks.WATER)) {
            return w.set(at, Blocks.OAK_PLANKS.defaultBlockState()) ? 1 : 0;
        }
        if (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT) || top.is(Blocks.COARSE_DIRT)
                || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.ROOTED_DIRT)) {
            BlockPos above = at.above();
            BlockState plant = w.get(above);
            if (!plant.isAir() && plant.canBeReplaced() && plant.getFluidState().isEmpty()) {
                if (w.set(above, Blocks.AIR.defaultBlockState())) {
                    changed++;
                }
            }
            if (w.get(above).isAir() && w.set(at, Blocks.DIRT_PATH.defaultBlockState())) {
                changed++;
            }
        }
        return changed;
    }

    /** Columns of a two-wide straight line between two points. */
    static List<int[]> line(int x0, int z0, int x1, int z1) {
        List<int[]> out = new ArrayList<>();
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        boolean alongX = dx >= dz;
        int err = dx - dz;
        int x = x0;
        int z = z0;
        while (true) {
            out.add(new int[]{x, z});
            out.add(alongX ? new int[]{x, z + 1} : new int[]{x + 1, z});
            if (x == x1 && z == z1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
        return out;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isWaiting() {
        return waiting;
    }
}
