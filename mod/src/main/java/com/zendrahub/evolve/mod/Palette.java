package com.zendrahub.evolve.mod;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks a structure is made of. Derived only from values that never
 * change after construction (material, storeys, the biome at the site and the
 * region's base wood decay), so decay can recompute exactly what was built.
 */
record Palette(BlockState floor, BlockState wall, BlockState pillar, BlockState roof,
               BlockState window, BlockState foundation, BlockState sapling) {

    static Palette timber(BlockState planks, BlockState log, BlockState slab, BlockState sapling) {
        return new Palette(planks, planks, log, slab, Blocks.GLASS.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(), sapling);
    }

    static Palette stone(int stories, BlockState sapling) {
        boolean dressed = stories >= 2;
        BlockState wall = dressed ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        BlockState roof = dressed ? Blocks.STONE_BRICK_SLAB.defaultBlockState()
                : Blocks.COBBLESTONE_SLAB.defaultBlockState();
        return new Palette(Blocks.SMOOTH_STONE.defaultBlockState(), wall, Blocks.STONE_BRICKS.defaultBlockState(),
                roof, Blocks.GLASS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), sapling);
    }

    static Palette mudBrick(BlockState log, BlockState sapling) {
        return new Palette(Blocks.PACKED_MUD.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(), log,
                Blocks.MUD_BRICK_SLAB.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                Blocks.PACKED_MUD.defaultBlockState(), sapling);
    }
}
