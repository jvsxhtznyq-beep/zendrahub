package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.model.GridPos;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/** Plants saplings of the local species on open soil, spaced apart, in a seeded order. */
final class PlantSaplingsJob implements WorldJob {
    private final WorldAction.PlantSaplings action;
    private int[] order;
    private int index;
    private int planted;
    private boolean done;
    private boolean waiting;

    PlantSaplingsJob(WorldAction.PlantSaplings action) {
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
        int used = 0;
        while (used < budget && planted < action.count() && index < order.length) {
            int c = order[index++];
            BlockPos spot = plantingSpot(w, minX + (c & 15), minZ + (c >> 4));
            if (spot == null) {
                continue;
            }
            BlockState sapling = BiomeMapper.classify(w.biome(spot)).wood().sapling.defaultBlockState();
            if (w.set(spot, sapling)) {
                used++;
                planted++;
            }
        }
        if (planted >= action.count() || index >= order.length) {
            done = true;
        }
        return used;
    }

    /** The block above open soil in this column, if a sapling may go there. */
    private static BlockPos plantingSpot(WorldAccess w, int x, int z) {
        if (w.inAnyFootprint(x, z, 1)) {
            return null;
        }
        int y = w.surfaceY(x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState top = w.get(m.set(x, y, z));
        if (!top.isAir() && top.canBeReplaced() && top.getFluidState().isEmpty()) {
            y--; // grass or flowers on top: plant in their place
            top = w.get(m.set(x, y, z));
        }
        if (!top.is(BlockTags.DIRT)) {
            return null;
        }
        BlockPos above = new BlockPos(x, y + 1, z);
        BlockState here = w.get(above);
        if (!here.isAir() && !(here.canBeReplaced() && here.getFluidState().isEmpty())) {
            return null;
        }
        if (w.get(above.north()).is(BlockTags.SAPLINGS) || w.get(above.south()).is(BlockTags.SAPLINGS)
                || w.get(above.east()).is(BlockTags.SAPLINGS) || w.get(above.west()).is(BlockTags.SAPLINGS)) {
            return null;
        }
        return above;
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
