package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.Simulation;
import com.zendrahub.evolve.core.WorldGen;
import com.zendrahub.evolve.core.WorldGenOptions;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;
import com.zendrahub.evolve.core.persist.SaveManager;
import com.zendrahub.evolve.core.scenario.Scenarios;
import com.zendrahub.evolve.core.util.DetRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * One server's EVOLVE world: the simulation, its clock, the queue of block
 * work it produced, and the bridges back from the real world (timber surveys)
 * and into it (gravity, announcements). Lives from server start to stop.
 */
final class EvolveRuntime {
    static final int AUTOSAVE_TICKS = 6000;
    static final int SURVEY_INTERVAL = 10;
    static final int GRAVITY_INTERVAL = 20;
    static final int MAX_ADVANCE_DAYS = 365;
    /** Cells around the founder that are measured from the real world at start. */
    private static final int START_SURVEY_RADIUS = 3;
    private static final long ESTIMATE_STREAM = 404;

    /** A command could not be carried out; the message is shown to the player. */
    static final class Refusal extends Exception {
        private static final long serialVersionUID = 1L;

        Refusal(String message) {
            super(message);
        }
    }

    private final MinecraftServer server;
    private final ServerLevel level;
    private final Path stateFile;
    final RuntimeSettings settings;
    private final JobQueue jobs = new JobQueue();
    private final TimberSurvey survey = new TimberSurvey();
    private final GravityApplier gravity = new GravityApplier();
    private final EventBroadcaster broadcaster = new EventBroadcaster();

    private Simulation sim;
    private WorldAccess world;
    private long ticks;
    private long lastSaveTick;
    private long lastErrorTick = -100_000;

    private EvolveRuntime(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
        Path dir = McCompat.worldDir(server).resolve("evolve");
        this.stateFile = dir.resolve("state.json");
        this.settings = RuntimeSettings.load(dir.resolve("runtime.properties"));
    }

    static EvolveRuntime open(MinecraftServer server) {
        EvolveRuntime rt = new EvolveRuntime(server);
        rt.loadState();
        return rt;
    }

    // --- lifecycle ----------------------------------------------------------

    private void loadState() {
        try {
            SaveManager.LoadResult result = SaveManager.load(stateFile);
            for (String warning : result.warnings()) {
                EvolveMod.LOG.warn("EVOLVE save: {}", warning);
            }
            if (result.state() == null) {
                return;
            }
            attach(new Simulation(result.state(), SimConfig.defaults()));
            broadcaster.skipTo(sim.state().events);
            EvolveMod.LOG.info("EVOLVE world loaded: day {}, {} settlements, {} pending world actions{}",
                    sim.state().day, sim.state().settlements.size(), sim.pendingActions().size(),
                    result.fromBackup() ? " (recovered from backup)" : "");
        } catch (IOException | RuntimeException e) {
            EvolveMod.LOG.error("Could not load {}; it is moved aside and EVOLVE starts empty", stateFile, e);
            quarantine();
        }
    }

    /** Moves unreadable save files out of the way so a new world cannot overwrite them. */
    private void quarantine() {
        for (String suffix : new String[]{"", ".bak"}) {
            Path file = stateFile.resolveSibling(stateFile.getFileName() + suffix);
            if (Files.exists(file)) {
                try {
                    Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    EvolveMod.LOG.error("Could not move aside {}", file, e);
                }
            }
        }
    }

    private void attach(Simulation simulation) {
        sim = simulation;
        sim.setHeadless(false);
        world = new WorldAccess(level, sim.state());
        jobs.clear();
        jobs.sync(sim);
    }

    void tick() {
        ticks++;
        try {
            if (sim != null) {
                if (!settings.paused && ticks % settings.ticksPerDay == 0) {
                    stepDays(1);
                }
                jobs.tick(world, sim, settings.blockBudget);
                if (ticks % SURVEY_INTERVAL == 0) {
                    survey.tick(world, sim, playersInOverworld(), jobs.busyCells());
                }
                if (ticks - lastSaveTick >= AUTOSAVE_TICKS) {
                    save();
                }
            }
            if (ticks % GRAVITY_INTERVAL == 0) {
                gravity.apply(world, server.getPlayerList().getPlayers());
            }
        } catch (RuntimeException e) {
            if (ticks - lastErrorTick > 1200) {
                EvolveMod.LOG.error("EVOLVE tick failed", e);
                lastErrorTick = ticks;
            }
        }
    }

    void shutdown() {
        if (sim != null) {
            save();
        }
        settings.save();
        gravity.reset(server.getPlayerList().getPlayers());
    }

