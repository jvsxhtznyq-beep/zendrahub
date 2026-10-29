package com.zendrahub.evolve.core.eco;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.WorldGen;
import com.zendrahub.evolve.core.WorldGenOptions;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcosystemSystemTest {
    private final SimConfig cfg = SimConfig.defaults();

    @Test
    void clearedCellNextToForestRecoversThroughSeedRain() {
        WorldState edge = EcoWorlds.forest(3, 3);
        Cell middle = edge.cell(1, 1);
        middle.timber = 0;
        WorldState isolated = EcoWorlds.forest(1, 1);
        Cell lone = isolated.cell(0, 0);
        lone.timber = 0;
        double capacity = Biome.FOREST.timberCapacity;

        EcosystemSystem a = new EcosystemSystem();
        EcosystemSystem b = new EcosystemSystem();
        EcoWorlds.run(edge, cfg, 30, a);
        EcoWorlds.run(isolated, cfg, 30, b);
        assertTrue(middle.timber > 0, "regrowth has started");
        assertTrue(middle.timber > 5 * lone.timber, "seed rain from neighbours speeds the start of recovery");

        EcoWorlds.run(edge, cfg, 370, a);
        EcoWorlds.run(isolated, cfg, 370, b);
        assertTrue(middle.timber > 0.5 * capacity, "cleared forest is half back within 400 days: " + middle.timber);
        assertTrue(middle.timber > lone.timber, "an isolated clearing recovers more slowly");
    }

    @Test
    void timberGrowsLogisticallyAndStopsAtCapacity() {
        WorldState s = EcoWorlds.forest(1, 1);
        Cell c = s.cell(0, 0);
        double capacity = Ecology.timberCapacity(c);
        c.timber = 0.5 * capacity;
        EcosystemSystem eco = new EcosystemSystem();
        EcoWorlds.run(s, cfg, 1, eco);
        assertTrue(c.timber > 0.5 * capacity, "a half-stocked forest grows");
        EcoWorlds.run(s, cfg, 2000, eco);
        assertTrue(c.timber <= capacity + 1e-9, "never grows past capacity");
        assertTrue(c.timber > 0.99 * capacity, "and approaches it");

        c.timber = 1.5 * capacity;
        EcoWorlds.run(s, cfg, 50, eco);
        assertEquals(1.5 * capacity, c.timber, 1e-9, "a denser real forest is not thinned by the model");
    }

    @Test
    void fertilityAndSeasonScaleGrowth() {
        WorldState fertile = EcoWorlds.uniform(1, 1, Biome.FOREST, 0.5, 1.0, 2.0, 0.002, 0.0);
        WorldState normal = EcoWorlds.uniform(1, 1, Biome.FOREST, 0.5, 1.0, 1.0, 0.002, 0.0);
        double grownFertile = growthOn(fertile, 5);
        double grownNormal = growthOn(normal, 5);
        assertEquals(2.0, grownFertile / grownNormal, 1e-9, "growth is proportional to fertility");

        WorldState spring = EcoWorlds.uniform(1, 1, Biome.FOREST, 0.5, 1.0, 1.0, 0.002, 0.0);
        WorldState winter = EcoWorlds.uniform(1, 1, Biome.FOREST, 0.5, 1.0, 1.0, 0.002, 0.0);
        double springGrowth = growthOn(spring, 5);
        double winterGrowth = growthOn(winter, 35);
        assertEquals(1.25 / 0.25, springGrowth / winterGrowth, 1e-9, "spring grows five times faster than winter");
    }

    /** Timber gained in one step that simulates the given day. */
    private double growthOn(WorldState s, int day) {
        Cell c = s.cell(0, 0);
        double before = c.timber;
        s.day = day - 1;
        EcoWorlds.run(s, cfg, 1, new EcosystemSystem());
        return c.timber - before;
    }

    @Test
    void saplingsMatureIntoTimber() {
        WorldState planted = EcoWorlds.forest(1, 1);
        WorldState bare = EcoWorlds.forest(1, 1);
        planted.cell(0, 0).timber = 0;
        bare.cell(0, 0).timber = 0;
        Ecology.plant(planted.cell(0, 0), 40);

        EcoWorlds.run(planted, cfg, 60, new EcosystemSystem());
        EcoWorlds.run(bare, cfg, 60, new EcosystemSystem());
        Cell c = planted.cell(0, 0);
        assertTrue(c.saplings < 20, "most saplings have matured: " + c.saplings);
        assertTrue(c.timber > 80, "into standing timber: " + c.timber);
        assertTrue(c.timber > 10 * bare.cell(0, 0).timber, "far more than unaided regrowth");
    }

    @Test
    void herbivoresDeclineWhenTheirForestIsCleared() {
        WorldState control = EcoWorlds.forest(12, 12);
        WorldState cleared = EcoWorlds.forest(12, 12);
        for (Cell c : cleared.cells()) {
            c.timber = 0;
        }
        EcoWorlds.run(control, cfg, 120, new EcosystemSystem());
        EcoWorlds.run(cleared, cfg, 120, new EcosystemSystem());

        double kept = EcoWorlds.totalHerbivores(control);
        double left = EcoWorlds.totalHerbivores(cleared);
        assertTrue(left < 0.5 * kept, "losing cover halves the herds: " + left + " vs " + kept);
        assertFalse(cleared.events.ofType(EventType.WILDLIFE_DECLINE).isEmpty(), "the decline is reported");
        assertTrue(control.events.ofType(EventType.WILDLIFE_DECLINE).isEmpty(), "an untouched forest reports none");
    }

    @Test
    void animalsMoveFromClearedLandIntoForest() {
        WorldState control = EcoWorlds.forest(12, 12);
        WorldState cleared = EcoWorlds.forest(12, 12);
        for (Cell c : cleared.cells()) {
            if (c.x < 6) {
                c.timber = 0;
            }
        }
        EcoWorlds.run(control, cfg, 5, new EcosystemSystem());
        EcoWorlds.run(cleared, cfg, 5, new EcosystemSystem());

        assertTrue(EcoWorlds.herbivoresInColumns(cleared, 6, 7) > EcoWorlds.herbivoresInColumns(control, 6, 7),
                "the forest edge fills with animals leaving the clearing");
        assertTrue(EcoWorlds.herbivoresInColumns(cleared, 0, 6) < EcoWorlds.herbivoresInColumns(control, 0, 6),
                "the clearing empties");
    }

    @Test
    void migrationConservesAnimals() {
        SimConfig still = SimConfig.defaults();
        still.eco.herbivoreBirthRate = 0;
        still.eco.herbivoreBaseDeath = 0;
        still.eco.herbivoreStarvationDeath = 0;
        still.eco.herbivoreCrowdingDeath = 0;
        still.eco.extinctionThreshold = 0;
        WorldState s = EcoWorlds.forest(8, 8);
        for (Cell c : s.cells()) {
            if (c.x < 4) {
                c.timber = 0;
            }
        }
        double total = EcoWorlds.totalHerbivores(s);
        double leftBefore = EcoWorlds.herbivoresInColumns(s, 0, 4);
        EcoWorlds.run(s, still, 20, new EcosystemSystem());

        assertEquals(total, EcoWorlds.totalHerbivores(s), total * 1e-9, "moving never creates or destroys animals");
        assertTrue(EcoWorlds.herbivoresInColumns(s, 0, 4) < 0.8 * leftBefore, "but they did move");
    }

    @Test
    void observedTimberReplacesTheModel() {
        WorldState s = EcoWorlds.forest(2, 2);
        s.day = 7;
        SimContext ctx = new SimContext(s, cfg);
        EcosystemSystem eco = new EcosystemSystem();
        Cell c = s.cell(1, 0);
        c.timber = 100;

        eco.observeTimber(ctx, new GridPos(1, 0), 30);
        assertEquals(30, c.timber);
        assertEquals(7, c.lastObservedDay);
        assertEquals(1, s.events.ofType(EventType.OBSERVATION).size(), "a large discrepancy is recorded");

        eco.observeTimber(ctx, new GridPos(1, 0), 35);
        assertEquals(35, c.timber);
        assertEquals(1, s.events.ofType(EventType.OBSERVATION).size(), "a small one is not");

        eco.observeTimber(ctx, new GridPos(5, 5), 10);
        eco.observeTimber(ctx, new GridPos(0, 0), -1);
        assertTrue(s.cell(0, 0).timber > 0, "invalid reports are ignored");
    }

    @Test
    void forestEventsHaveHysteresis() {
        WorldState s = EcoWorlds.forest(4, 4);
        Region r = s.regions.get(0);
        EcosystemSystem eco = new EcosystemSystem();
        // Region cover as a share of its initial cover: dips below half, hovers, recovers past 80%, hovers.
        double[] shares = {0.4, 0.55, 0.45, 0.6, 0.48, 0.7, 0.75, 0.9, 0.7, 0.9};
        for (double share : shares) {
            for (Cell c : s.cells()) {
                c.timber = share * r.initialForestCover * Ecology.FULL_FOREST_LOGS;
            }
            EcoWorlds.run(s, cfg, 1, eco);
        }
        assertEquals(1, s.events.ofType(EventType.FOREST_DEPLETED).size(), "one depletion despite hovering");
        assertEquals(1, s.events.ofType(EventType.FOREST_RECOVERED).size(), "one recovery despite hovering");
        assertFalse(r.forestDepleted);
    }

    @Test
    void hostPopulatedWorldTakesBaselinesOnItsFirstDay() {
        WorldState s = WorldGen.blank(9, new WorldGenOptions(16, 16, 8));
        for (Region r : s.regions) {
            assertEquals(0, r.initialForestCover);
            assertEquals(0, r.initialHerbivores);
        }
        for (Cell c : s.cells()) {
            WorldGen.populateCell(c, (c.x + c.z) % 3 == 0 ? Biome.PLAINS : Biome.FOREST, 0.9);
        }
        EcoWorlds.run(s, cfg, 1, new EcosystemSystem());
        for (Region r : s.regions) {
            assertTrue(r.initialForestCover > 0.3, r.name + " has a forest baseline");
            assertTrue(r.initialHerbivores > 1, r.name + " has a wildlife baseline");
        }
        EcoWorlds.run(s, cfg, 200, new EcosystemSystem());
        assertTrue(s.events.ofType(EventType.WILDLIFE_DECLINE).isEmpty(), "settled wildlife does not crash");
        assertTrue(s.events.ofType(EventType.FOREST_DEPLETED).isEmpty());
    }

    @Test
    void naturalDynamicsStayNearTheirBaselines() {
        for (long seed : new long[]{2, 3, 42}) {
            WorldState s = WorldGen.generate(seed, WorldGenOptions.defaults());
            EcoWorlds.run(s, cfg, 400, new com.zendrahub.evolve.core.env.EnvironmentSystem(), new EcosystemSystem());
            assertTrue(s.events.ofType(EventType.WILDLIFE_DECLINE).isEmpty(),
                    "seed " + seed + ": no wildlife collapse without hunting or clearing");
            assertTrue(s.events.ofType(EventType.FOREST_DEPLETED).isEmpty(),
                    "seed " + seed + ": storms alone do not deplete forests");
        }
    }

    @Test
    void sameInputsGiveTheSameEcosystem() {
        WorldState a = WorldGen.generate(77, WorldGenOptions.defaults());
        WorldState b = WorldGen.generate(77, WorldGenOptions.defaults());
        EcoWorlds.run(a, cfg, 60, new com.zendrahub.evolve.core.env.EnvironmentSystem(), new EcosystemSystem());
        EcoWorlds.run(b, cfg, 60, new com.zendrahub.evolve.core.env.EnvironmentSystem(), new EcosystemSystem());
        Cell[] ca = a.cells();
        Cell[] cb = b.cells();
        for (int i = 0; i < ca.length; i++) {
            assertEquals(ca[i].timber, cb[i].timber, 0.0);
            assertEquals(ca[i].forage, cb[i].forage, 0.0);
            assertEquals(ca[i].herbivores, cb[i].herbivores, 0.0);
        }
        assertEquals(a.events.totalRecorded(), b.events.totalRecorded());
    }
}
