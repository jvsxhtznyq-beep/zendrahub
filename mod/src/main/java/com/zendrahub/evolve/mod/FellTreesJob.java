package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.GridPos;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Cuts down whole trees in a chunk until about the requested number of logs
 * is gone. Trunks are visited in a seeded order so clearings spread across the
 * chunk; each tree is finished once started. Orphaned leaves decay naturally.
 */
final class FellTreesJob implements WorldJob {
    private static final int REACH = 7;

    private final WorldAction.FellTrees action;
    private int[] order;
    private int column;
    private int removed;
    private ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
    private Set<Long> visited = new HashSet<>();
    private BlockPos base;
    private boolean done;
    private boolean waiting;

    FellTreesJob(WorldAction.FellTrees action) {
        this.action = action;
    }

    @Override
    public long actionId() {
        return action.id();
    }

    @Override
    public GridPos busyCell() {
        return action.cell();
    }

    @Override
    public int run(WorldAccess w, int budget) {
        waiting = false;
        if (!w.state.inBounds(action.cell())) {
            done = true;
            return 0;
        }
        if (!w.cellLoaded(action.cell())) {
            waiting = true;
            return 0;
        }
        if (order == null) {
            order = Jobs.columnOrder(w.state.seed, action.id());
        }
        int minX = w.minBlockX(action.cell());
        int minZ = w.minBlockZ(action.cell());
        BlockState air = Blocks.AIR.defaultBlockState();
        int used = 0;
        int reads = 0;
        while (used < budget && reads < budget * 16) {
            if (!frontier.isEmpty()) {
                BlockPos p = frontier.poll();
                reads++;
                if (!w.loaded(p) || !w.get(p).is(BlockTags.LOGS)) {
                    continue;
                }
                w.set(p, air);
                used++;
                removed++;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            BlockPos q = p.offset(dx, dy, dz);
                            if (q.getY() >= base.getY() && Math.abs(q.getX() - base.getX()) <= REACH
                                    && Math.abs(q.getZ() - base.getZ()) <= REACH && visited.add(q.asLong())) {
                                frontier.add(q);
                            }
                        }
                    }
                }
                continue;
            }
            if (removed >= action.logs() || column >= order.length) {
                done = true;
                break;
            }
            int c = order[column++];
            int x = minX + (c & 15);
            int z = minZ + (c >> 4);
            reads += 8;
            BlockPos trunk = trunkBase(w, x, z);
            if (trunk != null) {
                base = trunk;
                visited = new HashSet<>();
                visited.add(trunk.asLong());
                frontier = new ArrayDeque<>();
                frontier.add(trunk);
            }
        }
        return used;
    }

    /** Lowest log of a natural tree standing in the column, or null. */
    private static BlockPos trunkBase(WorldAccess w, int x, int z) {
        if (w.inAnyFootprint(x, z, 1)) {
            return null;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int y = w.surfaceY(x, z) - 1;
        BlockState top = w.get(m.set(x, y, z));
        if (!top.is(BlockTags.LEAVES) && !top.is(BlockTags.LOGS)) {
            return null;
        }
        BlockPos lowest = null;
        for (int i = 0; i < 48; i++, y--) {
            BlockState s = w.get(m.set(x, y, z));
            if (s.is(BlockTags.LOGS)) {
                lowest = m.immutable();
            } else if (!WorldAccess.isOpen(s)) {
                break;
            }
        }
        return lowest;
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
