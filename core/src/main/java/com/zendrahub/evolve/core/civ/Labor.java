package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.eco.Ecology;
import com.zendrahub.evolve.core.env.EnvironmentSystem;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Job;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;
import com.zendrahub.evolve.core.model.TradeRoute;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Daily labour: who does what, and what it yields. Food comes first, then a
 * few days of firewood, construction, logging, replanting, quarrying and (for
 * outposts) hauling; spare hands gather more food or stockpile timber. Every
 * extraction goes through {@link Ecology}, and logging and planting are
 * reported to the host.
 */
final class Labor {

    /** What a settlement's people did today. */
    record Workday(int workers, int builders, double food, double logs, double stone, int saplings,
                   double shipped, double delivered) {
    }

    private Labor() {
    }

    static int workers(CivConfig cfg, Settlement s) {
        if (s.population <= 0) {
            return 0;
        }
        double share = s.isOutpost() ? cfg.outpostWorkShare : cfg.workShare;
        return Math.max(1, (int) Math.round(s.population * share));
    }

    static double logsPerWorker(SimContext ctx, Settlement s, Ledger ledger) {
        CivConfig cfg = ctx.config.civ;
        double bonus = ledger.has(s, BuildingType.LUMBER_CAMP) ? 1 + cfg.lumberCampBonus : 1.0;
        return cfg.logYield / Math.sqrt(Construction.gravityAt(ctx.state, s.home)) * bonus;
    }

    /** Stone one quarrier cuts per day: richer rock helps, gravity makes every block heavier. */
    static double stonePerWorker(CivConfig cfg, double richness, double gravity) {
        return cfg.quarryYield * richness / gravity;
    }

    static double haulPerWorker(CivConfig cfg, double gravity) {
        return cfg.haulPerWorker / gravity;
    }

    static boolean conserving(Settlement s) {
        return s.strategy == Strategy.CONSERVE && !s.isOutpost();
    }

    /** Logs the settlement allows itself today: anything standing, or only what regrows when conserving. */
    static double loggable(CivConfig cfg, Settlement s, LocalArea area) {
        if (!conserving(s)) {
            return area.standingTimber;
        }
        double selective = 0;
        for (Cell c : area.cells) {
            selective += c.timber * cfg.selectiveLoggingShare;
        }
        return Math.min(selective, area.regrowthPerDay);
    }

    /** Logs the settlement could cut per day with half its workers, within what it allows itself. */
    static double loggingCapacity(SimContext ctx, Settlement s, LocalArea area, Ledger ledger) {
        CivConfig cfg = ctx.config.civ;
        double potential = workers(cfg, s) * 0.5 * logsPerWorker(ctx, s, ledger);
        return Math.min(potential, loggable(cfg, s, area));
    }

    static Workday work(SimContext ctx, Settlement s, LocalArea area, Needs needs, Ledger ledger) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        int workers = workers(cfg, s);
        int remaining = workers;
        double gravity = Construction.gravityAt(st, s.home);
        double perLogger = logsPerWorker(ctx, s, ledger);
        double allowed = loggable(cfg, s, area);

        // 1. Food, by best marginal yield, until today's target is met.
        FoodPlan food = new FoodPlan(ctx, s, area, ledger);
        double foodTarget = needs.foodPerDay + Math.max(0, needs.reserveTarget * needs.foodPerDay - s.food) / 10.0;
        foodTarget = Math.min(foodTarget, needs.foodPerDay + Math.max(0, needs.storageCap - s.food));
        double plannedFood = 0;
        while (remaining > 0 && plannedFood < foodTarget) {
            Job j = food.bestJob();
            if (j == null) {
                break;
            }
            plannedFood += food.assign(j);
            remaining--;
        }

        // 2. A few days of firewood before anything is built.
        int loggers = 0;
        double logs = 0;
        double firewoodGap = needs.firewoodPerDay * cfg.firewoodReserveDays - s.timber;
        if (firewoodGap > 0 && remaining > 0) {
            double want = Math.min(firewoodGap, allowed);
            int n = Math.min(remaining, (int) Math.ceil(want / perLogger - 1e-9));
            double cut = fell(ctx, s, area, Math.min(want, n * perLogger));
            loggers += n;
            remaining -= n;
            logs += cut;
            s.timber = Math.min(needs.storageCap, s.timber + cut);
        }

        // 3. Construction.
        int builders = 0;
        if (needs.project != null && remaining > 0) {
            double left = (1 - needs.project.progress) * Construction.laborDays(needs.project, st);
            builders = Math.min(remaining,
                    Math.min(Construction.maxBuilders(needs.project), (int) Math.ceil(left - 1e-9)));
            remaining -= builders;
        }

