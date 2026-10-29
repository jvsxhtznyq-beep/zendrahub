package com.zendrahub.evolve.core.cli;

import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.Decision;
import com.zendrahub.evolve.core.model.Job;
import com.zendrahub.evolve.core.model.Material;
import com.zendrahub.evolve.core.model.MemoryEntry;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Text views of a world for the command line. */
final class Report {
    private Report() {
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static String fmt0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    static void world(PrintStream out, WorldState s) {
        out.printf(Locale.ROOT, "World seed %d, %dx%d cells, day %d (year %d, %s), %d regions, %d events recorded%n",
                s.seed, s.width, s.height, s.day, s.year(), s.season(), s.regions.size(), s.events.totalRecorded());
        for (Settlement t : s.settlements) {
            settlementLine(out, s, t);
        }
    }

    static void settlementLine(PrintStream out, WorldState s, Settlement t) {
        out.printf(Locale.ROOT, "  %-18s %-7s pop %3d  food %6s  timber %6s  stone %6s  %-8s %-9s at %s%s%n",
                t.name, t.isOutpost() ? "outpost" : "town", t.population, fmt0(t.food), fmt0(t.timber),
                fmt0(t.stone), t.strategy, t.preferredMaterial, t.home,
                t.abandoned ? "  [abandoned day " + t.abandonedDay + "]" : "");
    }

    static void settlementDetail(PrintStream out, WorldState s, Settlement t) {
        settlementLine(out, s, t);
        Region r = s.regionAt(t.home);
        if (r != null) {
            out.printf(Locale.ROOT, "    region %s: gravity %s, fertility %s, wood decay %s, storminess %s, forest cover %s%n",
                    r.name, fmt(r.env.gravity), fmt(r.env.fertility), fmt(r.env.woodDecay * 100) + "%/day",
                    fmt(r.env.storminess), fmt(r.forestCover));
        }
        if (!t.labor.isEmpty()) {
            StringBuilder sb = new StringBuilder("    labour:");
            for (Map.Entry<Job, Integer> e : t.labor.entrySet()) {
                if (e.getValue() > 0) {
                    sb.append(' ').append(e.getKey().name().toLowerCase(Locale.ROOT)).append(' ').append(e.getValue());
                }
            }
            out.println(sb);
        }
        Decision d = t.lastDecision;
        if (d != null) {
            out.println("    last decision (day " + d.day() + "): " + d.chosen() + "  " + scores(d));
            for (String reason : d.reasons()) {
                out.println("      - " + reason);
            }
        }
        if (!t.memory.isEmpty()) {
            StringBuilder sb = new StringBuilder("    remembers:");
            for (MemoryEntry m : t.memory) {
                sb.append(' ').append(m.kind()).append("(day ").append(m.day()).append(')');
            }
            out.println(sb);
        }
        Map<Material, Integer> byMaterial = new EnumMap<>(Material.class);
        int active = 0;
        int ruined = 0;
        for (Building b : s.buildings) {
            if (b.settlementId != t.id) {
                continue;
            }
            if (b.isActive()) {
                active++;
                byMaterial.merge(b.material, 1, Integer::sum);
            } else if (b.isComplete() && b.condition <= 0) {
                ruined++;
            }
        }
        out.println("    buildings: " + active + " standing " + byMaterial + (ruined > 0 ? ", " + ruined + " ruined" : ""));
    }

    static String scores(Decision d) {
        StringBuilder sb = new StringBuilder();
        for (Strategy st : Strategy.values()) {
            Double v = d.scores().get(st);
            if (v != null) {
                sb.append(st).append(' ').append(fmt(v)).append("  ");
            }
        }
        return sb.toString().trim();
    }

    static void events(PrintStream out, List<SimEvent> events) {
        for (SimEvent e : events) {
            out.printf(Locale.ROOT, "  #%-5d day %4d  %-20s %s%n", e.id(), e.day(), e.type(), e.message());
        }
    }

    /** Trade routes (not supply roads to its own outposts) the settlement has ever had. */
    static int countRoutes(WorldState s, Settlement t) {
        int n = 0;
        for (TradeRoute r : s.routes) {
            if (r.connects(t.id) && !r.isSupplyRoad(s)) {
                n++;
            }
        }
        return n;
    }

    static int countOutposts(WorldState s, Settlement t) {
        int n = 0;
        for (Settlement o : s.settlements) {
            if (o.parentId == t.id) {
                n++;
            }
        }
        return n;
    }
}
