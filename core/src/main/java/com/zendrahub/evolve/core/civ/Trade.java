package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.Locale;

/** Timber trade between settlements: choosing partners, opening routes, daily exchange, closing. */
final class Trade {

    /** A potential supplier and how much it is worth to the asking settlement. */
    record Partner(Settlement settlement, double spare, double distance, double loss, double value) {
    }

    private Trade() {
    }

    /** The active route on which the settlement buys timber, or null. Supply roads to outposts don't count. */
    static TradeRoute activeRequestRoute(WorldState st, Settlement s) {
        for (TradeRoute r : st.routes) {
            if (r.active && r.fromId == s.id && !isSupplyRoad(st, r)) {
                return r;
            }
        }
        return null;
    }

    /** A route between a town and its own outpost: hauling, not trade. */
    static boolean isSupplyRoad(WorldState st, TradeRoute r) {
        return r.isSupplyRoad(st);
    }

    /** The active supply road between an outpost and its town, or null. */
    static TradeRoute supplyRoad(WorldState st, Settlement outpost) {
        for (TradeRoute r : st.routes) {
            if (r.active && r.toId == outpost.id && r.fromId == outpost.parentId) {
                return r;
            }
        }
        return null;
    }

    /** Opens the road a new outpost hauls timber home on. */
    static TradeRoute openSupplyRoad(SimContext ctx, Settlement town, Settlement outpost, long causeId) {
        WorldState st = ctx.state;
        TradeRoute route = new TradeRoute(st.nextRouteId(), town.id, outpost.id, ctx.day());
        st.routes.add(route);
        Cell home = st.cell(outpost.home);
        ctx.emit(EventType.TRADE_ROUTE_OPENED, outpost.id, home.regionId, outpost.home,
                String.format(Locale.ROOT, "a supply road joins %s to %s (%.1f cells)", outpost.name, town.name,
                        outpost.home.distance(town.home)), causeId);
        ctx.layRoad(route.id);
        return route;
    }

    /** Timber a supplier can spare: stock beyond its own needs (exports excluded). */
    static double spare(SimContext ctx, Settlement supplier, Ledger ledger) {
        LocalArea area = LocalArea.survey(ctx, supplier.home, ctx.config.civ.workRadius);
        Needs needs = Needs.assess(ctx, supplier, area, ledger);
        double keep = needs.timberNeedSoon - needs.exportPerDay * 5;
        return Math.max(0, supplier.timber - keep);
    }

