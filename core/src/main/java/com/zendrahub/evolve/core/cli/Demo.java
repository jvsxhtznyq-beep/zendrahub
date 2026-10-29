package com.zendrahub.evolve.core.cli;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.Simulation;
import com.zendrahub.evolve.core.eco.Ecology;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Material;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.WorldState;
import com.zendrahub.evolve.core.scenario.Scenarios;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The milestone-1 demonstration: the same world and code run from two
 * starting conditions (abundant vs. depleted forest around the home town),
 * then the same town under different gravity, then a sweep over many seeds.
 * Nothing here steers the outcome; it only runs and reports.
 */
final class Demo {

    /** One finished run with the home town's strategy history sampled every day. */
    record Run(String label, Simulation sim, Settlement home, List<String> strategyHistory,
               Set<Strategy> strategiesUsed, double coverStart, double herbivoresStart, double fertilityStart) {

        WorldState state() {
            return sim.state();
        }

        Region homeRegion() {
            return sim.state().regionAt(home.home);
        }
    }

    private Demo() {
    }

    static Run simulate(String label, long seed, boolean depleted, Double gravity, int days) {
        Simulation sim = Scenarios.forestTown(seed, depleted, SimConfig.defaults());
        Settlement home = sim.state().settlementByName(Scenarios.HOME_TOWN);
        if (gravity != null) {
            Scenarios.setRegionGravity(sim.state(), home.home, gravity);
        }
        Region region = sim.state().regionAt(home.home);
        double cover = regionCover(sim.state(), region);
        double herbivores = regionHerbivores(sim.state(), region);
        double fertility = region.env.fertility;
        List<String> history = new ArrayList<>();
        Set<Strategy> used = EnumSet.of(home.strategy);
        Strategy last = home.strategy;
        history.add(last + " d" + sim.state().day);
        for (int d = 0; d < days; d++) {
            sim.step();
            if (home.strategy != last) {
                last = home.strategy;
                used.add(last);
                history.add(last + " d" + sim.state().day);
            }
        }
        return new Run(label, sim, home, history, used, cover, herbivores, fertility);
    }

    static void run(PrintStream out, long seed, int days, int sweep) {
        out.println("PROJECT EVOLVE - milestone 1 demonstration");
        out.printf(Locale.ROOT, "Seed %d, %d days (%d years). Same world, same code, same towns;%n", seed, days, days / 40);
        out.println("the only difference is the state of the forest around " + Scenarios.HOME_TOWN + " on day 0.");
        out.println();

        Run a = simulate("ABUNDANT TIMBER", seed, false, null, days);
        Run b = simulate("DEPLETED FOREST", seed, true, null, days);
        compare(out, a, b);
        out.println();
        explain(out, b);
        out.println();
        explain(out, a);
        out.println();
        gravityCheck(out, seed, days);
        if (sweep > 0) {
            out.println();
            sweep(out, seed, days, sweep);
        }
    }

