package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.model.Decision;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility-based strategic planning. Each strategy gets a score from the
 * settlement's situation; the highest wins, with a small bonus for staying the
 * course. Scores and the factors behind them are kept in the {@link Decision}.
 */
final class Planner {
    private Planner() {
    }

    /**
     * Timber scarcity, 0..1: how far the standing timber within reach (plus half
     * the regrowth the settlers expect over the horizon) falls short of 1.5
     * horizons of demand. 0 when supply covers 1.5 horizons, 1 at half a horizon.
     */
    static double scarcity(CivConfig cfg, LocalArea area, Needs needs) {
        return clamp01(1.5 - coverage(cfg, area, needs));
    }

    static double coverage(CivConfig cfg, LocalArea area, Needs needs) {
        double horizon = cfg.scarcityHorizonDays;
        double supply = area.standingTimber + area.regrowthPerDay * horizon * 0.5;
        return supply / (Math.max(0.5, needs.demandPerDay) * horizon);
    }

    static Decision evaluate(SimContext ctx, Settlement s, LocalArea area, Needs needs, Ledger ledger, long causeId) {
        CivConfig cfg = ctx.config.civ;
        List<String> reasons = new ArrayList<>();
        double demand = Math.max(0.5, needs.demandPerDay);
        double coverage = coverage(cfg, area, needs);
        double scarcity = clamp01(1.5 - coverage);
        reasons.add(String.format(Locale.ROOT,
                "timber: %.0f logs standing within %d cells, regrowth ~%.1f/day, demand %.1f/day"
                        + " -> covers %.2f of %.0f days, scarcity %.2f",
                area.standingTimber, cfg.workRadius, area.regrowthPerDay, demand, coverage,
                cfg.scarcityHorizonDays, scarcity));

        double gravity = Construction.gravityAt(ctx.state, s.home);
        double housingPressure = clamp01(needs.housingGap / Math.max(4.0, s.population));
        double shortageMemory = Memories.recall(ctx, s, MemoryKind.TIMBER_SHORTAGE);
        double tradeFailure = Memories.recall(ctx, s, MemoryKind.TRADE_FAILURE);
        double outpostFailure = Memories.recall(ctx, s, MemoryKind.OUTPOST_ABANDONED);
        double sustain = clamp01(area.regrowthPerDay / demand);
        double substitution = Math.max(clamp01(area.bestStone / gravity), area.mudSoil ? 0.6 : 0.0);

        double expand = 0.1 + 0.7 * Math.pow(1 - scarcity, 1.5) + 0.1 * housingPressure;
        double conserve = scarcity * (0.3 + 0.5 * sustain) + 0.25 * shortageMemory + 0.1 * substitution;
        reasons.add(String.format(Locale.ROOT,
                "housing gap %d (pressure %.2f); regrowth meets %.0f%% of demand; substitutes (stone/mud) %.2f",
                needs.housingGap, housingPressure, sustain * 100, substitution));

        Trade.Partner partner = Trade.bestPartner(ctx, s, needs, ledger);
        double trade = 0;
        if (partner == null) {
            reasons.add(String.format(Locale.ROOT, "no trade partner with spare timber within %.0f cells",
                    cfg.tradeRange));
        } else {
            trade = scarcity * partner.value() * (1 - 0.5 * tradeFailure);
            reasons.add(String.format(Locale.ROOT,
                    "best trade partner %s: %.0f spare logs, %.1f cells away, haul loss %.0f%% -> value %.2f",
                    partner.settlement().name, partner.spare(), partner.distance(), partner.loss() * 100,
                    partner.value()));
        }

        Outposts.Site site = Outposts.currentOrBestSite(ctx, s, needs, ledger);
        boolean canSettle = site != null && (site.existing() || s.population >= cfg.minPopulationForOutpost);
        double outpost = canSettle ? scarcity * site.value() * (1 - 0.5 * outpostFailure) : 0;
        if (site == null) {
            reasons.add(String.format(Locale.ROOT, "no forested outpost site %d-%d cells away",
                    cfg.outpostMinDistance, cfg.outpostMaxDistance));
        } else {
            reasons.add(String.format(Locale.ROOT,
                    "%s outpost site %s: %.0f logs nearby, %.1f cells away, haul loss %.0f%% -> value %.2f%s",
                    site.existing() ? "current" : "best", site.cell(), site.standing(), site.distance(),
                    site.loss() * 100, site.value(),
                    canSettle ? "" : String.format(Locale.ROOT, " (needs %d people, has %d)",
                            cfg.minPopulationForOutpost, s.population)));
        }
        if (shortageMemory > 0 || tradeFailure > 0 || outpostFailure > 0) {
            reasons.add(String.format(Locale.ROOT,
                    "memories: timber shortage %.2f, trade failure %.2f, abandoned outpost %.2f",
                    shortageMemory, tradeFailure, outpostFailure));
        }

        Map<Strategy, Double> scores = new EnumMap<>(Strategy.class);
        scores.put(Strategy.EXPAND, expand);
        scores.put(Strategy.CONSERVE, conserve);
        scores.put(Strategy.TRADE, trade);
        scores.put(Strategy.OUTPOST, outpost);
        scores.merge(s.strategy, cfg.hysteresis, Double::sum);
        reasons.add(String.format(Locale.ROOT, "+%.2f for staying with %s", cfg.hysteresis, s.strategy));

        Strategy chosen = Strategy.EXPAND;
        double best = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Strategy, Double> e : scores.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                chosen = e.getKey();
            }
        }
        return new Decision(ctx.day(), chosen, scores, List.copyOf(reasons), causeId);
    }

    static String summary(Decision d) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Strategy, Double> e : d.scores().entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append(' ').append(String.format(Locale.ROOT, "%.2f", e.getValue()));
        }
        return sb.toString();
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
