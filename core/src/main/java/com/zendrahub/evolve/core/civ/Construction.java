package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Material;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;

import java.util.Locale;

/** What to build next, what it costs, and progress from plan to completed structure. */
final class Construction {

    /** A building a settlement wants, with its style, site and material cost. */
    record Plan(BuildingType type, BuildingStyle.Choice style, GridPos cell, double timberCost, double stoneCost) {
    }

    private Construction() {
    }

    /** Timber for one storey before material adjustments. */
    static double baseTimber(BuildingType type) {
        return switch (type) {
            case HOUSE -> 30;
            case STOREHOUSE -> 45;
            case FARM -> 10;
            case LUMBER_CAMP -> 25;
            case OUTPOST_HALL -> 40;
            case TRADING_POST -> 30;
        };
    }

    /** Worker-days for one storey of timber at gravity 1. */
    static double baseLabor(BuildingType type) {
        return switch (type) {
            case HOUSE -> 20;
            case STOREHOUSE -> 30;
            case FARM -> 10;
            case LUMBER_CAMP -> 15;
            case OUTPOST_HALL -> 30;
            case TRADING_POST -> 20;
        };
    }

    /** Each storey above the first costs 80% of the first. */
    static double storeyFactor(int stories) {
        return 1 + 0.8 * (stories - 1);
    }

    static double timberCost(BuildingType type, Material material, int stories) {
        if (type == BuildingType.FARM) {
            return baseTimber(type);
        }
        double share = switch (material) {
            case TIMBER -> 1.0;
            case STONE -> 0.25;
            case MUD_BRICK -> 0.3;
        };
        return baseTimber(type) * share * storeyFactor(stories);
    }

    static double stoneCost(BuildingType type, Material material, int stories) {
        if (type == BuildingType.FARM || material != Material.STONE) {
            return 0;
        }
        return baseTimber(type) * storeyFactor(stories);
    }

    /** Worker-days to build; stone is heavy, so its labour grows with gravity, and all hauling does a little. */
    static double laborDays(BuildingType type, Material material, int stories, double gravity) {
        double materialFactor = type == BuildingType.FARM ? 1.0 : switch (material) {
            case TIMBER -> 1.0;
            case STONE -> 1.6 * gravity;
            case MUD_BRICK -> 1.4;
        };
        return baseLabor(type) * materialFactor * storeyFactor(stories) * (0.7 + 0.3 * gravity);
    }

    static double laborDays(Building b, WorldState st) {
        return laborDays(b.type, b.material, b.stories, gravityAt(st, b.cell));
    }

    static int maxBuilders(Building b) {
        return 3 + 2 * b.stories;
    }

    static double repairTimber(Building b) {
        return timberCost(b.type, b.material, b.stories) * 0.4 * (1 - b.condition);
    }

    static double repairStone(Building b) {
        return stoneCost(b.type, b.material, b.stories) * 0.4 * (1 - b.condition);
    }

    /** Share of a 16 x 16 cell the building covers. */
    static double footprintShare(BuildingType type) {
        return type.footprint * type.footprint / 256.0;
    }

    static double gravityAt(WorldState st, GridPos pos) {
        Region r = st.regionAt(pos);
        return r == null ? 1.0 : r.env.gravity;
    }

    /** What the settlement should build next, or null when nothing is needed or there is no room. */
    static Plan nextPlan(SimContext ctx, Settlement s, LocalArea area, Ledger ledger, double foodPerDay,
                         double reserveTarget, int housing, double storageCap) {
        CivConfig cfg = ctx.config.civ;
        WorldState st = ctx.state;
        BuildingType type = null;
        if (s.isOutpost()) {
            if (!ledger.has(s, BuildingType.OUTPOST_HALL)) {
                type = BuildingType.OUTPOST_HALL;
            } else if (!ledger.has(s, BuildingType.LUMBER_CAMP)) {
                type = BuildingType.LUMBER_CAMP;
            } else if (housing < s.population) {
                type = BuildingType.HOUSE;
            }
        } else {
            double wildFood = area.forage * cfg.gatherMaxShare
                    + area.herbivores * cfg.huntMaxShare * cfg.foodPerAnimal;
            boolean foodPressure = s.food < reserveTarget * foodPerDay * 0.6 || wildFood < foodPerDay * 1.2;
            int farms = ledger.countActive(s, BuildingType.FARM);
            if (Trade.activeRequestRoute(st, s) != null && !ledger.has(s, BuildingType.TRADING_POST)) {
                type = BuildingType.TRADING_POST;
            } else if (foodPressure && farms <= s.population / 6 && siteFor(ctx, s, BuildingType.FARM) != null) {
                type = BuildingType.FARM;
            } else if (housing < s.population + 2) {
                type = BuildingType.HOUSE;
            } else if (Math.max(s.food, Math.max(s.timber, s.stone)) >= 0.85 * storageCap) {
                type = BuildingType.STOREHOUSE;
            }
        }
        if (type == null) {
            return null;
        }
        GridPos cell = siteFor(ctx, s, type);
        if (cell == null) {
            return null;
        }
        BuildingStyle.Choice style = BuildingStyle.choose(ctx, s, type, area);
        return new Plan(type, style, cell, timberCost(type, style.material(), style.stories()),
                stoneCost(type, style.material(), style.stories()));
    }