    private static void compare(PrintStream out, Run a, Run b) {
        String f = "%-30s %-34s %-34s%n";
        out.printf(Locale.ROOT, f, "", a.label(), b.label());
        out.printf(Locale.ROOT, f, "Home cell / region", a.home().home + " " + a.homeRegion().name,
                b.home().home + " " + b.homeRegion().name);
        out.printf(Locale.ROOT, f, "Population now / peak", a.home().population + " / " + a.home().peakPopulation,
                b.home().population + " / " + b.home().peakPopulation);
        out.printf(Locale.ROOT, f, "Strategy now", a.home().strategy, b.home().strategy);
        out.printf(Locale.ROOT, f, "Strategy changes", a.strategyHistory().size() - 1, b.strategyHistory().size() - 1);
        out.printf(Locale.ROOT, f, "Strategies used", a.strategiesUsed(), b.strategiesUsed());
        out.printf(Locale.ROOT, f, "Outposts founded", Report.countOutposts(a.state(), a.home()),
                Report.countOutposts(b.state(), b.home()));
        out.printf(Locale.ROOT, f, "Trade routes", Report.countRoutes(a.state(), a.home()),
                Report.countRoutes(b.state(), b.home()));
        out.printf(Locale.ROOT, f, "Preferred material", a.home().preferredMaterial, b.home().preferredMaterial);
        out.printf(Locale.ROOT, f, "Buildings standing by material", buildings(a), buildings(b));
        out.printf(Locale.ROOT, f, "Timber harvested (logs)", Report.fmt0(a.home().timberHarvested),
                Report.fmt0(b.home().timberHarvested));
        out.printf(Locale.ROOT, f, "Home region forest cover", change(a.coverStart(), regionCover(a.state(), a.homeRegion())),
                change(b.coverStart(), regionCover(b.state(), b.homeRegion())));
        out.printf(Locale.ROOT, f, "Home region herbivores", change0(a.herbivoresStart(), regionHerbivores(a.state(), a.homeRegion())),
                change0(b.herbivoresStart(), regionHerbivores(b.state(), b.homeRegion())));
        out.printf(Locale.ROOT, f, "Home region fertility", change(a.fertilityStart(), a.homeRegion().env.fertility),
                change(b.fertilityStart(), b.homeRegion().env.fertility));
        out.printf(Locale.ROOT, f, "Timber / food shortages", count(a, EventType.TIMBER_SHORTAGE) + " / " + count(a, EventType.FOOD_SHORTAGE),
                count(b, EventType.TIMBER_SHORTAGE) + " / " + count(b, EventType.FOOD_SHORTAGE));
        out.printf(Locale.ROOT, f, "Storms in home region", countRegion(a, EventType.STORM), countRegion(b, EventType.STORM));
        out.printf(Locale.ROOT, f, "Events recorded (world)", a.state().events.totalRecorded(), b.state().events.totalRecorded());
        out.println();
        out.println("Strategy history, " + a.label() + ": " + String.join(" -> ", a.strategyHistory()));
        out.println("Strategy history, " + b.label() + ": " + String.join(" -> ", b.strategyHistory()));
    }

    /** The home town's first strategic turn, why it happened, and what it led to. */
    private static void explain(PrintStream out, Run r) {
        WorldState s = r.state();
        out.println("Why, in the " + r.label() + " world:");
        SimEvent turn = null;
        for (SimEvent e : s.events.all()) {
            if (e.settlementId() == r.home().id
                    && (e.type() == EventType.STRATEGY_CHANGED || e.type() == EventType.OUTPOST_FOUNDED
                    || e.type() == EventType.TRADE_ROUTE_OPENED)) {
                turn = e;
                break;
            }
        }
        if (turn == null) {
            out.println("  " + r.home().name + " never changed course: its forest kept up with demand.");
        } else {
            out.println("  Causal chain to its first strategic turn:");
            long turnId = turn.id();
            Report.events(out, s.events.causalChain(turnId));
            List<SimEvent> effects = s.events.filter(e -> e.causeId() == turnId);
            if (!effects.isEmpty()) {
                out.println("  Direct consequences:");
                Report.events(out, effects.subList(0, Math.min(6, effects.size())));
            }
        }
        out.println("  Current state:");
        Report.settlementDetail(out, s, r.home());
        for (Settlement o : s.settlements) {
            if (o.parentId == r.home().id) {
                Report.settlementDetail(out, s, o);
            }
        }
    }

    /** The same abundant world with the home region's gravity changed: does behaviour follow the rule? */
    private static void gravityCheck(PrintStream out, long seed, int days) {
        out.println("Environment rule check: the abundant-timber world with the home region's gravity forced to");
        String f = "  %-8s %-14s %-16s %-14s %-22s %-10s%n";
        out.printf(Locale.ROOT, f, "gravity", "mean storeys", "stone buildings", "timber logs", "strategies used", "population");
        for (double g : new double[]{0.6, 1.0, 1.6}) {
            Run r = simulate("g" + g, seed, false, g, days);
            double storeys = 0;
            int n = 0;
            int stone = 0;
            for (Building b : r.state().buildings) {
                if (b.settlementId == r.home().id && b.isComplete() && b.builtDay > 0) {
                    storeys += b.stories;
                    n++;
                    if (b.material == Material.STONE) {
                        stone++;
                    }
                }
            }
            out.printf(Locale.ROOT, f, Report.fmt(g), n == 0 ? "-" : Report.fmt(storeys / n), stone + " of " + n,
                    Report.fmt0(r.home().timberHarvested), r.strategiesUsed(), r.home().population);
        }
    }

