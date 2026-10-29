package com.zendrahub.evolve.core.cli;

import com.zendrahub.evolve.core.Simulation;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records a run as a sequence of compact frames for visualisation outside
 * Minecraft. Cell layers are quantised to one byte per cell and base64-encoded.
 */
final class Replay {
    /** Events worth showing in a replay feed. */
    private static final Set<EventType> HEADLINES = EnumSet.of(
            EventType.SETTLEMENT_FOUNDED, EventType.OUTPOST_FOUNDED, EventType.SETTLEMENT_ABANDONED,
            EventType.OUTPOST_ABANDONED, EventType.STRATEGY_CHANGED, EventType.MATERIAL_CHANGED,
            EventType.TRADE_ROUTE_OPENED, EventType.TRADE_ROUTE_CLOSED, EventType.TIMBER_SHORTAGE,
            EventType.FOOD_SHORTAGE, EventType.STARVATION, EventType.STORM, EventType.ENVIRONMENT_SHIFT,
            EventType.FOREST_DEPLETED, EventType.FOREST_RECOVERED, EventType.WILDLIFE_DECLINE,
            EventType.WILDLIFE_RECOVERY, EventType.BUILDING_RUINED, EventType.RECLAIMED, EventType.REPLANTED);

    private final List<Object> frames = new ArrayList<>();
    private long lastEventId;

    private Replay() {
    }

    /** Runs {@code days} days, capturing a frame every {@code every} days (and the first and last day). */
    static Map<String, Object> record(String label, Simulation sim, int days, int every) {
        Replay r = new Replay();
        r.capture(sim.state());
        for (int d = 1; d <= days; d++) {
            sim.step();
            if (d % every == 0 || d == days) {
                r.capture(sim.state());
            }
        }
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("label", label);
        run.put("frames", r.frames);
        return run;
    }

    /** Static description of the world: size, biomes and regions. */
    static Map<String, Object> world(WorldState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("width", (long) s.width);
        m.put("height", (long) s.height);
        m.put("seed", s.seed);
        List<Object> biomeNames = new ArrayList<>();
        for (Biome b : Biome.values()) {
            biomeNames.add(b.name());
        }
        m.put("biomeNames", biomeNames);
        byte[] biomes = new byte[s.cells().length];
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = (byte) s.cells()[i].biome.ordinal();
        }
        m.put("biomes", Base64.getEncoder().encodeToString(biomes));
        List<Object> regions = new ArrayList<>();
        for (Region g : s.regions) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("name", g.name);
            rm.put("bounds", List.of((long) g.x0, (long) g.z0, (long) g.x1, (long) g.z1));
            rm.put("gravityFlux", g.env.gravityFlux);
            regions.add(rm);
        }
        m.put("regions", regions);
        return m;
    }

    private void capture(WorldState s) {
        Cell[] cells = s.cells();
        byte[] timber = new byte[cells.length];
        byte[] animals = new byte[cells.length];
        byte[] built = new byte[cells.length];
        for (int i = 0; i < cells.length; i++) {
            Cell c = cells[i];
            timber[i] = quantise(c.timber / 240.0);
            animals[i] = quantise(c.herbivores / 12.0);
            built[i] = quantise(c.builtArea);
        }
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("day", (long) s.day);
        f.put("season", s.season().name());
        Base64.Encoder b64 = Base64.getEncoder();
        f.put("timber", b64.encodeToString(timber));
        f.put("herbivores", b64.encodeToString(animals));
        f.put("built", b64.encodeToString(built));

        List<Object> env = new ArrayList<>();
        for (Region g : s.regions) {
            env.add(List.of(round(g.env.gravity), round(g.env.fertility), round(g.env.woodDecay * 1000),
                    round(g.env.storminess), round(g.forestCover)));
        }
        f.put("env", env);

        List<Object> towns = new ArrayList<>();
        for (Settlement t : s.settlements) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("id", (long) t.id);
            tm.put("name", t.name);
            tm.put("x", (long) t.home.x());
            tm.put("z", (long) t.home.z());
            tm.put("parent", (long) t.parentId);
            tm.put("population", (long) t.population);
            tm.put("strategy", t.strategy.name());
            tm.put("material", t.preferredMaterial.name());
            tm.put("abandoned", t.abandoned);
            tm.put("food", round(t.food));
            tm.put("timber", round(t.timber));
            tm.put("stone", round(t.stone));
            int standing = 0;
            int maxStories = 0;
            for (Building b : s.buildings) {
                if (b.settlementId == t.id && b.isActive()) {
                    standing++;
                    maxStories = Math.max(maxStories, b.stories);
                }
            }
            tm.put("buildings", (long) standing);
            tm.put("maxStories", (long) maxStories);
            if (t.lastDecision != null) {
                Map<String, Object> scores = new LinkedHashMap<>();
                t.lastDecision.scores().forEach((k, v) -> scores.put(k.name(), round(v)));
                tm.put("scores", scores);
            }
            towns.add(tm);
        }
        f.put("settlements", towns);

        List<Object> routes = new ArrayList<>();
        for (TradeRoute r : s.routes) {
            Settlement a = s.settlement(r.fromId);
            Settlement b = s.settlement(r.toId);
            routes.add(List.of((long) a.home.x(), (long) a.home.z(), (long) b.home.x(), (long) b.home.z(), r.active,
                    r.isSupplyRoad(s)));
        }
        f.put("routes", routes);

        List<Object> events = new ArrayList<>();
        for (SimEvent e : s.events.filter(e -> e.id() > lastEventId && HEADLINES.contains(e.type()))) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("id", e.id());
            em.put("day", (long) e.day());
            em.put("type", e.type().name());
            em.put("message", e.message());
            em.put("cause", e.causeId());
            events.add(em);
        }
        lastEventId = s.events.nextId() - 1;
        f.put("events", events);
        frames.add(f);
    }

    private static byte quantise(double share) {
        return (byte) Math.round(Math.max(0, Math.min(1, share)) * 255);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
