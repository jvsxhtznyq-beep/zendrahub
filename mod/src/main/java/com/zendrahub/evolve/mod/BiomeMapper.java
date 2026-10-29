package com.zendrahub.evolve.mod;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Translates Minecraft biomes into the simulation's terrain classes and local wood types. */
final class BiomeMapper {

    /** Wood species, used for saplings and timber building palettes. */
    enum Wood {
        OAK(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.OAK_SLAB, Blocks.OAK_SAPLING),
        SPRUCE(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_SAPLING),
        BIRCH(Blocks.BIRCH_PLANKS, Blocks.BIRCH_LOG, Blocks.BIRCH_SLAB, Blocks.BIRCH_SAPLING),
        JUNGLE(Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_LOG, Blocks.JUNGLE_SLAB, Blocks.JUNGLE_SAPLING),
        ACACIA(Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG, Blocks.ACACIA_SLAB, Blocks.ACACIA_SAPLING),
        // A lone dark oak sapling never grows (it needs a 2x2), so plant oak in its place.
        DARK_OAK(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_SLAB, Blocks.OAK_SAPLING),
        CHERRY(Blocks.CHERRY_PLANKS, Blocks.CHERRY_LOG, Blocks.CHERRY_SLAB, Blocks.CHERRY_SAPLING),
        MANGROVE(Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_LOG, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_PROPAGULE);

        final Block planks;
        final Block log;
        final Block slab;
        final Block sapling;

        Wood(Block planks, Block log, Block slab, Block sapling) {
            this.planks = planks;
            this.log = log;
            this.slab = slab;
            this.sapling = sapling;
        }
    }

    record Info(com.zendrahub.evolve.core.model.Biome biome, Wood wood) {
    }

    private BiomeMapper() {
    }

    static Info classify(Holder<Biome> b) {
        if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN) || b.is(BiomeTags.IS_RIVER)) {
            return info(com.zendrahub.evolve.core.model.Biome.WATER, Wood.OAK);
        }
        if (b.is(Biomes.MANGROVE_SWAMP)) {
            return info(com.zendrahub.evolve.core.model.Biome.WETLAND, Wood.MANGROVE);
        }
        if (b.is(Biomes.SWAMP)) {
            return info(com.zendrahub.evolve.core.model.Biome.WETLAND, Wood.OAK);
        }
        if (b.is(Biomes.DESERT) || b.is(BiomeTags.IS_BADLANDS) || b.is(BiomeTags.IS_BEACH)) {
            return info(com.zendrahub.evolve.core.model.Biome.DESERT, Wood.ACACIA);
        }
        if (b.is(BiomeTags.IS_JUNGLE)) {
            return info(com.zendrahub.evolve.core.model.Biome.FOREST, Wood.JUNGLE);
        }
        if (b.is(BiomeTags.IS_TAIGA)) {
            return info(com.zendrahub.evolve.core.model.Biome.FOREST, Wood.SPRUCE);
        }
        if (b.is(Biomes.CHERRY_GROVE)) {
            return info(com.zendrahub.evolve.core.model.Biome.FOREST, Wood.CHERRY);
        }
        if (b.is(Biomes.BIRCH_FOREST) || b.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
            return info(com.zendrahub.evolve.core.model.Biome.FOREST, Wood.BIRCH);
        }
        if (b.is(Biomes.DARK_FOREST)) {
            return info(com.zendrahub.evolve.core.model.Biome.FOREST, Wood.DARK_OAK);
        }
        if (b.is(BiomeTags.IS_FOREST)) {
            return info(com.zendrahub.evolve.core.model.Biome.FOREST, Wood.OAK);
        }
        if (b.is(BiomeTags.IS_SAVANNA)) {
            return info(com.zendrahub.evolve.core.model.Biome.PLAINS, Wood.ACACIA);
        }
        if (b.is(Biomes.MEADOW)) {
            return info(com.zendrahub.evolve.core.model.Biome.PLAINS, Wood.OAK);
        }
        if (b.is(BiomeTags.IS_MOUNTAIN)) {
            return info(com.zendrahub.evolve.core.model.Biome.MOUNTAIN, Wood.SPRUCE);
        }
        if (b.is(BiomeTags.IS_HILL)) {
            return info(com.zendrahub.evolve.core.model.Biome.HILLS, Wood.SPRUCE);
        }
        return info(com.zendrahub.evolve.core.model.Biome.PLAINS, Wood.OAK);
    }

    private static Info info(com.zendrahub.evolve.core.model.Biome biome, Wood wood) {
        return new Info(biome, wood);
    }
}
