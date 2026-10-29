package com.zendrahub.evolve.core.cli;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.Simulation;
import com.zendrahub.evolve.core.WorldGen;
import com.zendrahub.evolve.core.WorldGenOptions;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;
import com.zendrahub.evolve.core.persist.StateCodec;
import com.zendrahub.evolve.core.scenario.Scenarios;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Reproducible benchmarks: fixed seeds and sizes, a warm-up, then timed days.
 * Reports throughput, retained heap of the world, entity counts and the size
 * and speed of save/load. Heap is measured as used memory after GC before and
 * after building the world, so treat it as an estimate.
 */
final class Bench {
    private static final long SEED = 7_000_001L;

    private Bench() {
    }

    static void run(PrintStream out, boolean quick) {
        int[][] cases = quick
                ? new int[][]{{32, 4}, {64, 16}}
                : new int[][]{{32, 4}, {64, 16}, {128, 64}, {256, 256}};
        int warmup = quick ? 10 : 20;
        int days = quick ? 30 : 100;
        out.printf(Locale.ROOT, "EVOLVE benchmark: seed %d, %d warm-up days, %d timed days, Java %s, %d cores%n",
                SEED, warmup, days, System.getProperty("java.version"), Runtime.getRuntime().availableProcessors());
        String f = "%-9s %-8s %-9s %-11s %-13s %-10s %-10s %-9s %-10s %-10s %-9s%n";
        out.printf(Locale.ROOT, f, "cells", "towns", "people", "ms/day", "cell-days/s", "buildings", "events",
                "heap MB", "save KB", "save ms", "load ms");
        for (int[] c : cases) {
            Result r = measure(c[0], c[1], warmup, days);
            out.printf(Locale.ROOT, f, c[0] + "x" + c[0], r.towns, r.people, Report.fmt(r.msPerDay),
                    Report.fmt0(r.cellDaysPerSecond), r.buildings, r.events, Report.fmt(r.heapMb),
                    Report.fmt0(r.saveBytes / 1024.0), Report.fmt(r.saveMs), Report.fmt(r.loadMs));
        }
    }

    record Result(int towns, int people, double msPerDay, double cellDaysPerSecond, int buildings, long events,
                  double heapMb, long saveBytes, double saveMs, double loadMs) {
    }

    static Result measure(int size, int towns, int warmup, int days) {
        long before = usedHeap();
        WorldState s = WorldGen.generate(SEED + size, new WorldGenOptions(size, size, 8));
        Simulation sim = new Simulation(s, SimConfig.defaults());
        List<GridPos> sites = Scenarios.latticeSites(s, towns, Math.max(6, size / (int) Math.ceil(Math.sqrt(towns))));
        int founded = 0;
        for (GridPos p : sites) {
            if (sim.foundSettlement("Town" + (founded + 1), p, 20) != null) {
                founded++;
            }
        }
        sim.advance(warmup);
        long t0 = System.nanoTime();
        sim.advance(days);
        double msPerDay = (System.nanoTime() - t0) / 1e6 / days;
        double heapMb = Math.max(0, usedHeap() - before) / (1024.0 * 1024.0);

        int people = 0;
        for (Settlement t : s.settlements) {
            people += t.population;
        }
        long st = System.nanoTime();
        byte[] json = StateCodec.toJson(s).getBytes(StandardCharsets.UTF_8);
        double saveMs = (System.nanoTime() - st) / 1e6;
        long lt = System.nanoTime();
        WorldState back = StateCodec.fromJson(new String(json, StandardCharsets.UTF_8));
        double loadMs = (System.nanoTime() - lt) / 1e6;
        if (back.day != s.day) {
            throw new IllegalStateException("benchmark save/load mismatch");
        }
        double cellDays = (double) size * size * days / ((msPerDay * days) / 1000.0);
        return new Result(founded, people, msPerDay, cellDays, s.buildings.size(), s.events.totalRecorded(),
                heapMb, json.length, saveMs, loadMs);
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return rt.totalMemory() - rt.freeMemory();
    }
}