        // 4. Timber. Towns close a quarter of the gap to what they need soon each day;
        //    outposts exist to supply their town, so most of their hands log.
        double room = Math.max(0, needs.storageCap - s.timber);
        double want;
        if (s.isOutpost()) {
            int hands = (int) Math.ceil(remaining * cfg.outpostLoggingShare - 1e-9);
            want = Math.min(hands * perLogger, Math.min(room, allowed - logs));
        } else {
            want = Math.min((needs.timberNeedSoon - s.timber) / 4.0, Math.min(room, allowed - logs));
        }
        if (want > 0 && remaining > 0) {
            int n = Math.min(remaining, (int) Math.ceil(want / perLogger - 1e-9));
            double cut = fell(ctx, s, area, Math.min(want, n * perLogger));
            loggers += n;
            remaining -= n;
            logs += cut;
            s.timber = Math.min(needs.storageCap, s.timber + cut);
        }

        // 5. Replanting while conserving: the point of conserving, so it comes before quarrying.
        int replanters = 0;
        int saplings = 0;
        if (conserving(s) && remaining > 0) {
            replanters = Math.min(remaining, Math.max(1, (int) Math.round(workers * 0.12)));
            saplings = replant(ctx, s, area, (int) Math.round(replanters * cfg.saplingsPerWorker));
            if (saplings == 0) {
                replanters = 0;
            }
            remaining -= replanters;
        }

        // 6. Quarrying for stone projects and repairs.
        int quarriers = 0;
        double stone = 0;
        double perQuarrier = stonePerWorker(cfg, area.bestStone, gravity);
        double stoneGoal = Math.min(needs.stoneNeedSoon - s.stone, needs.storageCap - s.stone);
        if (stoneGoal > 0 && perQuarrier > 0.05 && remaining > 0) {
            quarriers = Math.min(remaining, (int) Math.ceil(stoneGoal / 4.0 / perQuarrier - 1e-9));
            remaining -= quarriers;
            stone = quarriers * perQuarrier;
            s.stone = Math.min(needs.storageCap, s.stone + stone);
        }

        // 7. Outposts haul their surplus home along the supply road.
        int haulers = 0;
        double shipped = 0;
        double delivered = 0;
        Settlement parent = s.isOutpost() ? st.settlement(s.parentId) : null;
        if (parent != null && !parent.abandoned && remaining > 0) {
            double surplus = s.timber - needs.timberNeedSoon;
            if (surplus > 1) {
                double routeGravity = Outposts.routeGravity(st, s.home, parent.home);
                double perHauler = haulPerWorker(cfg, routeGravity);
                haulers = Math.min(remaining, (int) Math.ceil(surplus / perHauler - 1e-9));
                remaining -= haulers;
                shipped = Math.min(surplus, haulers * perHauler);
                double loss = Outposts.haulLoss(cfg, s.home.distance(parent.home), routeGravity);
                delivered = shipped * (1 - loss);
                s.timber -= shipped;
                parent.timber = Math.min(ledger.storage(parent, cfg), parent.timber + delivered);
                TradeRoute road = Trade.supplyRoad(st, s);
                if (road != null) {
                    road.timberMoved += shipped;
                    road.lastTradeDay = ctx.day();
                }
            }
        }

        // 8. Spare hands: more food while there is room for it, else stockpile timber when expanding.
        while (remaining > 0 && s.food + plannedFood < needs.storageCap * 0.9) {
            Job j = food.bestJob();
            if (j == null) {
                break;
            }
            plannedFood += food.assign(j);
            remaining--;
        }
        if (remaining > 0 && s.strategy == Strategy.EXPAND && !s.isOutpost()) {
            double stockpile = Math.min(Math.max(0, needs.storageCap * 0.6 - s.timber), allowed - logs);
            int extra = Math.min(remaining, (int) Math.floor(Math.max(0, stockpile) / perLogger));
            if (extra > 0) {
                double cut = fell(ctx, s, area, extra * perLogger);
                s.timber = Math.min(needs.storageCap, s.timber + cut);
                logs += cut;
                loggers += extra;
                remaining -= extra;
            }
        }

        double produced = food.produce(ctx, s, area);
        s.food = Math.min(needs.storageCap, s.food + produced);

