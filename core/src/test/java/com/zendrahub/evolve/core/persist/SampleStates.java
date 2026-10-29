package com.zendrahub.evolve.core.persist;

import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Decision;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** A hand-built world that exercises every saved field, independent of the subsystems. */
final class SampleStates {
    private SampleStates() {
    }

    static WorldState sample() {
        WorldState s = new WorldState(-1234567890123L, 6, 4);
        s.day = 57;
        s.daysPerSeason = 12;
        s.anchored = true;
        s.originChunkX = -40;
        s.originChunkZ = 17;

        Region west = new Region(0, "Westwood", 0, 0, 3, 4);
        west.env.set(1.0, 1.1, 0.002, 0.02);
        west.forestCover = 0.61;
        west.initialForestCover = 0.8;
        west.herbivores = 31.5;
        west.initialHerbivores = 40;
        west.forestDepleted = true;
        west.wildlifeDepleted = true;
        Region east = new Region(1, "Skyreach \"East\"", 3, 0, 6, 4);
        east.env.set(0.6, 1.25, 0.004, 0.05);
        east.env.gravityFlux = true;
        east.env.fluxAmplitude = 0.2;
        east.env.fluxPeriodDays = 45;
        east.env.fluxPhase = 1.0471975511965976;
        east.env.gravity = 0.7312345678901234;
        s.regions.add(west);
        s.regions.add(east);

        Biome[] biomes = Biome.values();
        for (Cell c : s.cells()) {
            c.biome = biomes[(c.x + c.z) % biomes.length];
            c.regionId = c.x < 3 ? 0 : 1;
            c.timber = c.biome.timberCapacity * 0.37 + c.x * 0.1;
            c.forage = 0.1 + c.z / 3.0;
            c.herbivores = c.x * 1.75;
            c.builtArea = c.x == 1 ? 0.0976 : 0;
            c.saplings = c.z;
            c.lastObservedDay = c.x == 2 ? 50 : -1;
        }

        Settlement town = new Settlement(1, "Oakridge", -1, new GridPos(1, 1), 3);
        town.population = 23;
        town.food = 210.5;
        town.timber = 12.25;
        town.stone = 40;
        town.strategy = Strategy.OUTPOST;
        town.strategySinceDay = 50;
        town.preferredMaterial = Material.STONE;
        town.timberHarvested = 900.5;
        town.foodProduced = 1234.75;
        town.peakPopulation = 25;
        town.labor.put(Job.FARM, 5);
        town.labor.put(Job.LOG, 3);
        town.labor.put(Job.IDLE, 1);
        town.memory.add(new MemoryEntry(MemoryKind.TIMBER_SHORTAGE, 40, 0.8, "stock ran out\nagain"));
        Map<Strategy, Double> scores = new EnumMap<>(Strategy.class);
        scores.put(Strategy.EXPAND, 0.1);
        scores.put(Strategy.CONSERVE, 0.42);
        scores.put(Strategy.TRADE, 0.0);
        scores.put(Strategy.OUTPOST, 0.71);
        town.lastDecision = new Decision(50, Strategy.OUTPOST, scores,
                List.of("timber scarcity 0.83", "best site (4, 2) value 0.66"), 7);
        Settlement outpost = new Settlement(2, "Oakridge Camp", 1, new GridPos(4, 2), 51);
        outpost.population = 5;
        outpost.food = 30;
        s.settlements.add(town);
        s.settlements.add(outpost);

        Building house = new Building(1, 1, BuildingType.HOUSE, Material.TIMBER, 2, new GridPos(1, 1), 3);
        house.builtDay = 10;
        house.progress = 1;
        house.condition = 0.83;
        house.setAnchor(-630, 71, 280);
        Building hall = new Building(2, 2, BuildingType.OUTPOST_HALL, Material.MUD_BRICK, 1, new GridPos(4, 2), 51);
        hall.progress = 0.4;
        s.buildings.add(house);
        s.buildings.add(hall);

        TradeRoute route = new TradeRoute(1, 1, 2, 52);
        route.lastTradeDay = 56;
        route.timberMoved = 77.5;
        s.routes.add(route);

        s.pendingActions.add(new WorldAction.FellTrees(s.nextActionId++, 1, new GridPos(2, 1), 14));
        s.pendingActions.add(new WorldAction.PlantSaplings(s.nextActionId++, 1, new GridPos(2, 2), 6));
        s.pendingActions.add(new WorldAction.Construct(s.nextActionId++, 2));
        s.pendingActions.add(new WorldAction.Decay(s.nextActionId++, 1, 0.75));
        s.pendingActions.add(new WorldAction.LayRoad(s.nextActionId++, 1));

        s.events.record(1, EventType.WORLD_CREATED, -1, -1, -1, -1, "World created", -1);
        long shortage = s.events.record(40, EventType.TIMBER_SHORTAGE, 1, 0, 1, 1,
                "Oakridge is short of timber", -1).id();
        s.events.record(50, EventType.OUTPOST_FOUNDED, 2, 1, 4, 2, "Oakridge founds Oakridge Camp", shortage);
        return s;
    }
}
