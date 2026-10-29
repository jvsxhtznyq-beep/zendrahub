package com.zendrahub.evolve.core.persist;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.event.EventLog;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Decision;
import com.zendrahub.evolve.core.model.EnvironmentProfile;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Job;
import com.zendrahub.evolve.core.model.Material;
import com.zendrahub.evolve.core.model.MemoryEntry;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a {@link WorldState} to and from its JSON save form. Every field is
 * written, so decode(encode(s)) is the same state and a resumed run is
 * identical to an uninterrupted one. Decoding validates sizes, ids and enum
 * names and reports the exact path of anything wrong.
 *
 * <p>Cells are stored column-wise (one array per field) to keep files small.
 */
public final class StateCodec {
    public static final String FORMAT = "evolve-world";

    private StateCodec() {
    }

    public static String toJson(WorldState state) {
        return Json.write(encode(state));
    }

    public static WorldState fromJson(String json) {
        return decode(Json.parse(json));
    }

    // ---------------------------------------------------------------- encode

    public static Map<String, Object> encode(WorldState s) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("formatVersion", (long) WorldState.FORMAT_VERSION);
        root.put("seed", s.seed);
        root.put("width", (long) s.width);
        root.put("height", (long) s.height);
        root.put("day", (long) s.day);
        root.put("daysPerSeason", (long) s.daysPerSeason);
        root.put("anchored", s.anchored);
        root.put("originChunkX", (long) s.originChunkX);
        root.put("originChunkZ", (long) s.originChunkZ);
        root.put("nextActionId", s.nextActionId);
        root.put("cells", encodeCells(s));

        List<Object> regions = new ArrayList<>();
        for (Region r : s.regions) {
            regions.add(encodeRegion(r));
        }
        root.put("regions", regions);

        List<Object> settlements = new ArrayList<>();
        for (Settlement t : s.settlements) {
            settlements.add(encodeSettlement(t));
        }
        root.put("settlements", settlements);

        List<Object> buildings = new ArrayList<>();
        for (Building b : s.buildings) {
            buildings.add(encodeBuilding(b));
        }
        root.put("buildings", buildings);

        List<Object> routes = new ArrayList<>();
        for (TradeRoute r : s.routes) {
            routes.add(encodeRoute(r));
        }
        root.put("routes", routes);