        Map<Job, Integer> jobs = new EnumMap<>(Job.class);
        jobs.put(Job.GATHER, food.gatherers);
        jobs.put(Job.HUNT, food.hunters);
        jobs.put(Job.FARM, food.farmers);
        jobs.put(Job.LOG, loggers);
        jobs.put(Job.QUARRY, quarriers);
        jobs.put(Job.BUILD, builders);
        jobs.put(Job.REPLANT, replanters);
        jobs.put(Job.HAUL, haulers);
        jobs.put(Job.IDLE, remaining);
        s.labor.clear();
        s.labor.putAll(jobs);
        s.timberHarvested += logs;
        s.foodProduced += produced;
        return new Workday(workers, builders, produced, logs, stone, saplings, shipped, delivered);
    }

    /**
     * Cuts timber within reach and tells the host. Expanding settlements clear the
     * nearest stands first; conserving ones take a small share from the densest.
     */
    static double fell(SimContext ctx, Settlement s, LocalArea area, double want) {
        CivConfig cfg = ctx.config.civ;
        if (want <= 1e-9) {
            return 0;
        }
        double taken = 0;
        if (conserving(s)) {
            List<Cell> densest = new ArrayList<>(area.cells);
            densest.sort(Comparator.comparingDouble((Cell c) -> -c.timber));
            for (Cell c : densest) {
                if (taken >= want - 1e-9) {
                    break;
                }
                taken += cut(ctx, s, c, Math.min(want - taken, c.timber * cfg.selectiveLoggingShare));
            }
        } else {
            for (Cell c : area.cells) {
                if (taken >= want - 1e-9) {
                    break;
                }
                taken += cut(ctx, s, c, want - taken);
            }
        }
        return taken;
    }

    private static double cut(SimContext ctx, Settlement s, Cell c, double want) {
        double t = Ecology.harvestTimber(c, want);
        int logs = (int) Math.round(t);
        if (logs > 0) {
            ctx.fellTrees(s.id, c.pos(), logs);
        }
        return t;
    }

    /** Plants saplings in the most depleted cells within reach; returns how many were planted. */
    static int replant(SimContext ctx, Settlement s, LocalArea area, int budget) {
        CivConfig cfg = ctx.config.civ;
        List<Cell> targets = new ArrayList<>();
        for (Cell c : area.cells) {
            double k = Ecology.timberCapacity(c);
            if (k > 20 && c.timber + c.saplings * cfg.logsPerSapling < 0.6 * k) {
                targets.add(c);
            }
        }
        targets.sort(Comparator.comparingDouble(c -> c.timber / Ecology.timberCapacity(c)));
        int planted = 0;
        for (Cell c : targets) {
            if (budget <= 0) {
                break;
            }
            double k = Ecology.timberCapacity(c);
            int room = (int) Math.floor((0.6 * k - c.timber) / cfg.logsPerSapling - c.saplings);
            int n = Math.min(Math.min(budget, 20), Math.max(0, room));
            if (n > 0) {
                Ecology.plant(c, n);
                ctx.plantSaplings(s.id, c.pos(), n);
                budget -= n;
                planted += n;
            }
        }
        return planted;
    }

    /** Assigns food workers by marginal yield and then gathers, hunts and harvests for them. */
    private static final class FoodPlan {
        private final CivConfig cfg;
        private final double farmYield;
        private final int farmCapacity;
        private final double gatherPool;
        private final double huntPool;
        int farmers;
        int gatherers;
        int hunters;

        FoodPlan(SimContext ctx, Settlement s, LocalArea area, Ledger ledger) {
            this.cfg = ctx.config.civ;
            WorldState st = ctx.state;
            int farms = 0;
            double suitability = 0;
            double growth = 0;
            for (Building b : ledger.of(s)) {
                if (b.type == BuildingType.FARM && b.isActive()) {
                    Cell c = st.cell(b.cell);
                    Region r = st.region(c.regionId);
                    farms++;
                    suitability += c.biome.farmSuitability;
                    growth += r == null ? st.season().growth : EnvironmentSystem.growthFactor(st, r);
                }
            }
            this.farmCapacity = farms * cfg.workersPerFarm;
            this.farmYield = farms == 0 ? 0 : cfg.farmYield * (suitability / farms) * (growth / farms);
            this.gatherPool = area.forage * cfg.gatherMaxShare;
            this.huntPool = area.herbivores * cfg.huntMaxShare;
        }

        double marginal(Job j) {
            return switch (j) {
                case FARM -> farmers < farmCapacity ? farmYield : 0;
                case GATHER -> Math.max(0, Math.min(cfg.gatherYield, gatherPool - gatherers * cfg.gatherYield));
                case HUNT -> Math.max(0, Math.min(cfg.huntYield, huntPool - hunters * cfg.huntYield))
                        * cfg.foodPerAnimal;
                default -> 0;
            };
        }

        Job bestJob() {
            Job best = null;
            double bestYield = 1e-6;
            for (Job j : new Job[]{Job.FARM, Job.GATHER, Job.HUNT}) {
                double y = marginal(j);
                if (y > bestYield) {
                    bestYield = y;
                    best = j;
                }
            }
            return best;
        }

        double assign(Job j) {
            double y = marginal(j);
            switch (j) {
                case FARM -> farmers++;
                case GATHER -> gatherers++;
                case HUNT -> hunters++;
                default -> throw new IllegalArgumentException(j.name());
            }
            return y;
        }

        double produce(SimContext ctx, Settlement s, LocalArea area) {
            double food = farmers * farmYield;
            double gatherWant = Math.min(gatherers * cfg.gatherYield, gatherPool);
            double huntWant = Math.min(hunters * cfg.huntYield, huntPool);
            for (Cell c : area.cells) {
                if (gatherWant <= 1e-9) {
                    break;
                }
                double got = Ecology.gatherForage(c, Math.min(gatherWant, c.forage * cfg.gatherMaxShare));
                gatherWant -= got;
                food += got;
            }
            for (Cell c : area.cells) {
                if (huntWant <= 1e-9) {
                    break;
                }
                double got = Ecology.hunt(c, Math.min(huntWant, c.herbivores * cfg.huntMaxShare));
                huntWant -= got;
                food += got * cfg.foodPerAnimal;
            }
            return food;
        }
    }
}