    /**
     * The most valuable supplier within range, or null if none has timber to offer.
     * A partner is worth its spare stock plus a month of what it can log for others,
     * net of hauling loss, against a month of the asking settlement's demand.
     * Partners that let the settlement down recently are not asked again yet.
     */
    static Partner bestPartner(SimContext ctx, Settlement s, Needs needs, Ledger ledger) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        Partner best = null;
        for (Settlement p : st.settlements) {
            if (p == s || p.abandoned || related(s, p) || failedRecently(ctx, s, p)) {
                continue;
            }
            double distance = s.home.distance(p.home);
            if (distance > cfg.tradeRange) {
                continue;
            }
            double loss = Outposts.haulLoss(cfg, distance, Outposts.routeGravity(st, s.home, p.home));
            double spare = spare(ctx, p, ledger);
            LocalArea area = LocalArea.survey(ctx, p.home, cfg.workRadius);
            double flow = Math.min(cfg.tradeMaxPerDay,
                    Labor.loggingCapacity(ctx, p, area, ledger) * cfg.partnerExportShare);
            double value = Planner.clamp01((spare + flow * 30) * (1 - loss)
                    / (Math.max(0.5, needs.demandPerDay) * 30));
            if (value <= 0) {
                continue;
            }
            if (best == null || value > best.value() + 1e-12) {
                best = new Partner(p, spare, distance, loss, value);
            }
        }
        return best;
    }

    /** True if a route from {@code s} to {@code partner} closed within the retry period. */
    private static boolean failedRecently(SimContext ctx, Settlement s, Settlement partner) {
        for (TradeRoute r : ctx.state.routes) {
            if (!r.active && r.fromId == s.id && r.toId == partner.id
                    && ctx.day() - r.closedDay < ctx.config.civ.tradeRetryDays) {
                return true;
            }
        }
        return false;
    }

    private static boolean related(Settlement a, Settlement b) {
        return a.parentId == b.id || b.parentId == a.id || (a.parentId >= 0 && a.parentId == b.parentId);
    }

    /** Opens, keeps or closes the settlement's trade route according to its strategy and its partner. */
    static void manage(SimContext ctx, Settlement s, LocalArea area, Needs needs, Ledger ledger, long causeId) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        TradeRoute route = activeRequestRoute(st, s);
        if (route != null) {
            Settlement partner = st.settlement(route.toId);
            int lastGood = Math.max(route.lastTradeDay, route.establishedDay);
            if (partner == null || partner.abandoned) {
                close(ctx, route, "the partner is gone", true);
            } else if (ctx.day() - lastGood > cfg.tradeFailDays) {
                close(ctx, route, String.format(Locale.ROOT, "no timber delivered for %d days",
                        ctx.day() - lastGood), true);
            } else if (s.strategy != Strategy.TRADE && Planner.scarcity(cfg, area, needs) < 0.3) {
                close(ctx, route, "timber is no longer scarce", false);
            }
        } else if (s.strategy == Strategy.TRADE) {
            Partner best = bestPartner(ctx, s, needs, ledger);
            if (best != null && best.value() > 0.05) {
                open(ctx, s, best, causeId >= 0 ? causeId : Events.latest(st, s.id, EventType.STRATEGY_CHANGED, 500));
            }
        }
    }

    static TradeRoute open(SimContext ctx, Settlement s, Partner partner, long causeId) {
        WorldState st = ctx.state;
        TradeRoute route = new TradeRoute(st.nextRouteId(), s.id, partner.settlement().id, ctx.day());
        st.routes.add(route);
        Cell home = st.cell(s.home);
        ctx.emit(EventType.TRADE_ROUTE_OPENED, s.id, home.regionId, s.home,
                String.format(Locale.ROOT, "%s opens a timber trade route to %s (%.1f cells, haul loss %.0f%%)",
                        s.name, partner.settlement().name, partner.distance(), partner.loss() * 100), causeId);
        ctx.layRoad(route.id);
        return route;
    }

    static void close(SimContext ctx, TradeRoute route, String reason, boolean failure) {
        WorldState st = ctx.state;
        route.active = false;
        route.closedDay = ctx.day();
        Settlement from = st.settlement(route.fromId);
        Settlement to = st.settlement(route.toId);
        Cell home = from == null ? null : st.cell(from.home);
        boolean supply = isSupplyRoad(st, route);
        ctx.emit(EventType.TRADE_ROUTE_CLOSED, route.fromId, home == null ? -1 : home.regionId,
                from == null ? null : from.home,
                String.format(Locale.ROOT, "%s %s - %s closed: %s (%.0f logs moved)",
                        supply ? "supply road" : "trade route", from == null ? "?" : from.name,
                        to == null ? "?" : to.name, reason, route.timberMoved));
        if (from != null && !from.abandoned && !supply) {
            if (failure) {
                Memories.add(ctx, from, MemoryKind.TRADE_FAILURE, 0.6, reason);
            } else if (route.timberMoved > 50) {
                Memories.add(ctx, from, MemoryKind.TRADE_SUCCESS, 0.4, "trade with "
                        + (to == null ? "?" : to.name) + " served us well");
            }
        }
    }

    /** Daily exchange on the settlement's route: timber in (minus haul loss), food or stone out. */
    static void exchange(SimContext ctx, Settlement s, Needs needs, Ledger ledger) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        TradeRoute route = activeRequestRoute(st, s);
        if (route == null) {
            return;
        }
        Settlement partner = st.settlement(route.toId);
        if (partner == null || partner.abandoned) {
            return;
        }
        double room = Math.max(0, needs.storageCap - s.timber);
        double request = Math.min(Math.min(cfg.tradeMaxPerDay, room), Math.max(0, needs.timberNeedSoon - s.timber));
        if (request < 0.5) {
            route.lastTradeDay = ctx.day();
            return;
        }
        double loss = Outposts.haulLoss(cfg, s.home.distance(partner.home),
                Outposts.routeGravity(st, s.home, partner.home));
        double give = Math.min(request / (1 - loss), spare(ctx, partner, ledger));
        double foodSpare = Math.max(0, s.food - needs.foodPerDay * 5);
        double stoneSpare = Math.max(0, s.stone - needs.stoneNeedSoon);
        give = Math.min(give, (foodSpare + stoneSpare) / cfg.timberPriceInFood);
        if (give < 0.5) {
            return;
        }
        double price = give * cfg.timberPriceInFood;
        double paidFood = Math.min(price, foodSpare);
        double paidStone = price - paidFood;
        partner.timber -= give;
        s.timber = Math.min(needs.storageCap, s.timber + give * (1 - loss));
        s.food -= paidFood;
        s.stone -= paidStone;
        double partnerCap = ledger.storage(partner, cfg);
        partner.food = Math.min(partnerCap, partner.food + paidFood);
        partner.stone = Math.min(partnerCap, partner.stone + paidStone);
        route.timberMoved += give;
        route.foodMoved += paidFood;
        route.stoneMoved += paidStone;
        route.lastTradeDay = ctx.day();
    }
}