    /** Many seeds, both starting conditions: how often do the towns' paths diverge? */
    private static void sweep(PrintStream out, long seed, int days, int count) {
        out.println("Seed sweep: " + count + " worlds (seeds " + seed + ".." + (seed + count - 1) + "), both starting conditions");
        Map<Strategy, Integer> usedA = new EnumMap<>(Strategy.class);
        Map<Strategy, Integer> usedB = new EnumMap<>(Strategy.class);
        int diverged = 0;
        int outpostsA = 0;
        int outpostsB = 0;
        long popA = 0;
        long popB = 0;
        for (long s = seed; s < seed + count; s++) {
            Run a = simulate("a", s, false, null, days);
            Run b = simulate("b", s, true, null, days);
            for (Strategy st : a.strategiesUsed()) {
                usedA.merge(st, 1, Integer::sum);
            }
            for (Strategy st : b.strategiesUsed()) {
                usedB.merge(st, 1, Integer::sum);
            }
            int oa = Report.countOutposts(a.state(), a.home());
            int ob = Report.countOutposts(b.state(), b.home());
            outpostsA += oa;
            outpostsB += ob;
            popA += a.home().population;
            popB += b.home().population;
            if (!a.strategyHistory().equals(b.strategyHistory()) || oa != ob) {
                diverged++;
            }
        }
        out.printf(Locale.ROOT, "  worlds where the two towns' histories diverged: %d of %d%n", diverged, count);
        out.printf(Locale.ROOT, "  %-26s %-24s %-24s%n", "", "abundant", "depleted");
        for (Strategy st : Strategy.values()) {
            out.printf(Locale.ROOT, "  %-26s %-24s %-24s%n", "worlds that used " + st,
                    usedA.getOrDefault(st, 0) + " of " + count, usedB.getOrDefault(st, 0) + " of " + count);
        }
        out.printf(Locale.ROOT, "  %-26s %-24s %-24s%n", "outposts founded (total)", outpostsA, outpostsB);
        out.printf(Locale.ROOT, "  %-26s %-24s %-24s%n", "mean final population",
                Report.fmt(popA / (double) count), Report.fmt(popB / (double) count));
    }

    private static String buildings(Run r) {
        Map<Material, Integer> m = new EnumMap<>(Material.class);
        for (Building b : r.state().buildings) {
            if (b.isActive() && (b.settlementId == r.home().id || isChild(r.state(), b.settlementId, r.home().id))) {
                m.merge(b.material, 1, Integer::sum);
            }
        }
        return m.toString();
    }

    private static boolean isChild(WorldState s, int settlementId, int parentId) {
        Settlement t = s.settlement(settlementId);
        return t != null && t.parentId == parentId;
    }

    private static long count(Run r, EventType type) {
        return r.state().events.filter(e -> e.type() == type && e.settlementId() == r.home().id).size();
    }

    private static long countRegion(Run r, EventType type) {
        int region = r.homeRegion().id;
        return r.state().events.filter(e -> e.type() == type && e.regionId() == region).size();
    }

    static double regionCover(WorldState s, Region r) {
        double total = 0;
        int n = 0;
        for (int z = r.z0; z < r.z1; z++) {
            for (int x = r.x0; x < r.x1; x++) {
                total += Ecology.forestCover(s.cell(x, z));
                n++;
            }
        }
        return n == 0 ? 0 : total / n;
    }

    static double regionHerbivores(WorldState s, Region r) {
        double total = 0;
        for (int z = r.z0; z < r.z1; z++) {
            for (int x = r.x0; x < r.x1; x++) {
                total += s.cell(x, z).herbivores;
            }
        }
        return total;
    }

    private static String change(double from, double to) {
        return Report.fmt(from) + " -> " + Report.fmt(to);
    }

    private static String change0(double from, double to) {
        return Report.fmt0(from) + " -> " + Report.fmt0(to);
    }
}