        List<Object> actions = new ArrayList<>();
        for (WorldAction a : s.pendingActions) {
            actions.add(encodeAction(a));
        }
        root.put("pendingActions", actions);
        root.put("events", encodeEvents(s.events));
        return root;
    }

    private static Map<String, Object> encodeCells(WorldState s) {
        Cell[] cells = s.cells();
        int n = cells.length;
        List<Object> biome = new ArrayList<>(n);
        List<Object> region = new ArrayList<>(n);
        List<Object> timber = new ArrayList<>(n);
        List<Object> forage = new ArrayList<>(n);
        List<Object> herbivores = new ArrayList<>(n);
        List<Object> builtArea = new ArrayList<>(n);
        List<Object> saplings = new ArrayList<>(n);
        List<Object> observed = new ArrayList<>(n);
        for (Cell c : cells) {
            biome.add((long) c.biome.ordinal());
            region.add((long) c.regionId);
            timber.add(c.timber);
            forage.add(c.forage);
            herbivores.add(c.herbivores);
            builtArea.add(c.builtArea);
            saplings.add(c.saplings);
            observed.add((long) c.lastObservedDay);
        }
        List<Object> legend = new ArrayList<>();
        for (Biome b : Biome.values()) {
            legend.add(b.name());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("biomeLegend", legend);
        m.put("biome", biome);
        m.put("region", region);
        m.put("timber", timber);
        m.put("forage", forage);
        m.put("herbivores", herbivores);
        m.put("builtArea", builtArea);
        m.put("saplings", saplings);
        m.put("lastObservedDay", observed);
        return m;
    }

    private static Map<String, Object> encodeRegion(Region r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", (long) r.id);
        m.put("name", r.name);
        m.put("bounds", List.of((long) r.x0, (long) r.z0, (long) r.x1, (long) r.z1));
        m.put("forestCover", r.forestCover);
        m.put("initialForestCover", r.initialForestCover);
        m.put("herbivores", r.herbivores);
        m.put("initialHerbivores", r.initialHerbivores);
        m.put("forestDepleted", r.forestDepleted);
        m.put("wildlifeDepleted", r.wildlifeDepleted);
        EnvironmentProfile e = r.env;
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("gravity", e.gravity);
        env.put("fertility", e.fertility);
        env.put("woodDecay", e.woodDecay);
        env.put("storminess", e.storminess);
        env.put("baseGravity", e.baseGravity);
        env.put("baseFertility", e.baseFertility);
        env.put("baseWoodDecay", e.baseWoodDecay);
        env.put("baseStorminess", e.baseStorminess);
        env.put("gravityFlux", e.gravityFlux);
        env.put("fluxAmplitude", e.fluxAmplitude);
        env.put("fluxPeriodDays", e.fluxPeriodDays);
        env.put("fluxPhase", e.fluxPhase);
        m.put("env", env);
        return m;
    }

    private static Map<String, Object> encodeSettlement(Settlement t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", (long) t.id);
        m.put("name", t.name);
        m.put("parentId", (long) t.parentId);
        m.put("home", pos(t.home));
        m.put("foundedDay", (long) t.foundedDay);
        m.put("population", (long) t.population);
        m.put("food", t.food);
        m.put("timber", t.timber);
        m.put("stone", t.stone);
        m.put("strategy", t.strategy.name());
        m.put("preferredMaterial", t.preferredMaterial.name());
        m.put("abandoned", t.abandoned);
        m.put("abandonedDay", (long) t.abandonedDay);
        m.put("timberHarvested", t.timberHarvested);
        m.put("foodProduced", t.foodProduced);
        m.put("peakPopulation", (long) t.peakPopulation);
        Map<String, Object> labor = new LinkedHashMap<>();
        for (Map.Entry<Job, Integer> e : t.labor.entrySet()) {
            labor.put(e.getKey().name(), (long) e.getValue());
        }
        m.put("labor", labor);
        List<Object> memory = new ArrayList<>();
        for (MemoryEntry e : t.memory) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("kind", e.kind().name());
            mm.put("day", (long) e.day());
            mm.put("weight", e.weight());
            mm.put("note", e.note());
            memory.add(mm);
        }
        m.put("memory", memory);
        if (t.lastDecision != null) {
            m.put("lastDecision", encodeDecision(t.lastDecision));
        }
        return m;
    }

    private static Map<String, Object> encodeDecision(Decision d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("day", (long) d.day());
        m.put("chosen", d.chosen().name());
        Map<String, Object> scores = new LinkedHashMap<>();
        for (Map.Entry<Strategy, Double> e : d.scores().entrySet()) {
            scores.put(e.getKey().name(), e.getValue());
        }
        m.put("scores", scores);
        m.put("reasons", new ArrayList<Object>(d.reasons()));
        m.put("causeEventId", d.causeEventId());
        return m;
    }

    private static Map<String, Object> encodeBuilding(Building b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", (long) b.id);
        m.put("settlementId", (long) b.settlementId);
        m.put("type", b.type.name());
        m.put("material", b.material.name());
        m.put("stories", (long) b.stories);
        m.put("cell", pos(b.cell));
        m.put("plannedDay", (long) b.plannedDay);
        m.put("builtDay", (long) b.builtDay);
        m.put("progress", b.progress);
        m.put("condition", b.condition);
        m.put("abandoned", b.abandoned);
        if (b.hasAnchor) {
            m.put("anchor", List.of((long) b.anchorX, (long) b.anchorY, (long) b.anchorZ));
        }
        return m;
    }

    private static Map<String, Object> encodeRoute(TradeRoute r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", (long) r.id);
        m.put("fromId", (long) r.fromId);
        m.put("toId", (long) r.toId);
        m.put("establishedDay", (long) r.establishedDay);
        m.put("active", r.active);
        m.put("closedDay", (long) r.closedDay);
        m.put("lastTradeDay", (long) r.lastTradeDay);
        m.put("timberMoved", r.timberMoved);
        m.put("foodMoved", r.foodMoved);
        m.put("stoneMoved", r.stoneMoved);
        return m;
    }

    private static Map<String, Object> encodeAction(WorldAction a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id());
        switch (a) {
            case WorldAction.FellTrees f -> {
                m.put("type", "FellTrees");
                m.put("settlementId", (long) f.settlementId());
                m.put("cell", pos(f.cell()));
                m.put("logs", (long) f.logs());
            }
            case WorldAction.PlantSaplings p -> {
                m.put("type", "PlantSaplings");
                m.put("settlementId", (long) p.settlementId());
                m.put("cell", pos(p.cell()));
                m.put("count", (long) p.count());
            }
            case WorldAction.Construct c -> {
                m.put("type", "Construct");
                m.put("buildingId", (long) c.buildingId());
            }
            case WorldAction.Decay d -> {
                m.put("type", "Decay");
                m.put("buildingId", (long) d.buildingId());
                m.put("condition", d.condition());
            }
            case WorldAction.LayRoad r -> {
                m.put("type", "LayRoad");
                m.put("routeId", (long) r.routeId());
            }
        }
        return m;
    }

    private static Map<String, Object> encodeEvents(EventLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("capacity", (long) log.capacity());
        m.put("nextId", log.nextId());
        m.put("totalRecorded", log.totalRecorded());
        List<Object> list = new ArrayList<>();
        for (SimEvent e : log.all()) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("id", e.id());
            em.put("day", (long) e.day());
            em.put("type", e.type().name());
            em.put("settlementId", (long) e.settlementId());
            em.put("regionId", (long) e.regionId());
            em.put("cell", List.of((long) e.cellX(), (long) e.cellZ()));
            em.put("message", e.message());
            em.put("causeId", e.causeId());
            list.add(em);
        }
        m.put("list", list);
        return m;
    }

    private static List<Object> pos(GridPos p) {
        return List.of((long) p.x(), (long) p.z());
    }

    // ---------------------------------------------------------------- decode

    public static WorldState decode(Object json) {
        Fields root = new Fields(json, "$");
        String format = root.getString("format", "");
        if (!FORMAT.equals(format)) {
            throw new SaveFormatException("$.format: not an EVOLVE world save");
        }
        int version = root.getInt("formatVersion");
        if (version > WorldState.FORMAT_VERSION) {
            throw new SaveFormatException("save format " + version + " is newer than this build supports ("
                    + WorldState.FORMAT_VERSION + "); update the mod");
        }
        if (version < 1) {
            throw new SaveFormatException("$.formatVersion: invalid version " + version);
        }
        root = Migrations.upgrade(root, version);

        int width = root.getInt("width");
        int height = root.getInt("height");
        if (width <= 0 || height <= 0 || (long) width * height > 16_777_216L) {
            throw new SaveFormatException("$: invalid world size " + width + "x" + height);
        }
        Fields eventsF = root.getObject("events");
        EventLog log = new EventLog(eventsF.getInt("capacity", EventLog.DEFAULT_CAPACITY));
        WorldState s = new WorldState(root.getLong("seed"), width, height, log);
        s.day = root.getInt("day");
        s.daysPerSeason = root.getInt("daysPerSeason", 10);
        if (s.daysPerSeason <= 0) {
            throw new SaveFormatException("$.daysPerSeason: must be positive");
        }
        s.anchored = root.getBool("anchored", false);
        s.originChunkX = root.getInt("originChunkX", 0);
        s.originChunkZ = root.getInt("originChunkZ", 0);
        s.nextActionId = root.getLong("nextActionId");

        List<Object> regions = root.getList("regions");
        for (int i = 0; i < regions.size(); i++) {
            Region r = decodeRegion(new Fields(regions.get(i), "$.regions[" + i + "]"));
            if (r.id != i) {
                throw new SaveFormatException("$.regions[" + i + "].id: expected " + i + ", found " + r.id);
            }
            s.regions.add(r);
        }
        decodeCells(root.getObject("cells"), s);

        List<Object> settlements = root.getList("settlements");
        for (int i = 0; i < settlements.size(); i++) {
            Settlement t = decodeSettlement(new Fields(settlements.get(i), "$.settlements[" + i + "]"));
            requireSequentialId("$.settlements[" + i + "]", t.id, i);
            s.settlements.add(t);
        }
        List<Object> buildings = root.getList("buildings");
        for (int i = 0; i < buildings.size(); i++) {
            Building b = decodeBuilding(new Fields(buildings.get(i), "$.buildings[" + i + "]"));
            requireSequentialId("$.buildings[" + i + "]", b.id, i);
            s.buildings.add(b);
        }
        List<Object> routes = root.getList("routes");
        for (int i = 0; i < routes.size(); i++) {
            TradeRoute r = decodeRoute(new Fields(routes.get(i), "$.routes[" + i + "]"));
            requireSequentialId("$.routes[" + i + "]", r.id, i);
            s.routes.add(r);
        }
        List<Object> actions = root.getList("pendingActions");
        for (int i = 0; i < actions.size(); i++) {
            s.pendingActions.add(decodeAction(new Fields(actions.get(i), "$.pendingActions[" + i + "]")));
        }
        List<Object> events = eventsF.getList("list");
        for (int i = 0; i < events.size(); i++) {
            log.restore(decodeEvent(new Fields(events.get(i), "$.events.list[" + i + "]")));
        }
        log.setNextId(eventsF.getLong("nextId"));
        log.setTotalRecorded(eventsF.getLong("totalRecorded"));
        validateReferences(s);
        return s;
    }

    private static void requireSequentialId(String path, int id, int index) {
        if (id != index + 1) {
            throw new SaveFormatException(path + ".id: expected " + (index + 1) + ", found " + id);
        }
    }

    private static void decodeCells(Fields f, WorldState s) {
        int n = s.width * s.height;
        List<Object> legendRaw = f.getList("biomeLegend");
        Biome[] legend = new Biome[legendRaw.size()];
        for (int i = 0; i < legend.length; i++) {
            try {
                legend[i] = Biome.valueOf(String.valueOf(legendRaw.get(i)));
            } catch (IllegalArgumentException e) {
                throw new SaveFormatException(f.path() + ".biomeLegend[" + i + "]: unknown biome " + legendRaw.get(i));
            }
        }
        List<Object> biome = f.getList("biome", n);
        List<Object> region = f.getList("region", n);
        List<Object> timber = f.getList("timber", n);
        List<Object> forage = f.getList("forage", n);
        List<Object> herbivores = f.getList("herbivores", n);
        List<Object> builtArea = f.getList("builtArea", n);
        List<Object> saplings = f.getList("saplings", n);
        List<Object> observed = f.getList("lastObservedDay", n);
        Cell[] cells = s.cells();
        for (int i = 0; i < n; i++) {
            String at = f.path() + "[" + i + "]";
            int b = Fields.asInt(biome.get(i), at + ".biome");
            if (b < 0 || b >= legend.length) {
                throw new SaveFormatException(at + ".biome: index " + b + " outside the legend");
            }
            Cell c = cells[i];
            c.biome = legend[b];
            c.regionId = Fields.asInt(region.get(i), at + ".region");
            if (s.region(c.regionId) == null) {
                throw new SaveFormatException(at + ".region: no region " + c.regionId);
            }
            c.timber = nonNegative(Fields.asDouble(timber.get(i), at + ".timber"), at + ".timber");
            c.forage = nonNegative(Fields.asDouble(forage.get(i), at + ".forage"), at + ".forage");
            c.herbivores = nonNegative(Fields.asDouble(herbivores.get(i), at + ".herbivores"), at + ".herbivores");
            c.builtArea = Fields.asDouble(builtArea.get(i), at + ".builtArea");
            c.saplings = nonNegative(Fields.asDouble(saplings.get(i), at + ".saplings"), at + ".saplings");
            c.lastObservedDay = Fields.asInt(observed.get(i), at + ".lastObservedDay");
        }
    }

    private static double nonNegative(double v, String where) {
        if (v < 0) {
            throw new SaveFormatException(where + ": negative value " + v);
        }
        return v;
    }

    private static Region decodeRegion(Fields f) {
        List<Object> b = f.getList("bounds", 4);
        String at = f.path() + ".bounds";
        Region r = new Region(f.getInt("id"), f.getString("name"),
                Fields.asInt(b.get(0), at), Fields.asInt(b.get(1), at),
                Fields.asInt(b.get(2), at), Fields.asInt(b.get(3), at));
        r.forestCover = f.getDouble("forestCover");
        r.initialForestCover = f.getDouble("initialForestCover");
        r.herbivores = f.getDouble("herbivores", 0);
        r.initialHerbivores = f.getDouble("initialHerbivores", 0);
        r.forestDepleted = f.getBool("forestDepleted", false);
        r.wildlifeDepleted = f.getBool("wildlifeDepleted", false);
        Fields e = f.getObject("env");
        EnvironmentProfile env = r.env;
        env.gravity = e.getDouble("gravity");
        env.fertility = e.getDouble("fertility");
        env.woodDecay = e.getDouble("woodDecay");
        env.storminess = e.getDouble("storminess");
        env.baseGravity = e.getDouble("baseGravity");
        env.baseFertility = e.getDouble("baseFertility");
        env.baseWoodDecay = e.getDouble("baseWoodDecay");
        env.baseStorminess = e.getDouble("baseStorminess");
        env.gravityFlux = e.getBool("gravityFlux", false);
        env.fluxAmplitude = e.getDouble("fluxAmplitude", 0);
        env.fluxPeriodDays = e.getDouble("fluxPeriodDays", 60);
        env.fluxPhase = e.getDouble("fluxPhase", 0);
        // Out-of-range values (hand edits, older builds) are pulled back inside the rule bounds.
        env.clamp();
        return r;
    }

    private static Settlement decodeSettlement(Fields f) {
        Settlement t = new Settlement(f.getInt("id"), f.getString("name"), f.getInt("parentId"),
                decodePos(f, "home"), f.getInt("foundedDay"));
        t.population = f.getInt("population");
        if (t.population < 0) {
            throw new SaveFormatException(f.path() + ".population: negative");
        }
        t.food = f.getDouble("food");
        t.timber = f.getDouble("timber");
        t.stone = f.getDouble("stone");
        t.strategy = f.getEnum("strategy", Strategy.class);
        t.preferredMaterial = f.getEnum("preferredMaterial", Material.class);
        t.abandoned = f.getBool("abandoned");
        t.abandonedDay = f.getInt("abandonedDay", -1);
        t.timberHarvested = f.getDouble("timberHarvested", 0);
        t.foodProduced = f.getDouble("foodProduced", 0);
        t.peakPopulation = f.getInt("peakPopulation", t.population);
        Fields labor = f.getObject("labor");
        for (Map.Entry<String, Object> e : labor.raw().entrySet()) {
            Job job;
            try {
                job = Job.valueOf(e.getKey());
            } catch (IllegalArgumentException ex) {
                throw new SaveFormatException(labor.path() + ": unknown job " + e.getKey());
            }
            t.labor.put(job, Fields.asInt(e.getValue(), labor.path() + "." + e.getKey()));
        }
        List<Object> memory = f.getList("memory");
        for (int i = 0; i < memory.size(); i++) {
            Fields m = new Fields(memory.get(i), f.path() + ".memory[" + i + "]");
            t.memory.add(new MemoryEntry(m.getEnum("kind", MemoryKind.class), m.getInt("day"),
                    m.getDouble("weight"), m.getString("note", "")));
        }
        if (f.has("lastDecision")) {
            t.lastDecision = decodeDecision(f.getObject("lastDecision"));
        }
        return t;
    }

    private static Decision decodeDecision(Fields f) {
        Map<Strategy, Double> scores = new EnumMap<>(Strategy.class);
        Fields sf = f.getObject("scores");
        for (Map.Entry<String, Object> e : sf.raw().entrySet()) {
            Strategy st;
            try {
                st = Strategy.valueOf(e.getKey());
            } catch (IllegalArgumentException ex) {
                throw new SaveFormatException(sf.path() + ": unknown strategy " + e.getKey());
            }
            scores.put(st, Fields.asDouble(e.getValue(), sf.path() + "." + e.getKey()));
        }
        List<String> reasons = new ArrayList<>();
        for (Object o : f.getList("reasons")) {
            reasons.add(String.valueOf(o));
        }
        return new Decision(f.getInt("day"), f.getEnum("chosen", Strategy.class), scores, reasons,
                f.getLong("causeEventId"));
    }

    private static Building decodeBuilding(Fields f) {
        int stories = f.getInt("stories");
        if (stories < 1) {
            throw new SaveFormatException(f.path() + ".stories: must be at least 1");
        }
        Building b = new Building(f.getInt("id"), f.getInt("settlementId"),
                f.getEnum("type", BuildingType.class), f.getEnum("material", Material.class), stories,
                decodePos(f, "cell"), f.getInt("plannedDay"));
        b.builtDay = f.getInt("builtDay");
        b.progress = f.getDouble("progress");
        b.condition = f.getDouble("condition");
        b.abandoned = f.getBool("abandoned");
        if (f.has("anchor")) {
            List<Object> a = f.getList("anchor", 3);
            String at = f.path() + ".anchor";
            b.setAnchor(Fields.asInt(a.get(0), at), Fields.asInt(a.get(1), at), Fields.asInt(a.get(2), at));
        }
        return b;
    }

    private static TradeRoute decodeRoute(Fields f) {
        TradeRoute r = new TradeRoute(f.getInt("id"), f.getInt("fromId"), f.getInt("toId"),
                f.getInt("establishedDay"));
        r.active = f.getBool("active");
        r.closedDay = f.getInt("closedDay", -1);
        r.lastTradeDay = f.getInt("lastTradeDay", -1);
        r.timberMoved = f.getDouble("timberMoved", 0);
        r.foodMoved = f.getDouble("foodMoved", 0);
        r.stoneMoved = f.getDouble("stoneMoved", 0);
        return r;
    }

    private static WorldAction decodeAction(Fields f) {
        long id = f.getLong("id");
        String type = f.getString("type");
        return switch (type) {
            case "FellTrees" -> new WorldAction.FellTrees(id, f.getInt("settlementId"), decodePos(f, "cell"),
                    f.getInt("logs"));
            case "PlantSaplings" -> new WorldAction.PlantSaplings(id, f.getInt("settlementId"),
                    decodePos(f, "cell"), f.getInt("count"));
            case "Construct" -> new WorldAction.Construct(id, f.getInt("buildingId"));
            case "Decay" -> new WorldAction.Decay(id, f.getInt("buildingId"), f.getDouble("condition"));
            case "LayRoad" -> new WorldAction.LayRoad(id, f.getInt("routeId"));
            default -> throw new SaveFormatException(f.path() + ".type: unknown action " + type);
        };
    }

    private static SimEvent decodeEvent(Fields f) {
        List<Object> cell = f.getList("cell", 2);
        String at = f.path() + ".cell";
        return new SimEvent(f.getLong("id"), f.getInt("day"), f.getEnum("type", EventType.class),
                f.getInt("settlementId"), f.getInt("regionId"),
                Fields.asInt(cell.get(0), at), Fields.asInt(cell.get(1), at),
                f.getString("message"), f.getLong("causeId"));
    }

    private static GridPos decodePos(Fields f, String key) {
        List<Object> p = f.getList(key, 2);
        String at = f.path() + "." + key;
        return new GridPos(Fields.asInt(p.get(0), at), Fields.asInt(p.get(1), at));
    }

    /** Cross-references must point at existing entities, or later code would fail far from the cause. */
    private static void validateReferences(WorldState s) {
        for (Settlement t : s.settlements) {
            if (t.parentId >= 0 && s.settlement(t.parentId) == null) {
                throw new SaveFormatException("settlement " + t.id + ": parent " + t.parentId + " does not exist");
            }
            if (!s.inBounds(t.home)) {
                throw new SaveFormatException("settlement " + t.id + ": home " + t.home + " is outside the world");
            }
        }
        for (Building b : s.buildings) {
            if (s.settlement(b.settlementId) == null) {
                throw new SaveFormatException("building " + b.id + ": settlement " + b.settlementId + " does not exist");
            }
            if (!s.inBounds(b.cell)) {
                throw new SaveFormatException("building " + b.id + ": cell " + b.cell + " is outside the world");
            }
        }
        for (TradeRoute r : s.routes) {
            if (s.settlement(r.fromId) == null || s.settlement(r.toId) == null) {
                throw new SaveFormatException("route " + r.id + ": refers to a missing settlement");
            }
        }
        for (WorldAction a : s.pendingActions) {
            boolean ok = switch (a) {
                case WorldAction.FellTrees f -> s.inBounds(f.cell());
                case WorldAction.PlantSaplings p -> s.inBounds(p.cell());
                case WorldAction.Construct c -> s.building(c.buildingId()) != null;
                case WorldAction.Decay d -> s.building(d.buildingId()) != null;
                case WorldAction.LayRoad r -> s.route(r.routeId()) != null;
            };
            if (!ok) {
                throw new SaveFormatException("pending action " + a.id() + " refers to something that does not exist");
            }
        }
    }
}
