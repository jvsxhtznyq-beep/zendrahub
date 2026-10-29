package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The overworld as the simulation sees it: cell-to-chunk mapping, safe block
 * access (never touching unloaded chunks) and the footprints of placed
 * buildings. Cell (x, z) is chunk (originChunkX + x, originChunkZ + z).
 */
final class WorldAccess {
    final ServerLevel level;
    final WorldState state;

    WorldAccess(ServerLevel level, WorldState state) {
        this.level = level;
        this.state = state;
    }

    // --- grid mapping -------------------------------------------------------

    int chunkX(GridPos cell) {
        return state.originChunkX + cell.x();
    }

    int chunkZ(GridPos cell) {
        return state.originChunkZ + cell.z();
    }

    int minBlockX(GridPos cell) {
        return chunkX(cell) << 4;
    }

    int minBlockZ(GridPos cell) {
        return chunkZ(cell) << 4;
    }

    /** The grid cell containing a block, or null outside the simulated area. */
    GridPos cellAt(int blockX, int blockZ) {
        GridPos p = new GridPos((blockX >> 4) - state.originChunkX, (blockZ >> 4) - state.originChunkZ);
        return state.inBounds(p) ? p : null;
    }

    GridPos cellAt(BlockPos pos) {
        return cellAt(pos.getX(), pos.getZ());
    }

    /** Horizontal centre of a settlement: the middle of its home chunk. */
    int centerX(Settlement s) {
        return minBlockX(s.home) + 8;
    }

    int centerZ(Settlement s) {
        return minBlockZ(s.home) + 8;
    }

    // --- block access -------------------------------------------------------

    boolean loaded(int x, int z) {
        return McCompat.isChunkLoaded(level, x >> 4, z >> 4);
    }

    boolean loaded(BlockPos pos) {
        return loaded(pos.getX(), pos.getZ());
    }

    boolean cellLoaded(GridPos cell) {
        return McCompat.isChunkLoaded(level, chunkX(cell), chunkZ(cell));
    }

    BlockState get(BlockPos pos) {
        return level.getBlockState(pos);
    }

    boolean set(BlockPos pos, BlockState state) {
        return McCompat.setBlock(level, pos, state);
    }

    int surfaceY(int x, int z) {
        return McCompat.surfaceY(level, x, z);
    }

    Holder<Biome> biome(BlockPos pos) {
        return McCompat.biomeAt(level, pos);
    }

    /**
     * Height of the natural ground in a column: the first block below the
     * surface that is not air, foliage, a log or a replaceable plant. Water
     * counts as ground (callers check for fluids). Loaded chunks only.
     */
    int groundY(int x, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int y = surfaceY(x, z) - 1;
        for (int i = 0; i < 64; i++, y--) {
            BlockState s = level.getBlockState(m.set(x, y, z));
            if (!isOpen(s)) {
                return y;
            }
        }
        return y;
    }

    /** Air, leaves, logs and replaceable dry plants: what lies above natural ground. */
    static boolean isOpen(BlockState s) {
        return s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)
                || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }

    // --- footprints of placed buildings -------------------------------------

    /** True if the column lies within {@code margin} blocks of a placed building. */
    boolean inAnyFootprint(int x, int z, int margin) {
        for (Building b : state.buildings) {
            if (!b.hasAnchor || b.condition <= 0) {
                continue;
            }
            int f = b.type.footprint;
            if (x >= b.anchorX - margin && x < b.anchorX + f + margin
                    && z >= b.anchorZ - margin && z < b.anchorZ + f + margin) {
                return true;
            }
        }
        return false;
    }

    /** True if a square of side {@code size} at (x0, z0) comes within {@code margin} of a placed building. */
    boolean overlapsFootprint(int x0, int z0, int size, int margin) {
        for (Building b : state.buildings) {
            if (!b.hasAnchor || b.condition <= 0) {
                continue;
            }
            int f = b.type.footprint;
            boolean apartX = x0 + size + margin <= b.anchorX || b.anchorX + f + margin <= x0;
            boolean apartZ = z0 + size + margin <= b.anchorZ || b.anchorZ + f + margin <= z0;
            if (!apartX && !apartZ) {
                return true;
            }
        }
        return false;
    }

    /** True if every chunk under the building's footprint is loaded. */
    boolean footprintLoaded(Building b) {
        int f = b.type.footprint;
        return loaded(b.anchorX, b.anchorZ) && loaded(b.anchorX + f - 1, b.anchorZ)
                && loaded(b.anchorX, b.anchorZ + f - 1) && loaded(b.anchorX + f - 1, b.anchorZ + f - 1);
    }
}
