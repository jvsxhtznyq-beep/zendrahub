package com.zendrahub.evolve.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Wires the EVOLVE runtime into the server lifecycle; everything
 * runs on the server thread and only in the overworld, so vanilla clients can
 * join a server running the mod.
 */
public final class EvolveMod implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger(McCompat.MOD_ID);

    private static EvolveRuntime runtime;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(EvolveMod::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(EvolveMod::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(EvolveMod::onServerTick);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> EvolveCommands.register(dispatcher));
        LOG.info("Project EVOLVE loaded");
    }

    /** The runtime of the running server, or null while no server runs. */
    static EvolveRuntime runtime() {
        return runtime;
    }

    private static void onServerStarted(MinecraftServer server) {
        runtime = EvolveRuntime.open(server);
    }

    private static void onServerStopping(MinecraftServer server) {
        EvolveRuntime rt = runtime;
        runtime = null;
        if (rt != null) {
            rt.shutdown();
        }
    }

    private static void onServerTick(MinecraftServer server) {
        EvolveRuntime rt = runtime;
        if (rt != null) {
            rt.tick();
        }
    }
}
