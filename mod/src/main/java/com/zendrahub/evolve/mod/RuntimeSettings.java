package com.zendrahub.evolve.mod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Per-world runtime knobs, kept beside the simulation save in evolve/runtime.properties. */
final class RuntimeSettings {
    static final int MIN_TICKS_PER_DAY = 20;
    static final int MAX_TICKS_PER_DAY = 72000;

    /** Server ticks per simulated day; 600 ticks = 30 seconds. */
    int ticksPerDay = 600;
    boolean paused;
    /** Block changes the world jobs may make per server tick. */
    int blockBudget = 200;

    private final Path file;

    private RuntimeSettings(Path file) {
        this.file = file;
    }

    static RuntimeSettings load(Path file) {
        RuntimeSettings s = new RuntimeSettings(file);
        if (!Files.isRegularFile(file)) {
            return s;
        }
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(in);
        } catch (IOException e) {
            EvolveMod.LOG.warn("Could not read {}, using defaults", file, e);
            return s;
        }
        s.ticksPerDay = intProperty(p, "ticksPerDay", s.ticksPerDay, MIN_TICKS_PER_DAY, MAX_TICKS_PER_DAY);
        s.blockBudget = intProperty(p, "blockBudget", s.blockBudget, 10, 5000);
        s.paused = Boolean.parseBoolean(p.getProperty("paused", "false"));
        return s;
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("ticksPerDay", Integer.toString(ticksPerDay));
        p.setProperty("blockBudget", Integer.toString(blockBudget));
        p.setProperty("paused", Boolean.toString(paused));
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                p.store(out, "Project EVOLVE runtime settings");
            }
        } catch (IOException e) {
            EvolveMod.LOG.warn("Could not write {}", file, e);
        }
    }

    private static int intProperty(Properties p, String key, int fallback, int min, int max) {
        try {
            int v = Integer.parseInt(p.getProperty(key, Integer.toString(fallback)).trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
