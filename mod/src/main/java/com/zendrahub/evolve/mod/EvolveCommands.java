package com.zendrahub.evolve.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Decision;
import com.zendrahub.evolve.core.model.EnvironmentProfile;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Job;
import com.zendrahub.evolve.core.model.MemoryEntry;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;
import com.zendrahub.evolve.core.eco.Ecology;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** The /evolve command tree: start and steer the simulation, and inspect everything it knows. */
final class EvolveCommands {
    private static final int MAX_NAME_LENGTH = 32;

    private EvolveCommands() {
    }

    @FunctionalInterface
    private interface Handler {
        void run(EvolveRuntime rt, CommandSourceStack source) throws EvolveRuntime.Refusal;
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("evolve")
                .executes(ctx -> run(ctx.getSource(), EvolveCommands::help))
                .then(Commands.literal("start")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx.getSource(), (rt, src) ->
                                        start(rt, src, StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("found")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx.getSource(), (rt, src) ->
                                        found(rt, src, StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("status")
                        .executes(ctx -> run(ctx.getSource(), EvolveCommands::status)))
                .then(Commands.literal("here")
                        .executes(ctx -> run(ctx.getSource(), EvolveCommands::here)))
                .then(Commands.literal("settlements")
                        .executes(ctx -> run(ctx.getSource(), EvolveCommands::settlements)))
                .then(Commands.literal("settlement")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx.getSource(), (rt, src) ->
                                        settlement(rt, src, StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("history")
                        .executes(ctx -> run(ctx.getSource(), (rt, src) -> history(rt, src, 10)))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> run(ctx.getSource(), (rt, src) ->
                                        history(rt, src, IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("why")
                        .then(Commands.argument("eventId", LongArgumentType.longArg(1))
                                .executes(ctx -> run(ctx.getSource(), (rt, src) ->
                                        why(rt, src, LongArgumentType.getLong(ctx, "eventId"))))))
                .then(Commands.literal("advance")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, EvolveRuntime.MAX_ADVANCE_DAYS))
                                .executes(ctx -> run(ctx.getSource(), (rt, src) ->
                                        advance(rt, src, IntegerArgumentType.getInteger(ctx, "days"))))))
                .then(Commands.literal("speed")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("ticksPerDay", IntegerArgumentType.integer(
                                        RuntimeSettings.MIN_TICKS_PER_DAY, RuntimeSettings.MAX_TICKS_PER_DAY))
                                .executes(ctx -> run(ctx.getSource(), (rt, src) ->
                                        speed(rt, src, IntegerArgumentType.getInteger(ctx, "ticksPerDay"))))))
                .then(Commands.literal("pause")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> run(ctx.getSource(), (rt, src) -> pause(rt, src, true))))
                .then(Commands.literal("resume")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> run(ctx.getSource(), (rt, src) -> pause(rt, src, false))))
                .then(Commands.literal("save")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> run(ctx.getSource(), EvolveCommands::save))));
    }

    private static int run(CommandSourceStack source, Handler handler) {
        EvolveRuntime rt = EvolveMod.runtime();
        if (rt == null) {
            McCompat.fail(source, "EVOLVE is not running on this server.");
            return 0;
        }
        try {
            handler.run(rt, source);
            return 1;
        } catch (EvolveRuntime.Refusal refusal) {
            McCompat.fail(source, refusal.getMessage());
            return 0;
        }
    }

    private static void say(CommandSourceStack source, net.minecraft.network.chat.Component line) {
        McCompat.reply(source, line);
    }

    private static ServerPlayer player(CommandSourceStack source) throws EvolveRuntime.Refusal {
        ServerPlayer p = McCompat.player(source);
        if (p == null) {
            throw new EvolveRuntime.Refusal("Only a player can do that.");
        }
        return p;
    }

    private static String cleanName(String raw) throws EvolveRuntime.Refusal {
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new EvolveRuntime.Refusal("Names must be 1-" + MAX_NAME_LENGTH + " characters.");
        }
        return name;
    }

    // --- control ------------------------------------------------------------

    private static void help(EvolveRuntime rt, CommandSourceStack src) {
        say(src, Text.header("Project EVOLVE"));
        say(src, Text.field("/evolve start <name>", "create the world around you and found your town"));
        say(src, Text.field("/evolve found <name>", "found another town where you stand"));
        say(src, Text.field("/evolve status | here | settlements", "overview, your cell and region, all towns"));
        say(src, Text.field("/evolve settlement <name>", "stocks, strategy, the reasons behind it, memory"));
        say(src, Text.field("/evolve history [n] | why <event id>", "recent events, and what caused one"));
        say(src, Text.field("/evolve advance <days> | speed <ticks> | pause | resume | save", "the clock"));
    }

    private static void start(EvolveRuntime rt, CommandSourceStack src, String rawName) throws EvolveRuntime.Refusal {
        String name = cleanName(rawName);
        Settlement town = rt.start(player(src), name);
        WorldState s = rt.sim().state();
        say(src, Text.line("EVOLVE started: " + s.width + " x " + s.height + " chunks, " + s.regions.size()
                + " regions with their own rules.", ChatFormatting.GREEN));
        say(src, Text.field("Your town", town.name + " (" + town.population + " people) at cell " + town.home));
        say(src, Text.field("Clock", rt.settings.ticksPerDay + " ticks per day. Try /evolve here and /evolve settlements."));
    }

    private static void found(EvolveRuntime rt, CommandSourceStack src, String rawName) throws EvolveRuntime.Refusal {
        Settlement s = rt.found(player(src), cleanName(rawName));
        say(src, Text.line("Founded " + s.name + " with " + s.population + " people at cell " + s.home + ".",
                ChatFormatting.GREEN));
    }

    private static void advance(EvolveRuntime rt, CommandSourceStack src, int days) throws EvolveRuntime.Refusal {
        int n = rt.advance(days);
        say(src, Text.line("Advanced " + n + " days to day " + rt.sim().state().day + "; "
                + rt.queuedJobs() + " world changes queued.", ChatFormatting.GREEN));
    }

    private static void speed(EvolveRuntime rt, CommandSourceStack src, int ticksPerDay) {
        rt.setTicksPerDay(ticksPerDay);
        say(src, Text.line("A simulated day now lasts " + rt.settings.ticksPerDay + " ticks ("
                + Text.num(rt.settings.ticksPerDay / 20.0) + " s).", ChatFormatting.GREEN));
    }

    private static void pause(EvolveRuntime rt, CommandSourceStack src, boolean paused) throws EvolveRuntime.Refusal {
        rt.requireRunning();
        rt.setPaused(paused);
        say(src, Text.line(paused ? "Simulation paused." : "Simulation resumed.", ChatFormatting.GREEN));
    }

    private static void save(EvolveRuntime rt, CommandSourceStack src) throws EvolveRuntime.Refusal {
        rt.requireRunning();
        String error = rt.save();
        if (error != null) {
            throw new EvolveRuntime.Refusal("Save failed: " + error);
        }
        say(src, Text.line("EVOLVE world saved.", ChatFormatting.GREEN));
    }

    // --- inspection ---------------------------------------------------------

    private static void status(EvolveRuntime rt, CommandSourceStack src) throws EvolveRuntime.Refusal {
        rt.requireRunning();
        WorldState s = rt.sim().state();
        int towns = 0;
        int outposts = 0;
        int people = 0;
        for (Settlement t : s.settlements) {
            if (t.abandoned) {
                continue;
            }
            if (t.isOutpost()) {
                outposts++;
            } else {
                towns++;
            }
            people += t.population;
        }
        int standing = 0;
        for (Building b : s.buildings) {
            if (b.isActive()) {
                standing++;
            }
        }
        int routes = 0;
        for (TradeRoute r : s.routes) {
            if (r.active) {
                routes++;
            }
        }
        say(src, Text.header("EVOLVE: day " + s.day + ", " + Text.lower(s.season()) + " of year " + (s.year() + 1)));
        say(src, Text.field("clock", rt.settings.ticksPerDay + " ticks/day ("
                + Text.num(rt.settings.ticksPerDay / 20.0) + " s)" + (rt.settings.paused ? ", PAUSED" : "")));
        say(src, Text.field("world", towns + " towns, " + outposts + " outposts, " + people + " people, "
                + standing + " buildings, " + routes + " trade routes, " + s.regions.size() + " regions"));
        say(src, Text.field("history", s.events.totalRecorded() + " events; world changes queued "
                + rt.queuedJobs() + " (" + rt.waitingJobs() + " waiting for chunks to load)"));
    }

    private static void here(EvolveRuntime rt, CommandSourceStack src) throws EvolveRuntime.Refusal {
        rt.requireRunning();
        ServerPlayer p = player(src);
        GridPos pos = rt.world().cellAt(p.blockPosition());
        if (pos == null || !McCompat.isIn(p, rt.world().level)) {
            throw new EvolveRuntime.Refusal("You are outside the simulated area.");
        }
        WorldState s = rt.sim().state();
        Cell c = s.cell(pos);
        Region r = s.regionAt(pos);
        say(src, Text.header("Cell " + pos + " - chunk (" + rt.world().chunkX(pos) + ", " + rt.world().chunkZ(pos) + ")"));
        say(src, Text.field("terrain", Text.lower(c.biome) + "; timber " + Text.num(c.timber) + " / "
                + Text.num(c.biome.timberCapacity) + " logs (cover " + Text.pct(Ecology.forestCover(c)) + "), forage "
                + Text.num(c.forage) + ", herbivores " + Text.num(c.herbivores) + ", saplings " + Text.num(c.saplings)
                + ", built " + Text.pct(c.builtArea)));
        if (r != null) {
            EnvironmentProfile e = r.env;
            say(src, Text.field("region", r.name + " (#" + r.id + "), forest cover " + Text.pct(r.forestCover)
                    + " (was " + Text.pct(r.initialForestCover) + ")"));
            say(src, Text.field("rules", "gravity " + Text.two(e.gravity) + "x"
                    + (e.gravityFlux ? " (unstable +/-" + Text.two(e.fluxAmplitude) + ")" : "")
                    + ", fertility " + Text.two(e.fertility) + "x, wood decay " + Text.pct1(e.woodDecay)
                    + "/day, storms " + Text.pct1(e.storminess) + "/day"));
        }
        Settlement nearest = null;
        int best = Integer.MAX_VALUE;
        for (Settlement t : s.settlements) {
            int d = t.home.chebyshev(pos);
            if (!t.abandoned && d < best) {
                best = d;
                nearest = t;
            }
        }
        if (nearest != null) {
            say(src, Text.field("nearest settlement", nearest.name + ", " + best + " cells away"));
        }
    }

    private static void settlements(EvolveRuntime rt, CommandSourceStack src) throws EvolveRuntime.Refusal {
        rt.requireRunning();
        WorldState s = rt.sim().state();
        say(src, Text.header("Settlements on day " + s.day));
        if (s.settlements.isEmpty()) {
            say(src, Text.line("None yet.", ChatFormatting.GRAY));
        }
        for (Settlement t : s.settlements) {
            String kind = t.isOutpost() ? "outpost of " + nameOf(s, t.parentId) : "town";
            if (t.abandoned) {
                say(src, Text.line("#" + t.id + " " + t.name + " - " + kind + ", abandoned on day "
                        + t.abandonedDay, ChatFormatting.DARK_GRAY));
                continue;
            }
            say(src, Text.field("#" + t.id + " " + t.name, kind + "; " + t.population + " people, "
                    + Text.lower(t.strategy) + "; food " + Text.num(t.food) + ", timber " + Text.num(t.timber)
                    + ", stone " + Text.num(t.stone) + "; " + buildingsOf(s, t).size() + " buildings"));
        }
    }

    private static void settlement(EvolveRuntime rt, CommandSourceStack src, String rawName) throws EvolveRuntime.Refusal {
        rt.requireRunning();
        WorldState s = rt.sim().state();
        Settlement t = s.settlementByName(rawName.trim());
        if (t == null) {
            throw new EvolveRuntime.Refusal("No settlement called " + rawName.trim() + ". See /evolve settlements.");
        }
        List<Building> owned = buildingsOf(s, t);
        int housing = 0;
        for (Building b : owned) {
            if (b.isActive()) {
                housing += b.type.housing;
            }
        }
        String kind = t.isOutpost() ? "outpost of " + nameOf(s, t.parentId) : "town";
        say(src, Text.header(t.name + " (#" + t.id + ", " + kind + ", founded day " + t.foundedDay + ")"
                + (t.abandoned ? " - ABANDONED" : "")));
        say(src, Text.field("people", t.population + " (housing " + housing + ", peak " + t.peakPopulation
                + "); food " + Text.num(t.food) + ", timber " + Text.num(t.timber) + ", stone " + Text.num(t.stone)));
        say(src, Text.field("strategy", Text.lower(t.strategy) + "; builds in " + Text.lower(t.preferredMaterial)));
        Decision d = t.lastDecision;
        if (d != null) {
            List<Map.Entry<Strategy, Double>> scores = new ArrayList<>(d.scores().entrySet());
            scores.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            StringJoiner sj = new StringJoiner(", ");
            for (Map.Entry<Strategy, Double> e : scores) {
                sj.add(Text.lower(e.getKey()) + " " + Text.two(e.getValue()));
            }
            say(src, Text.field("decision (day " + d.day() + ")", sj.toString()));
            for (String reason : d.reasons()) {
                say(src, Text.bullet(reason));
            }
        }
        StringJoiner work = new StringJoiner(", ");
        for (Map.Entry<Job, Integer> e : t.labor.entrySet()) {
            if (e.getValue() > 0) {
                work.add(Text.lower(e.getKey()) + " " + e.getValue());
            }
        }
        if (work.length() > 0) {
            say(src, Text.field("work", work.toString()));
        }
        if (!t.memory.isEmpty()) {
            say(src, Text.field("remembers", t.memory.size() + " events"));
            int from = Math.max(0, t.memory.size() - 5);
            for (MemoryEntry m : t.memory.subList(from, t.memory.size())) {
                say(src, Text.bullet(Text.lower(m.kind()) + " on day " + m.day() + " (strength " + Text.two(m.weight())
                        + ")" + (m.note() == null || m.note().isEmpty() ? "" : ": " + m.note())));
            }
        }
        say(src, Text.field("buildings", describe(owned)));
        for (TradeRoute r : s.routes) {
            if (r.connects(t.id)) {
                say(src, Text.field("trade", (r.active ? "" : "closed ") + "route with " + nameOf(s, r.other(t.id))
                        + " since day " + r.establishedDay + ": timber " + Text.num(r.timberMoved) + ", food "
                        + Text.num(r.foodMoved) + ", stone " + Text.num(r.stoneMoved)));
            }
        }
    }

    private static void history(EvolveRuntime rt, CommandSourceStack src, int count) throws EvolveRuntime.Refusal {
        rt.requireRunning();
        List<SimEvent> events = rt.sim().state().events.recent(count);
        say(src, Text.header("Last " + events.size() + " events"));
        for (SimEvent e : events) {
            say(src, Text.line("#" + e.id() + " day " + e.day() + " ", ChatFormatting.DARK_GRAY)
                    .append(Text.line(e.message(), EventBroadcaster.color(e.type()))));
        }
    }

    private static void why(EvolveRuntime rt, CommandSourceStack src, long eventId) throws EvolveRuntime.Refusal {
        rt.requireRunning();
        List<SimEvent> chain = rt.sim().state().events.causalChain(eventId);
        if (chain.isEmpty()) {
            throw new EvolveRuntime.Refusal("Event #" + eventId + " is not in the retained history.");
        }
        say(src, Text.header("Why #" + eventId + " happened"));
        for (int i = 0; i < chain.size(); i++) {
            SimEvent e = chain.get(i);
            String prefix = i == 0 ? "" : "  ".repeat(Math.min(i, 6)) + "-> ";
            say(src, Text.line(prefix + "#" + e.id() + " day " + e.day() + ": ", ChatFormatting.DARK_GRAY)
                    .append(Text.line(e.message(), EventBroadcaster.color(e.type()))));
        }
    }

    // --- helpers ------------------------------------------------------------

    private static String nameOf(WorldState s, int settlementId) {
        Settlement t = s.settlement(settlementId);
        return t == null ? "#" + settlementId : t.name;
    }

    private static List<Building> buildingsOf(WorldState s, Settlement t) {
        List<Building> out = new ArrayList<>();
        for (Building b : s.buildings) {
            if (b.settlementId == t.id && b.condition > 0) {
                out.add(b);
            }
        }
        return out;
    }

    /** "3 house (timber, 1 storey), 1 storehouse (stone, 2 storeys), building: farm 40%". */
    private static String describe(List<Building> buildings) {
        if (buildings.isEmpty()) {
            return "none";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        StringJoiner building = new StringJoiner(", ");
        for (Building b : buildings) {
            if (!b.isComplete()) {
                building.add(Text.lower(b.type) + " " + Text.pct(b.progress));
                continue;
            }
            String key = Text.lower(b.type) + " (" + Text.lower(b.material) + ", " + b.stories
                    + (b.stories == 1 ? " storey" : " storeys") + (b.abandoned ? ", abandoned" : "") + ")";
            counts.merge(key, 1, Integer::sum);
        }
        StringJoiner sj = new StringJoiner(", ");
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            sj.add(e.getValue() + " " + e.getKey());
        }
        if (building.length() > 0) {
            sj.add("under construction: " + building);
        }
        return sj.toString();
    }
}
