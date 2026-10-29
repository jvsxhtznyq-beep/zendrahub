package com.zendrahub.evolve.core.cli;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.Simulation;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;
import com.zendrahub.evolve.core.persist.Json;
import com.zendrahub.evolve.core.persist.SaveManager;
import com.zendrahub.evolve.core.scenario.Scenarios;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Headless EVOLVE: run, save, resume and inspect worlds without Minecraft.
 *
 * <pre>
 * evolve demo    [--seed N] [--days N] [--sweep N]   abundant vs. depleted forest, gravity check, seed sweep
 * evolve run     [--seed N] [--days N] [--depleted] [--save FILE]
 * evolve resume  --load FILE [--days N] [--save FILE]
 * evolve inspect FILE [--events N]
 * evolve why     FILE EVENT_ID                         causal chain behind an event
 * evolve bench   [--quick]
 * evolve replay  --out FILE [--seed N] [--days N] [--every N]
 * </pre>
 */
public final class EvolveCli {
    static final long DEFAULT_SEED = 20261029L;

    private EvolveCli() {
    }

    public static void main(String[] args) {
        PrintStream out = System.out;
        try {
            System.exit(run(args, out));
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            usage(System.err);
            System.exit(2);
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args, PrintStream out) throws IOException {
        if (args.length == 0) {
            usage(out);
            return 0;
        }
        String command = args[0];
        Options o = Options.parse(args, 1);
        switch (command) {
            case "demo" -> Demo.run(out, o.longOpt("seed", DEFAULT_SEED), o.intOpt("days", 240), o.intOpt("sweep", 12));
            case "run" -> {
                Simulation sim = Scenarios.forestTown(o.longOpt("seed", DEFAULT_SEED), o.flag("depleted"),
                        SimConfig.defaults());
                sim.advance(o.intOpt("days", 120));
                summary(out, sim.state(), 15);
                saveIfAsked(out, o, sim.state());
            }
            case "resume" -> {
                WorldState s = load(out, Path.of(o.require("load")));
                Simulation sim = new Simulation(s, SimConfig.defaults());
                sim.advance(o.intOpt("days", 40));
                summary(out, s, 15);
                saveIfAsked(out, o, s);
            }
            case "inspect" -> {
                WorldState s = load(out, Path.of(o.positional(0, "save file")));
                summary(out, s, o.intOpt("events", 25));
            }
            case "why" -> {
                WorldState s = load(out, Path.of(o.positional(0, "save file")));
                long id = Long.parseLong(o.positional(1, "event id"));
                List<SimEvent> chain = s.events.causalChain(id);
                if (chain.isEmpty()) {
                    out.println("No event #" + id + " in the retained history.");
                    return 1;
                }
                Report.events(out, chain);
            }
            case "bench" -> Bench.run(out, o.flag("quick"));
            case "replay" -> {
                long seed = o.longOpt("seed", DEFAULT_SEED);
                int days = o.intOpt("days", 240);
                int every = Math.max(1, o.intOpt("every", 4));
                Simulation abundant = Scenarios.forestTown(seed, false, SimConfig.defaults());
                Simulation depleted = Scenarios.forestTown(seed, true, SimConfig.defaults());
                Map<String, Object> root = new LinkedHashMap<>();
                root.put("format", "evolve-replay");
                root.put("version", 1L);
                root.put("world", Replay.world(abundant.state()));
                root.put("runs", List.of(
                        Replay.record("Abundant timber", abundant, days, every),
                        Replay.record("Depleted forest", depleted, days, every)));
                Path file = Path.of(o.require("out"));
                String json = Json.write(root);
                if (file.getFileName().toString().endsWith(".html")) {
                    Files.writeString(file, viewerPage(json, !o.flag("fragment")));
                } else {
                    Files.writeString(file, json);
                }
                out.println("Wrote " + (days / every + 1) + " frames per run to " + file);
            }
            case "help", "--help", "-h" -> usage(out);
            default -> throw new IllegalArgumentException("unknown command '" + command + "'");
        }
        return 0;
    }

    /** The replay viewer with the data embedded; standalone pages get a full document around it. */
    private static String viewerPage(String json, boolean standalone) throws IOException {
        String template;
        try (var in = EvolveCli.class.getResourceAsStream("/evolve/replay-viewer.html")) {
            if (in == null) {
                throw new IOException("replay viewer template is missing from the classpath");
            }
            template = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        // "</" inside a script element would end it early; "<\/" is the same JSON string.
        String page = template.replace("/*__REPLAY_DATA__*/", json.replace("</", "<\\/"));
        if (!standalone) {
            return page;
        }
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
                + "</head>\n<body>\n" + page + "\n</body>\n</html>\n";
    }

    private static WorldState load(PrintStream out, Path file) throws IOException {
        SaveManager.LoadResult r = SaveManager.load(file);
        for (String w : r.warnings()) {
            out.println("warning: " + w);
        }
        if (r.state() == null) {
            throw new IOException("no save at " + file);
        }
        if (r.fromBackup()) {
            out.println("warning: loaded the backup copy");
        }
        return r.state();
    }

    private static void saveIfAsked(PrintStream out, Options o, WorldState s) throws IOException {
        if (o.has("save")) {
            Path file = Path.of(o.require("save"));
            SaveManager.save(file, s);
            out.println("Saved day " + s.day + " to " + file);
        }
    }

    private static void summary(PrintStream out, WorldState s, int events) {
        Report.world(out, s);
        out.println();
        for (Settlement t : s.settlements) {
            Report.settlementDetail(out, s, t);
        }
        out.println();
        out.println("Recent history:");
        Report.events(out, s.events.recent(events));
    }

    private static void usage(PrintStream out) {
        out.println("""
                EVOLVE headless simulator

                  demo    [--seed N] [--days N] [--sweep N]  abundant vs. depleted forest, gravity check, seed sweep
                  run     [--seed N] [--days N] [--depleted] [--save FILE]
                  resume  --load FILE [--days N] [--save FILE]
                  inspect FILE [--events N]
                  why     FILE EVENT_ID                      causal chain behind an event
                  bench   [--quick]                          throughput, memory and save/load timings
                  replay  --out FILE [--seed N] [--days N] [--every N] [--fragment]
                                                     both forest scenarios as replay data (.json) or a viewer page (.html)
                """);
    }

    /** Minimal --key value / --flag / positional argument parsing. */
    static final class Options {
        private final Map<String, String> named = new HashMap<>();
        private final List<String> positional = new ArrayList<>();

        static Options parse(String[] args, int from) {
            Options o = new Options();
            for (int i = from; i < args.length; i++) {
                String a = args[i];
                if (a.startsWith("--")) {
                    String key = a.substring(2);
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        o.named.put(key, args[++i]);
                    } else {
                        o.named.put(key, "true");
                    }
                } else {
                    o.positional.add(a);
                }
            }
            return o;
        }

        boolean has(String key) {
            return named.containsKey(key);
        }

        boolean flag(String key) {
            return "true".equals(named.get(key));
        }

        String require(String key) {
            String v = named.get(key);
            if (v == null || v.equals("true")) {
                throw new IllegalArgumentException("--" + key + " needs a value");
            }
            return v;
        }

        String positional(int index, String what) {
            if (index >= positional.size()) {
                throw new IllegalArgumentException("missing " + what);
            }
            return positional.get(index);
        }

        int intOpt(String key, int fallback) {
            if (!has(key)) {
                return fallback;
            }
            try {
                int v = Integer.parseInt(require(key));
                if (v < 0) {
                    throw new IllegalArgumentException("--" + key + " must not be negative");
                }
                return v;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--" + key + " must be a whole number");
            }
        }

        long longOpt(String key, long fallback) {
            if (!has(key)) {
                return fallback;
            }
            try {
                return Long.parseLong(require(key));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--" + key + " must be a whole number");
            }
        }
    }
}