    /** Writes the world to disk; returns an error message or null on success. */
    String save() {
        lastSaveTick = ticks;
        if (sim == null) {
            return "nothing to save yet";
        }
        try {
            SaveManager.save(stateFile, sim.state());
            return null;
        } catch (IOException | RuntimeException e) {
            EvolveMod.LOG.error("Could not save EVOLVE world to {}", stateFile, e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    // --- simulation control -------------------------------------------------

    void stepDays(int days) {
        for (int i = 0; i < days; i++) {
            sim.step();
        }
        jobs.sync(sim);
        broadcaster.publish(server, sim.state().events);
    }

    /**
     * Creates the simulated world centred on the player's chunk, reads its
     * terrain from the real world, and founds the player's town plus one
     * neighbouring town for it to trade with.
     */
    Settlement start(ServerPlayer player, String name) throws Refusal {
        if (sim != null) {
            throw new Refusal("EVOLVE is already running in this world (day " + sim.state().day + ").");
        }
        if (!McCompat.isIn(player, level)) {
            throw new Refusal("Start EVOLVE from the overworld.");
        }
        BlockPos at = player.blockPosition();
        WorldGenOptions options = WorldGenOptions.defaults();
        WorldState state = WorldGen.blank(McCompat.seed(level), options);
        state.anchored = true;
        state.originChunkX = (at.getX() >> 4) - options.width() / 2;
        state.originChunkZ = (at.getZ() >> 4) - options.height() / 2;
        WorldAccess access = new WorldAccess(level, state);
        GridPos home = access.cellAt(at);
        Simulation simulation = new Simulation(state, SimConfig.defaults());
        readTerrain(access, home, simulation);

        Settlement town = simulation.foundSettlement(name, home, 20);
        if (town == null) {
            throw new Refusal("A settlement cannot be founded here - try dry, open land.");
        }
        GridPos neighbourSite = neighbourSite(state, home);
        if (neighbourSite != null) {
            simulation.foundSettlement(Names.town(state), neighbourSite, 18);
        }
        attach(simulation);
        broadcaster.publish(server, state.events);
        save();
        return town;
    }

    /** Biomes for every cell from the game's biome source; measured timber where chunks are loaded. */
    private static void readTerrain(WorldAccess access, GridPos home, Simulation simulation) {
        WorldState state = access.state;
        for (Cell cell : state.cells()) {
            GridPos pos = cell.pos();
            int x = access.minBlockX(pos) + 8;
            int z = access.minBlockZ(pos) + 8;
            boolean loaded = access.cellLoaded(pos);
            int y = loaded ? access.surfaceY(x, z) : 64;
            Biome biome = BiomeMapper.classify(access.biome(new BlockPos(x, y, z))).biome();
            // Until a chunk is measured, assume a typical, partly harvested stand.
            double share = 0.55 + 0.35 * DetRandom.unit(state.seed, ESTIMATE_STREAM, cell.x, cell.z);
            WorldGen.populateCell(cell, biome, share);
        }
        for (int dz = -START_SURVEY_RADIUS; dz <= START_SURVEY_RADIUS; dz++) {
            for (int dx = -START_SURVEY_RADIUS; dx <= START_SURVEY_RADIUS; dx++) {
                GridPos pos = home.offset(dx, dz);
                Cell cell = state.cell(pos);
                if (cell != null && cell.biome != Biome.WATER && access.cellLoaded(pos)) {
                    simulation.observeTimber(pos, TimberSurvey.countLogs(access, pos));
                }
            }
        }
        // Settle wildlife and take each region's baselines from the terrain as read and measured.
        WorldGen.refreshRegionBaselines(state);
    }

    /**
     * A livable site with good quarrying 8-14 cells from home (any distance
     * beyond 8 if none is that close), or null when the area has none.
     */
    private static GridPos neighbourSite(WorldState state, GridPos home) {
        GridPos site = Scenarios.neighbourSite(state, home, 8, 14);
        return site != null ? site : Scenarios.neighbourSite(state, home, 8, Integer.MAX_VALUE);
    }

    Settlement found(ServerPlayer player, String name) throws Refusal {
        requireRunning();
        if (!McCompat.isIn(player, level)) {
            throw new Refusal("Settlements can only be founded in the overworld.");
        }
        if (sim.state().settlementByName(name) != null) {
            throw new Refusal("There is already a settlement called " + name + ".");
        }
        GridPos cell = world.cellAt(player.blockPosition());
        if (cell == null) {
            throw new Refusal("You are outside the simulated area.");
        }
        Settlement s = sim.foundSettlement(name, cell, 12);
        if (s == null) {
            throw new Refusal("A settlement cannot be founded here - try dry, open land away from other towns.");
        }
        jobs.sync(sim);
        broadcaster.publish(server, sim.state().events);
        return s;
    }

    int advance(int days) throws Refusal {
        requireRunning();
        int n = Math.max(1, Math.min(MAX_ADVANCE_DAYS, days));
        stepDays(n);
        return n;
    }

    void setTicksPerDay(int ticksPerDay) {
        settings.ticksPerDay = Math.max(RuntimeSettings.MIN_TICKS_PER_DAY,
                Math.min(RuntimeSettings.MAX_TICKS_PER_DAY, ticksPerDay));
        settings.save();
    }

    void setPaused(boolean paused) {
        settings.paused = paused;
        settings.save();
    }

    void requireRunning() throws Refusal {
        if (sim == null) {
            throw new Refusal("EVOLVE has not been started in this world. Use /evolve start <town name>.");
        }
    }

    // --- queries ------------------------------------------------------------

    Simulation sim() {
        return sim;
    }

    WorldAccess world() {
        return world;
    }

    int queuedJobs() {
        return jobs.size();
    }

    int waitingJobs() {
        return jobs.waiting();
    }

    private List<ServerPlayer> playersInOverworld() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (McCompat.isIn(p, level)) {
                out.add(p);
            }
        }
        return out;
    }
}