    /**
     * A cell within build radius with room for the building: fields go to the most
     * suitable land, everything else as close to home as possible.
     */
    static GridPos siteFor(SimContext ctx, Settlement s, BuildingType type) {
        CivConfig cfg = ctx.config.civ;
        double share = footprintShare(type);
        GridPos best = null;
        double bestSuitability = -1;
        for (Cell c : LocalArea.cellsAround(ctx.state, s.home, cfg.buildRadius)) {
            if (!c.biome.buildable() || c.builtArea + share > cfg.maxBuiltArea) {
                continue;
            }
            if (type != BuildingType.FARM) {
                return c.pos();
            }
            if (c.biome.farmSuitability >= 0.4 && c.biome.farmSuitability > bestSuitability) {
                bestSuitability = c.biome.farmSuitability;
                best = c.pos();
            }
        }
        return best;
    }

    /**
     * Starts the plan if the settlement can pay for it and still keep
     * {@code timberReserve} logs (firewood); materials are spent up front.
     */
    static Building tryStart(SimContext ctx, Settlement s, Plan plan, Ledger ledger, double timberReserve) {
        if (s.timber + 1e-9 < plan.timberCost() + timberReserve || s.stone + 1e-9 < plan.stoneCost()) {
            return null;
        }
        s.timber -= plan.timberCost();
        s.stone -= plan.stoneCost();
        WorldState st = ctx.state;
        Building b = new Building(st.nextBuildingId(), s.id, plan.type(), plan.style().material(),
                plan.style().stories(), plan.cell(), ctx.day());
        ledger.add(b);
        Cell cell = st.cell(plan.cell());
        ctx.emit(EventType.BUILDING_STARTED, s.id, cell.regionId, plan.cell(),
                String.format(Locale.ROOT, "%s starts a %s at %s", s.name, describe(b), plan.cell()));
        return b;
    }

    /** Adds a finished building without construction (starter buildings). */
    static Building addComplete(SimContext ctx, Settlement s, BuildingType type, BuildingStyle.Choice style,
                                GridPos cell, Ledger ledger) {
        WorldState st = ctx.state;
        int stories = BuildingStyle.storeys(gravityAt(st, cell), style.material(), 0, type);
        Building b = new Building(st.nextBuildingId(), s.id, type, style.material(), stories, cell, ctx.day());
        ledger.add(b);
        complete(ctx, b, false);
        return b;
    }

    static void progress(SimContext ctx, Settlement s, int builders, Ledger ledger) {
        Building b = ledger.project(s);
        if (b == null || builders <= 0) {
            return;
        }
        b.progress += builders / laborDays(b, ctx.state);
        if (b.progress >= 1.0 - 1e-9) {
            complete(ctx, b, true);
        }
    }

    private static void complete(SimContext ctx, Building b, boolean announce) {
        WorldState st = ctx.state;
        b.progress = 1.0;
        b.builtDay = ctx.day();
        Cell cell = st.cell(b.cell);
        cell.builtArea = Math.min(1.0, cell.builtArea + footprintShare(b.type));
        if (announce) {
            Settlement owner = st.settlement(b.settlementId);
            ctx.emit(EventType.BUILDING_COMPLETED, b.settlementId, cell.regionId, b.cell,
                    String.format(Locale.ROOT, "%s completes a %s", owner == null ? "?" : owner.name, describe(b)));
        }
        ctx.construct(b.id);
    }

    static String describe(Building b) {
        String material = switch (b.material) {
            case TIMBER -> "timber";
            case STONE -> "stone";
            case MUD_BRICK -> "mud-brick";
        };
        String type = b.type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return (b.stories > 1 ? b.stories + "-storey " : "") + material + " " + type;
    }
}
