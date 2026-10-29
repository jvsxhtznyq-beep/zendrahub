package com.zendrahub.evolve.mod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * Every call into Minecraft whose name or signature has moved between recent
 * versions goes through this class, so porting the mod means editing one file.
 * Written for Minecraft 26.1.2 (unobfuscated, Mojang's names); mod/API_NOTES.md
 * lists each assumption and how confident it is.
 */
public final class McCompat {
    public static final String MOD_ID = "evolve";

    /** Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS. */
    private static final int UPDATE_ALL = 3;

    private static final Identifier GRAVITY_ID = id("region_gravity");
    private static final Identifier FALL_DAMAGE_ID = id("region_fall_damage");
    private static final Identifier SAFE_FALL_ID = id("region_safe_fall");

    private McCompat() {
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    static Path worldDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    static long seed(ServerLevel level) {
        return level.getSeed();
    }

    /** True if the chunk is loaded; never loads or generates it. */
    static boolean isChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
        return level.hasChunk(chunkX, chunkZ);
    }

    /** One above the highest non-air block (leaves included). Only call for loaded chunks. */
    static int surfaceY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
    }

    /** Sets a block with neighbour and client updates; returns false if it already had that state. */
    static boolean setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos) == state) {
            return false;
        }
        return level.setBlock(pos, state, UPDATE_ALL);
    }

    /** The biome at a position; computed from the generator for unloaded chunks without loading them. */
    static Holder<Biome> biomeAt(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos);
    }

    static boolean isIn(ServerPlayer player, ServerLevel level) {
        return player.level() == level;
    }

    /**
     * Applies a region's gravity to a player: the gravity attribute scales by
     * {@code gravity}, fall damage scales with it and the safe fall distance
     * shrinks in proportion. 1.0 removes the modifiers.
     */
    static void setRegionGravity(ServerPlayer player, double gravity) {
        double g = Math.max(0.05, gravity);
        setMultiplier(player.getAttribute(Attributes.GRAVITY), GRAVITY_ID, g - 1.0);
        setMultiplier(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL_DAMAGE_ID, g - 1.0);
        setMultiplier(player.getAttribute(Attributes.SAFE_FALL_DISTANCE), SAFE_FALL_ID, 1.0 / g - 1.0);
    }

    private static void setMultiplier(AttributeInstance attribute, Identifier modifierId, double amount) {
        if (attribute == null) {
            return;
        }
        if (Math.abs(amount) < 1.0e-4) {
            attribute.removeModifier(modifierId);
            return;
        }
        attribute.addOrUpdateTransientModifier(
                new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    static ServerPlayer player(CommandSourceStack source) {
        return source.getPlayer();
    }

    static void reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    static void fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
    }

    static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
